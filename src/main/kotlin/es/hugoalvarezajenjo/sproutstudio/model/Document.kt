package es.hugoalvarezajenjo.sproutstudio.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File
import java.io.IOException
import es.hugoalvarezajenjo.sproutstudio.app.AppGraph
import es.hugoalvarezajenjo.sproutstudio.services.persistence.DocumentStore

val DiagramExtensions = es.hugoalvarezajenjo.sproutstudio.services.files.ProjectFiles.DIAGRAM_EXTENSIONS

fun File.isDiagram() = es.hugoalvarezajenjo.sproutstudio.services.files.ProjectFiles.isDiagram(this)

/** One open file (or an unsaved new diagram). */
@Stable
class Document(
    file: File?,
    initialText: String,
    /** Where saves go: the one writer of diagram files, which keeps writes to a file in order. */
    private val store: DocumentStore = AppGraph.default.documents,
) {
    /** Folded blocks; declared first because the [value] setter uses it. */
    val folds = es.hugoalvarezajenjo.sproutstudio.editor.FoldState()

    var file by mutableStateOf(file)
    private var current by mutableStateOf(TextFieldValue(initialText))

    /** Every writer goes through here, so folds follow edits and never hide the caret. */
    var value: TextFieldValue
        get() = current
        set(v) {
            folds.onEdit(current.text, v.text)
            current = folds.adjust(current, v)
        }
    var savedText by mutableStateOf(initialText)
        private set

    /** Line the editor should scroll to and select; consumed by the editor. */
    var jumpRequest by mutableStateOf<Int?>(null)

    /** Bumped to give the editor the keyboard back (e.g. after the command palette closes). */
    var focusTick by mutableStateOf(0)

    /** A copy/export asked for from outside the preview (the command palette); consumed by the preview. */
    var previewRequest by mutableStateOf<PreviewAction?>(null)

    /** Git markers of this text against the last commit (set by the editor; empty if untracked). */
    var gitLines by mutableStateOf(es.hugoalvarezajenjo.sproutstudio.domain.git.LineDiff.Index.Empty)

    /** Text before the last "Rollback Lines", for its Undo; cleared by the next edit. */
    var rollbackUndo by mutableStateOf<TextFieldValue?>(null)

    /** The editor's ⌘F / ⌘R bar. */
    val find = es.hugoalvarezajenjo.sproutstudio.editor.FindState()

    val text: String get() = value.text
    val dirty: Boolean get() = text != savedText
    val name: String get() = file?.name ?: "Untitled.puml"
    val baseName: String get() = name.substringBeforeLast('.')
    val dir: File? get() = file?.parentFile

    /** Why the last save failed (disk full, read-only...); null once a save succeeds. */
    var saveError by mutableStateOf<String?>(null)
        private set

    /**
     * Writes to [target] off the UI thread; on failure keeps the edits dirty and records
     * [saveError]. What gets saved is the text at the moment of the call: edits typed while
     * the write runs stay dirty.
     */
    suspend fun save(target: File? = file): Boolean {
        val f = target ?: return false
        val snapshot = text
        return finishSave(f, snapshot, runCatching { store.write(f, snapshot) }.exceptionOrNull())
    }

    /** [save], blocking. Only for callers that can't suspend and aren't the UI thread (⌘Q). */
    fun saveNow(target: File? = file): Boolean {
        val f = target ?: return false
        val snapshot = text
        return finishSave(f, snapshot, runCatching { store.writeNow(f, snapshot) }.exceptionOrNull())
    }

    private fun finishSave(f: File, snapshot: String, failure: Throwable?): Boolean {
        if (failure != null) {
            if (failure !is IOException) throw failure
            saveError = failure.message ?: failure.javaClass.simpleName
            return false
        }
        file = f
        savedText = snapshot
        saveError = null
        return true
    }

    /** Autosave entry point: only file-backed documents with edits; untitled ones need Save As. */
    suspend fun saveIfNeeded(): Boolean = file != null && dirty && save()

    /** Reload from disk when the file changed outside and we have no unsaved edits. */
    fun reloadIfChanged(): Boolean {
        val f = file ?: return false
        if (dirty || !f.exists()) return false
        val disk = f.readText()
        if (disk == savedText) return false
        value = TextFieldValue(disk, value.selection.let { if (it.end > disk.length) androidx.compose.ui.text.TextRange(disk.length) else it })
        savedText = disk
        return true
    }

    /**
     * Take the file's current content from disk, dropping the editor's copy (after a git
     * rollback, which is meant to throw the edits away). Folds and caret survive where they fit.
     */
    fun replaceFromDisk() {
        val f = file ?: return
        val disk = runCatching { f.readText() }.getOrNull() ?: return
        val sel = value.selection.let { if (it.max > disk.length) androidx.compose.ui.text.TextRange(disk.length) else it }
        value = TextFieldValue(disk, sel)
        savedText = disk
        saveError = null
    }

    companion object {
        fun open(file: File) = Document(file, file.readText())
    }
}

/** Preview actions that can be triggered without clicking the preview toolbar. */
enum class PreviewAction { COPY_IMAGE, EXPORT_SVG, EXPORT_PNG, EXPORT_PDF }
