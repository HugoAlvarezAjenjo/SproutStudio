package es.hugoalvarezajenjo.sproutstudio.services.render

import java.io.File

/**
 * Draws diagrams. Every call is safe from any coroutine; the implementation decides the
 * thread and the order. Calls that pass a `key` (one per open document, say) coalesce: a new
 * call replaces a waiting call with the same key, so typing fast never queues stale renders.
 */
interface RenderService {

    /** The diagram the preview shows. Latest call per [key] wins. */
    suspend fun preview(key: Any, request: PreviewRequest): RenderResult

    /** Every error in the file, for the Problems panel and the editor's red lines. Latest call per [key] wins. */
    suspend fun problems(key: Any, source: String, baseDir: File?): List<Problem>

    /** Small PNG of a template for the gallery; cached, and never ahead of the editor's work. */
    suspend fun thumbnail(source: String, dark: Boolean): ByteArray?

    /** Writes diagram [index] to [target]. Throws [OfflineIncludeException] for remote includes. */
    suspend fun export(source: String, baseDir: File?, index: Int, format: ExportFormat, target: File)

    /** PNG of diagram [index] at its natural size (copy to clipboard). */
    suspend fun png(source: String, baseDir: File?, index: Int): ByteArray
}

data class PreviewRequest(
    val source: String,
    val baseDir: File?,
    val index: Int,
    /** Pixel density: 3 keeps the image crisp on Retina and when zooming in. */
    val scale: Double = 3.0,
    /** Recolour with PlantUML's dark mode (preview only). */
    val dark: Boolean = false,
)
