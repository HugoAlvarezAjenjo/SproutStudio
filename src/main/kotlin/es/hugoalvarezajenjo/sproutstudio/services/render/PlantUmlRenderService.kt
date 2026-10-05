package es.hugoalvarezajenjo.sproutstudio.services.render

import es.hugoalvarezajenjo.sproutstudio.core.concurrency.LatestWinsQueue
import es.hugoalvarezajenjo.sproutstudio.core.concurrency.LatestWinsQueue.Priority
import kotlinx.coroutines.asCoroutineDispatcher
import java.io.File
import java.util.concurrent.Executors

/**
 * [RenderService] on PlantUML. One worker thread does all the work (PlantUML isn't
 * thread-safe), in this order:
 *  1. problems -- they drive the editor's red lines, so they must not wait for an image;
 *  2. the preview image, exports and copies;
 *  3. gallery thumbnails, only when nothing else is waiting.
 */
class PlantUmlRenderService : RenderService {

    private val queue = LatestWinsQueue(
        Executors.newSingleThreadExecutor { r -> Thread(r, "plantuml-render").apply { isDaemon = true } }
            .asCoroutineDispatcher(),
    )

    // Thumbnails depend only on the template and the colour mode: draw each once.
    private val thumbnails = java.util.concurrent.ConcurrentHashMap<Pair<String, Boolean>, ByteArray>()

    init {
        PlantUmlEngine.configureOffline()
    }

    override suspend fun preview(key: Any, request: PreviewRequest): RenderResult =
        queue.submit(Priority.NORMAL, PreviewKey(key)) {
            PlantUmlEngine.renderPreview(request.source, request.baseDir, request.index, request.scale, request.dark)
        }

    override suspend fun problems(key: Any, source: String, baseDir: File?): List<Problem> =
        queue.submit(Priority.URGENT, ProblemsKey(key)) { PlantUmlEngine.collectProblemsBlocking(source, baseDir) }

    override suspend fun thumbnail(source: String, dark: Boolean): ByteArray? {
        val k = source to dark
        thumbnails[k]?.let { return it }
        val bytes = queue.submit(Priority.BACKGROUND) { PlantUmlEngine.renderPreview(source, null, 0, 1.0, dark).bytes }
        if (bytes != null) thumbnails[k] = bytes
        return bytes
    }

    override suspend fun export(source: String, baseDir: File?, index: Int, format: ExportFormat, target: File) =
        queue.submit(Priority.NORMAL) { PlantUmlEngine.export(source, baseDir, index, format, target) }

    override suspend fun png(source: String, baseDir: File?, index: Int): ByteArray =
        queue.submit(Priority.NORMAL) { PlantUmlEngine.renderPng(source, baseDir, index) }

    // The same caller key must not make a problems scan replace a preview render, or vice versa.
    private data class PreviewKey(val owner: Any)
    private data class ProblemsKey(val owner: Any)
}
