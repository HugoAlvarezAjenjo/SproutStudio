package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.runtime.snapshotFlow
import es.hugoalvarezajenjo.sproutstudio.model.Document
import es.hugoalvarezajenjo.sproutstudio.services.persistence.AutoSaveSettings
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

/**
 * Autosave policy. Saves file-backed documents:
 *  - after [IDLE_MS] without typing ([watch]),
 *  - and on demand via [flush]: tab switch, window losing focus, before closing or renaming.
 * Untitled documents are never autosaved (there is no file to write to yet).
 * Writes go through the document store, off the UI thread.
 */
class AutoSaver(private val settings: AutoSaveSettings) {

    val enabled: Boolean get() = settings.enabled.value

    suspend fun flush(docs: Iterable<Document>) {
        if (enabled) docs.forEach { it.saveIfNeeded() }
    }

    /** [flush], blocking: for the quit handler, which runs off the UI thread and can't suspend. */
    fun flushNow(docs: Iterable<Document>) {
        if (enabled) docs.filter { it.file != null && it.dirty }.forEach { it.saveNow() }
    }

    /** Runs until cancelled; launch it once per project window. */
    @OptIn(FlowPreview::class)
    suspend fun watch(docs: () -> List<Document>, idleMs: Long = IDLE_MS) {
        snapshotFlow {
            if (!enabled) emptyList()
            else docs().filter { it.file != null && it.dirty }.map { it to it.text }
        }
            .debounce(idleMs)
            .collect { pending ->
                // Only save the text the user stopped on; a newer edit restarts the timer anyway.
                pending.forEach { (doc, text) -> if (doc.text == text) doc.saveIfNeeded() }
            }
    }

    companion object {
        const val IDLE_MS = 1000L
    }
}
