package es.hugoalvarezajenjo.sproutstudio.render

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader
import net.sourceforge.plantuml.error.PSystemError
import net.sourceforge.plantuml.ErrorUml
import net.sourceforge.plantuml.klimt.color.ColorMapper
import net.sourceforge.plantuml.preproc.Defines
import net.sourceforge.plantuml.security.SFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.Executors

/** What the user can export to. PDF needs extra libraries that are out of scope for v1. */
enum class ExportFormat(val label: String, val extension: String, internal val plantUml: FileFormat) {
    SVG("SVG (vector)", "svg", FileFormat.SVG),
    PNG("PNG (image)", "png", FileFormat.PNG),
}

data class RenderError(
    /** 1-based line in the editor text, or null when PlantUML could not pin it down. */
    val line: Int?,
    val message: String,
)

data class RenderResult(
    /** Image bytes (SVG or PNG, per request); present even for errors (PlantUML draws an error image). */
    val bytes: ByteArray?,
    /** Number of @startxxx/@endxxx blocks found in the file (0 when none). */
    val diagramCount: Int,
    /** Index actually rendered (clamped into range). */
    val index: Int,
    val error: RenderError?,
    /** Pixel scale the image was rendered at (PNG only; 1.0 for SVG). */
    val scale: Double = 1.0,
) {
    val svg: ByteArray? get() = bytes
}

/** One entry for the Problems panel: a single PlantUML error, placed in the file. */
data class Problem(
    /** 1-based line in the editor text, or null when PlantUML could not pin it down. */
    val line: Int?,
    val message: String,
    /** 0-based index of the diagram block this error belongs to. */
    val diagramIndex: Int,
)

/**
 * Renders PlantUML text fully in-process.
 *
 * - Layout engine is forced to Smetana (pure Java) so no Graphviz/dot binary is ever needed.
 * - The security profile is ALLOWLIST with an empty URL allowlist: no network access.
 * - All PlantUML calls go through one dedicated thread; PlantUML keeps global state and is
 *   not safe to call concurrently.
 */
object PlantUmlRenderer {

    private val thread: ExecutorCoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "plantuml-render").apply { isDaemon = true } }
            .asCoroutineDispatcher()

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

    suspend fun render(source: String, baseDir: File?, index: Int = 0): RenderResult =
        withContext(thread) { renderBlocking(source, baseDir, index) }

    /**
     * Preview render: a PNG drawn by PlantUML itself at [scale]x, so text uses real fonts.
     * (Skia's SVG renderer has no font manager on desktop and would drop all text.)
     */
    suspend fun renderPreview(
        source: String,
        baseDir: File?,
        index: Int,
        scale: Double,
        /** Recolour with PlantUML's own dark mode (as `-darkmode`). Preview only; exports stay original. */
        darkDiagram: Boolean = false,
    ): RenderResult =
        withContext(thread) {
            val mapper = if (darkDiagram) ColorMapper.DARK_MODE else ColorMapper.IDENTITY
            limitSize(PREVIEW_LIMIT)
            val r = renderBlocking(source, baseDir, index, FileFormat.PNG, scale, mapper)
            val size = r.bytes?.let { pngSize(it) } ?: return@withContext r
            if (size.first < PREVIEW_LIMIT && size.second < PREVIEW_LIMIT) return@withContext r
            // Too big for [scale]x: measure the natural size and render at the largest scale that fits.
            limitSize(EXPORT_LIMIT)
            val natural = renderBlocking(source, baseDir, index, FileFormat.PNG, 1.0).bytes?.let { pngSize(it) }
                ?: return@withContext r
            val fit = (PREVIEW_LIMIT - 64.0) / maxOf(natural.first, natural.second)
            limitSize(PREVIEW_LIMIT)
            renderBlocking(source, baseDir, index, FileFormat.PNG, minOf(scale, fit), mapper)
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

    suspend fun export(source: String, baseDir: File?, index: Int, format: ExportFormat, target: File) =
        withContext(thread) {
            OfflineGuard.check(source)?.let { throw IllegalStateException(it.message) }
            limitSize(EXPORT_LIMIT)
            val reader = reader(source, baseDir)
            target.outputStream().use { out ->
                reader.outputImage(out, index, FileFormatOption(format.plantUml))
            }
        }

    /** PNG bytes of one diagram, e.g. for copying to the clipboard. */
    suspend fun renderPng(source: String, baseDir: File?, index: Int): ByteArray =
        withContext(thread) {
            OfflineGuard.check(source)?.let { throw IllegalStateException(it.message) }
            limitSize(EXPORT_LIMIT)
            val out = ByteArrayOutputStream()
            reader(source, baseDir).outputImage(out, index, FileFormatOption(FileFormat.PNG))
            out.toByteArray()
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
                error = RenderError(v.line, v.message))
        }
        if (source.isBlank()) return RenderResult(null, 0, 0, null)

        val reader = reader(source, baseDir)
        val blocks = reader.blocks
        if (blocks.isEmpty()) {
            return RenderResult(null, 0, 0,
                RenderError(null, "No diagram found yet. Start with @startuml and end with @enduml."))
        }
        val i = index.coerceIn(0, blocks.size - 1)

        val error = (blocks[i].diagram as? PSystemError)?.let { err ->
            val first = err.firstError
            val pos = runCatching { err.lineLocation?.position }.getOrNull()
            RenderError(line = pos?.let { it + 1 }, message = first?.error ?: "Syntax error")
        }

        val out = ByteArrayOutputStream()
        val scaled = if (scale != 1.0) reader(source, baseDir, config + "scale $scale") else reader
        val option = FileFormatOption(format).withColorMapper(colorMapper)
        runCatching { scaled.outputImage(out, i, option) }
            .onFailure { t ->
                return RenderResult(null, blocks.size, i,
                    error ?: RenderError(null, t.message ?: t.javaClass.simpleName), scale)
            }
        return RenderResult(out.toByteArray(), blocks.size, i, error, scale)
    }

    /**
     * Every error PlantUML can find in [source], across all diagram blocks and all errors within
     * each block — the data behind the Problems panel. Rendering only touches one block at a time,
     * so this parses the whole file once. Returns an empty list when the file is clean.
     *
     * Runs on the PlantUML thread like [render]; PlantUML keeps global state and isn't concurrency-safe.
     */
    suspend fun collectProblems(source: String, baseDir: File?): List<Problem> =
        withContext(thread) { collectProblemsBlocking(source, baseDir) }

    fun collectProblemsBlocking(source: String, baseDir: File?): List<Problem> {
        OfflineGuard.check(source)?.let { return listOf(Problem(it.line, it.message, 0)) }
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
        if (errs.isEmpty()) return listOf(Problem(lineOf(err.lineLocation?.position), "Syntax error", block))
        return errs.map { e ->
            val pos = runCatching { e.lineLocation?.position ?: e.position }.getOrNull()
            Problem(lineOf(pos), e.error ?: "Syntax error", block)
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
