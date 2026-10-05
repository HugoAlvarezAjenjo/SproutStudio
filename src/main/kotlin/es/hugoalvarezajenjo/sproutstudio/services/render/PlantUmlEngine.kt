package es.hugoalvarezajenjo.sproutstudio.services.render

import net.sourceforge.plantuml.ErrorUml
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader
import net.sourceforge.plantuml.error.PSystemError
import net.sourceforge.plantuml.klimt.color.ColorMapper
import net.sourceforge.plantuml.preproc.Defines
import net.sourceforge.plantuml.security.SFile
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * PlantUML itself, fully in-process. Every function BLOCKS and none is thread-safe: PlantUML
 * keeps global state. Only [PlantUmlRenderService]'s single worker may call it (and tests).
 *
 * - Layout engine is forced to Smetana (pure Java) so no Graphviz/dot binary is ever needed.
 * - The security profile is ALLOWLIST with an empty URL allowlist: no network access.
 */
object PlantUmlEngine {

    /** Prepended to every diagram as configuration; does not shift user line numbers. */
    private val config = listOf("!pragma layout smetana")

    init {
        configureOffline()
    }

    /** Must run before PlantUML reads its security settings; idempotent. */
    fun configureOffline() {
        System.setProperty("PLANTUML_SECURITY_PROFILE", "ALLOWLIST")
        System.setProperty("plantuml.allowlist.url", "")
        System.setProperty("java.awt.headless", System.getProperty("java.awt.headless") ?: "false")
    }

    /**
     * Preview render: a PNG drawn by PlantUML itself at [scale]x, so text uses real fonts.
     * (Skia's SVG renderer has no font manager on desktop and would drop all text.)
     * [darkDiagram] recolours with PlantUML's own dark mode (as `-darkmode`); exports stay original.
     */
    fun renderPreview(source: String, baseDir: File?, index: Int, scale: Double, darkDiagram: Boolean = false): RenderResult {
        val mapper = if (darkDiagram) ColorMapper.DARK_MODE else ColorMapper.IDENTITY
        limitSize(PREVIEW_LIMIT)
        val r = renderBlocking(source, baseDir, index, FileFormat.PNG, scale, mapper)
        val size = r.bytes?.let { pngSize(it) } ?: return r
        if (size.first < PREVIEW_LIMIT && size.second < PREVIEW_LIMIT) return r
        // Too big for [scale]x: measure the natural size and render at the largest scale that fits.
        limitSize(EXPORT_LIMIT)
        val natural = renderBlocking(source, baseDir, index, FileFormat.PNG, 1.0).bytes?.let { pngSize(it) } ?: return r
        val fit = (PREVIEW_LIMIT - 64.0) / maxOf(natural.first, natural.second)
        limitSize(PREVIEW_LIMIT)
        return renderBlocking(source, baseDir, index, FileFormat.PNG, minOf(scale, fit), mapper)
    }

    /**
     * PlantUML silently CROPS images at PLANTUML_LIMIT_SIZE (4096 px by default), which cut
     * big diagrams in the preview (rendered at 3x) and in PNG exports. The preview is capped at
     * the GPU texture size and lowers its scale instead; exports are effectively unlimited.
     */
    private const val PREVIEW_LIMIT = 16384
    private const val EXPORT_LIMIT = 65536

    private fun limitSize(px: Int) { System.setProperty("PLANTUML_LIMIT_SIZE", px.toString()) }

    /** Width/height from the PNG IHDR header, without decoding the image. */
    private fun pngSize(b: ByteArray): Pair<Int, Int>? {
        if (b.size < 24) return null
        fun int(o: Int) = ((b[o].toInt() and 0xff) shl 24) or ((b[o + 1].toInt() and 0xff) shl 16) or
            ((b[o + 2].toInt() and 0xff) shl 8) or (b[o + 3].toInt() and 0xff)
        return int(16) to int(20)
    }

    /** Writes diagram [index] to [target]. Throws [OfflineIncludeException] for remote includes. */
    fun export(source: String, baseDir: File?, index: Int, format: ExportFormat, target: File) {
        OfflineGuard.check(source)?.let { throw OfflineIncludeException(it.line) }
        limitSize(EXPORT_LIMIT)
        val reader = reader(source, baseDir)
        if (format == ExportFormat.PDF) PdfFonts.install()
        target.outputStream().use { out -> reader.outputImage(out, index, FileFormatOption(format.plantUml)) }
    }

    /** PNG bytes of one diagram at its natural size, e.g. for copying to the clipboard. */
    fun renderPng(source: String, baseDir: File?, index: Int): ByteArray {
        OfflineGuard.check(source)?.let { throw OfflineIncludeException(it.line) }
        limitSize(EXPORT_LIMIT)
        val out = ByteArrayOutputStream()
        reader(source, baseDir).outputImage(out, index, FileFormatOption(FileFormat.PNG))
        return out.toByteArray()
    }

    fun renderBlocking(
        source: String,
        baseDir: File?,
        index: Int = 0,
        format: FileFormat = FileFormat.SVG,
        scale: Double = 1.0,
        colorMapper: ColorMapper = ColorMapper.IDENTITY,
    ): RenderResult {
        OfflineGuard.check(source)?.let { v ->
            return RenderResult(bytes = null, diagramCount = countBlocks(source), index = 0,
                error = RenderError(v.line, RenderError.Kind.OFFLINE_INCLUDE))
        }
        if (source.isBlank()) return RenderResult(null, 0, 0, null)

        val reader = reader(source, baseDir)
        val blocks = reader.blocks
        if (blocks.isEmpty()) return RenderResult(null, 0, 0, RenderError(null, RenderError.Kind.NO_DIAGRAM))
        val i = index.coerceIn(0, blocks.size - 1)

        val error = (blocks[i].diagram as? PSystemError)?.let { err ->
            val pos = runCatching { err.lineLocation?.position }.getOrNull()
            RenderError(line = pos?.let { it + 1 }, kind = RenderError.Kind.PLANTUML, message = err.firstError?.error)
        }

        val out = ByteArrayOutputStream()
        val scaled = if (scale != 1.0) reader(source, baseDir, config + "scale $scale") else reader
        val option = FileFormatOption(format).withColorMapper(colorMapper)
        runCatching { scaled.outputImage(out, i, option) }
            .onFailure { t ->
                return RenderResult(null, blocks.size, i,
                    error ?: RenderError(null, RenderError.Kind.FAILED, t.message ?: t.javaClass.simpleName), scale)
            }
        return RenderResult(out.toByteArray(), blocks.size, i, error, scale)
    }

    /**
     * Every error PlantUML can find in [source], across all diagram blocks and all errors within
     * each block -- the data behind the Problems panel. Rendering only touches one block at a time,
     * so this parses the whole file. Returns an empty list when the file is clean.
     */
    fun collectProblemsBlocking(source: String, baseDir: File?): List<Problem> {
        OfflineGuard.check(source)?.let { return listOf(Problem(it.line, "", 0, RenderError.Kind.OFFLINE_INCLUDE)) }
        if (source.isBlank()) return emptyList()

        val reader = reader(source, baseDir)
        val blocks = reader.blocks
        if (blocks.isEmpty()) return emptyList()

        val out = ArrayList<Problem>()
        val lines = source.split('\n').toMutableList()
        blocks.forEachIndexed { i, block ->
            var err = block.diagram as? PSystemError ?: return@forEachIndexed
            // PlantUML's parser stops at the FIRST bad line of a block, so "cla sad" further down is
            // never reported while "inte dasda" is broken. To list them all, blank each reported line
            // (with a comment, so numbering is kept) and parse again, until the block is clean.
            val masked = lines.toMutableList()
            var last = -1
            repeat(MAX_ERRORS_PER_BLOCK) {
                val found = errorsOf(err, i)
                out += found
                val pos = found.mapNotNull { it.line?.minus(1) }.maxOrNull() ?: return@forEachIndexed
                if (pos <= last || pos !in masked.indices) return@forEachIndexed
                // Never blank the block's own @start/@end: that would merge or split diagrams.
                if (masked[pos].trimStart().let { it.startsWith("@start") || it.startsWith("@end") }) return@forEachIndexed
                masked[pos] = "'"
                last = pos
                val again = reader(masked.joinToString("\n"), baseDir).blocks
                if (again.size != blocks.size) return@forEachIndexed
                err = again[i].diagram as? PSystemError ?: return@forEachIndexed
            }
        }
        // Same error can surface twice (block location + per-error location); keep distinct ones.
        return out.distinct().sortedWith(compareBy({ it.diagramIndex }, { it.line ?: Int.MAX_VALUE }))
    }

    private const val MAX_ERRORS_PER_BLOCK = 20

    /** The errors PlantUML reported for one failed block. */
    private fun errorsOf(err: PSystemError, block: Int): List<Problem> {
        // getErrorsUml() returns every error PlantUML raised for this block; getFirstError() is
        // the one it shows on its own bitmap. Fall back to firstError if the collection is empty.
        val errs: Collection<ErrorUml> = runCatching { err.errorsUml }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(err.firstError)
        if (errs.isEmpty()) return listOf(Problem(lineOf(err.lineLocation?.position), "", block))
        return errs.map { e ->
            val pos = runCatching { e.lineLocation?.position ?: e.position }.getOrNull()
            Problem(lineOf(pos), e.error ?: "", block)
        }
    }

    /** PlantUML line positions are 0-based; the editor is 1-based. Treat <0 as "unknown". */
    private fun lineOf(pos: Int?): Int? = pos?.takeIf { it >= 0 }?.let { it + 1 }

    private fun reader(source: String, baseDir: File?, configLines: List<String> = config): SourceStringReader {
        // Local includes are resolved relative to the file's folder and only from there.
        val dir = baseDir ?: File(System.getProperty("user.home"))
        System.setProperty("plantuml.include.path", dir.absolutePath)
        return SourceStringReader(Defines.createEmpty(), source, Charsets.UTF_8, configLines, SFile.fromFile(dir))
    }

    private val startLine = Regex("""^\s*@start\w+""", RegexOption.MULTILINE)
    private fun countBlocks(source: String) = startLine.findAll(source).count()
}
