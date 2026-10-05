package es.hugoalvarezajenjo.sproutstudio.model

import es.hugoalvarezajenjo.sproutstudio.app.AppGraph

/** Legacy access to the autosave setting; removed with the workspace ViewModel. */
object AutoSavePrefs {
    private val s get() = AppGraph.default.autoSave.enabled

    var enabled: Boolean
        get() = s.value
        set(v) { s.value = v }
}

/** Legacy access to [es.hugoalvarezajenjo.sproutstudio.features.workspace.AutoSaver]. */
object AutoSave {
    const val IDLE_MS = es.hugoalvarezajenjo.sproutstudio.features.workspace.AutoSaver.IDLE_MS
    private val saver get() = AppGraph.default.autoSaver

    suspend fun flush(docs: Iterable<Document>) = saver.flush(docs)

    fun flushNow(docs: Iterable<Document>) = saver.flushNow(docs)

    suspend fun watch(docs: () -> List<Document>, idleMs: Long = IDLE_MS) = saver.watch(docs, idleMs)
}
