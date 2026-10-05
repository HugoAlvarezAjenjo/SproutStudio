package es.hugoalvarezajenjo.sproutstudio.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/** Legacy access to the autosave setting; removed once the autosave moves to its service. */
object AutoSavePrefs {
    private val s get() = es.hugoalvarezajenjo.sproutstudio.app.AppGraph.default.autoSave.enabled

    var enabled: Boolean
        get() = s.value
        set(v) { s.value = v }
}

/**
 * Autosave policy. Saves file-backed documents:
 *  - after [IDLE_MS] without typing ([watch]),
 *  - and on demand via [flush]: tab switch, window losing focus, before closing.
 * Untitled documents are never autosaved (there is no file to write to yet).
 */
object AutoSave {
    const val IDLE_MS = 1000L

    fun flush(docs: Iterable<Document>) {
        if (AutoSavePrefs.enabled) docs.forEach { it.saveIfNeeded() }
    }

    /** Runs until cancelled; launch it once per project window. */
    @OptIn(FlowPreview::class)
    suspend fun watch(docs: () -> List<Document>, idleMs: Long = IDLE_MS) {
        snapshotFlow {
            if (!AutoSavePrefs.enabled) emptyList()
            else docs().filter { it.file != null && it.dirty }.map { it to it.text }
        }
            .debounce(idleMs)
            .collect { pending ->
                // Only save the text the user stopped on; a newer edit restarts the timer anyway.
                pending.forEach { (doc, text) -> if (doc.text == text) doc.saveIfNeeded() }
            }
    }
}
