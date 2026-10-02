package es.hugoalvarezajenjo.sproutstudio.render

import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader
import net.sourceforge.plantuml.error.PSystemError
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

    private fun reader(source: String, baseDir: File?, configLines: List<String> = config): SourceStringReader {
        // Local includes are resolved relative to the file's folder and only from there.
        val dir = baseDir ?: File(System.getProperty("user.home"))
        System.setProperty("plantuml.include.path", dir.absolutePath)
        return SourceStringReader(Defines.createEmpty(), source, Charsets.UTF_8, configLines, SFile.fromFile(dir))
    }

    private val startLine = Regex("""^\s*@start\w+""", RegexOption.MULTILINE)
    private fun countBlocks(source: String) = startLine.findAll(source).count()
}
