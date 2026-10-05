package es.hugoalvarezajenjo.sproutstudio.features.preview

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import es.hugoalvarezajenjo.sproutstudio.core.concurrency.AppDispatchers
import es.hugoalvarezajenjo.sproutstudio.core.viewmodel.ViewModel
import es.hugoalvarezajenjo.sproutstudio.features.preview.DiagramImage
import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import es.hugoalvarezajenjo.sproutstudio.services.render.PreviewRequest
import es.hugoalvarezajenjo.sproutstudio.services.render.Problem
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderError
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderResult
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The live preview of one text: re-renders it whenever it changes, and holds what the preview
 * pane, the Problems panel and the editor's red lines show.
 *
 * [source] is read inside a snapshot flow, so it must read Compose state (a document's text):
 * every change to it schedules a render. Typing is debounced by [debounceMs]; a new keystroke
 * drops the render in flight, and the render service drops the stale ones still queued.
 */
@Stable
class PreviewViewModel(
    private val render: RenderService,
    private val diagram: DiagramSettings,
    private val dispatchers: AppDispatchers,
    parent: Job? = null,
    private val debounceMs: Long = 300,
    private val source: () -> Source,
) : ViewModel(dispatchers.main, parent) {

    data class Source(val text: String, val baseDir: File?)

    var image by mutableStateOf<DiagramImage?>(null)
        private set
    /** True when [image] is an older, successful render shown while the current text has an error. */
    var stale by mutableStateOf(false)
        private set
    var error by mutableStateOf<RenderError?>(null)
        private set
    /** Every error across all diagram blocks (the Problems panel); empty when the file is clean. */
    var problems by mutableStateOf<List<Problem>>(emptyList())
        private set
    var diagramCount by mutableStateOf(0)
        private set
    /** Which diagram block of the file is shown. */
    var index by mutableStateOf(0)
        private set
    var rendering by mutableStateOf(false)
        private set
    /** False until the first render finished (the pane shows "Rendering…" instead of an error). */
    var firstDone by mutableStateOf(false)
        private set

    val text: String get() = source().text

    /** Out-of-range indices are clamped by the next render (which knows how many there are). */
    fun showDiagram(i: Int) {
        index = i.coerceAtLeast(0)
    }

    fun previousDiagram() = showDiagram(index - 1)
    fun nextDiagram() = showDiagram(index + 1)

    /** Writes the shown diagram to [target], in its original colours. */
    suspend fun export(format: ExportFormat, target: File) {
        val s = source()
        render.export(s.text, s.baseDir, index, format, target)
    }

    /** The shown diagram as a PNG at its natural size, in its original colours. */
    suspend fun png(): ByteArray {
        val s = source()
        return render.png(s.text, s.baseDir, index)
    }

    private data class Input(val source: Source, val index: Int, val dark: Boolean)

    init {
        scope.launch {
            var last: Input? = null
            snapshotFlow { Input(source(), index, diagram.dark.value) }
                .distinctUntilChanged()
                .collectLatest { input ->
                    // Only typing waits; switching diagram or colour mode renders at once.
                    if (firstDone && last?.source?.text != input.source.text) delay(debounceMs)
                    last = input
                    rendering = true
                    try {
                        // Problems first: they drive the red lines, which must not wait for the image.
                        problems = render.problems(this@PreviewViewModel, input.source.text, input.source.baseDir)
                        val r = render.preview(
                            this@PreviewViewModel,
                            PreviewRequest(input.source.text, input.source.baseDir, input.index, dark = input.dark),
                        )
                        // Decoding a big PNG is too slow for the UI thread.
                        val parsed = r.bytes?.let { b -> withContext(dispatchers.default) { DiagramImage.fromPng(b, r.scale) } }
                        show(r, parsed)
                    } finally {
                        rendering = false
                    }
                }
        }
    }

    private fun show(r: RenderResult, parsed: DiagramImage?) {
        diagramCount = r.diagramCount
        if (r.index != index) index = r.index
        error = r.error
        when {
            r.error == null && parsed != null -> { image = parsed; stale = false }
            // Keep the last good picture on screen while the user is mid-edit.
            image != null && firstDone -> stale = true
            // Never show PlantUML's own black-and-green error bitmap: the error strip says it better.
            r.error != null -> { image = null; stale = false }
            else -> { image = parsed; stale = false }
        }
        firstDone = true
    }
}
