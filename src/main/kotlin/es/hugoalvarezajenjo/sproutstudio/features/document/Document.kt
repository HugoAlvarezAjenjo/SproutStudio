package es.hugoalvarezajenjo.sproutstudio.features.document

import es.hugoalvarezajenjo.sproutstudio.features.editor.FindState
import es.hugoalvarezajenjo.sproutstudio.features.editor.FoldState
import es.hugoalvarezajenjo.sproutstudio.features.editor.NavigationState
import es.hugoalvarezajenjo.sproutstudio.domain.editing.EditHistory
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.DiagramTheme
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.EditOps
import es.hugoalvarezajenjo.sproutstudio.services.files.ProjectFiles
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import es.hugoalvarezajenjo.sproutstudio.domain.git.LineDiff
import java.io.File
import java.io.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import es.hugoalvarezajenjo.sproutstudio.services.persistence.DocumentStore

val DiagramExtensions = ProjectFiles.DIAGRAM_EXTENSIONS

fun File.isDiagram() = ProjectFiles.isDiagram(this)

/** One open file (or an unsaved new diagram). */
@Stable
class Document(
    file: File?,
    initialText: String,
    /** Where saves go: the one writer of diagram files, which keeps writes to a file in order. */
    private val store: DocumentStore,
    /** Milliseconds, for grouping typing into undo steps. */
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Folded blocks; declared first because the [value] setter uses it. */
    val folds = FoldState()

    var file by mutableStateOf(file)
        private set
    private var current by mutableStateOf(TextFieldValue(initialText))

    private val history = EditHistory()
    /** Bumped on every undo/redo-relevant change, so menus and the palette re-read [canUndo]. */
    private var historyVersion by mutableStateOf(0)

    /**
     * Every writer goes through here, so folds follow edits and never hide the caret. A write
     * that changes the text is one undo step (see [type] for keystrokes, which are grouped).
     */
    var value: TextFieldValue
        get() = current
        set(v) = write(v, EditHistory.Kind.COMMAND)

    /** A keystroke in the editor: consecutive ones are undone together. */
    fun type(v: TextFieldValue) = write(v, EditHistory.Kind.TYPING)

    private fun write(v: TextFieldValue, kind: EditHistory.Kind) {
        val before = current
        show(v)
        if (before.text != current.text) {
            history.record(before, current, kind, clock())
            historyVersion++
        } else if (before.selection != current.selection) {
            history.breakGroup() // typing after moving the caret is a new step
        }
    }

    private fun show(v: TextFieldValue) {
        folds.onEdit(current.text, v.text)
        current = folds.adjust(current, v)
    }

    val canUndo: Boolean get() = historyVersion.let { history.canUndo }
    val canRedo: Boolean get() = historyVersion.let { history.canRedo }

    /** ⌘Z: back to the text and selection before the last step. */
    fun undo() = history.undo()?.let { show(it); historyVersion++ }

    /** ⇧⌘Z: forward again to the text and selection after the last undone step. */
    fun redo() = history.redo()?.let { show(it); historyVersion++ }

    /**
     * Gives diagram [block] the PlantUML theme [theme] (null = default look) by editing its
     * `!theme` line: one undo step, with the selection kept on the same text.
     */
    fun setDiagramTheme(block: Int, theme: String?) {
        val v = value
        val edit = DiagramTheme.set(v.text, block, theme) ?: return
        value = TextFieldValue(edit.applyTo(v.text), TextRange(edit.shift(v.selection.start), edit.shift(v.selection.end)))
    }

    var savedText by mutableStateOf(initialText)
        private set

    // One-shot requests to this document's editor and preview. Channels, not state: a request
    // is delivered exactly once, even if the editor isn't on screen yet (preview-only layout),
    // and two requests in a row are never merged into one.
    private val jumps = Channel<TextRange>(Channel.BUFFERED)
    private val focusRequests = Channel<Unit>(Channel.CONFLATED)
    private val previewCommands = Channel<PreviewAction>(Channel.CONFLATED)

    /** Selections the editor should make and scroll into view (Problems panel, Go to Declaration...). */
    val jumpRequests: Flow<TextRange> = jumps.receiveAsFlow()
    /** Asks for the keyboard back (e.g. after the command palette closes). */
    val focusRequestFlow: Flow<Unit> = focusRequests.receiveAsFlow()
    /** Copy / export asked for from outside the preview (the command palette). */
    val previewCommandFlow: Flow<PreviewAction> = previewCommands.receiveAsFlow()

    /** Select 1-based [line] and scroll to it. */
    fun jumpTo(line: Int) = jumpTo(EditOps.selectLine(text, line))
    fun jumpTo(selection: TextRange) { jumps.trySend(selection) }
    fun requestFocus() { focusRequests.trySend(Unit) }
    fun requestPreview(action: PreviewAction) { previewCommands.trySend(action) }

    /** The pending preview command, taken off the queue. For tests. */
    internal fun takePreviewCommand(): PreviewAction? = previewCommands.tryReceive().getOrNull()

    /** Git markers of this text against the last commit (set by the editor; empty if untracked). */
    var gitLines by mutableStateOf(LineDiff.Index.Empty)
        private set

    fun updateGitLines(index: LineDiff.Index) {
        if (gitLines !== index) gitLines = index
    }

    /**
     * Text before the last "Rollback Lines", while its toast offers Undo; cleared by the next
     * edit, after which the toast's Undo would undo that edit instead of the rollback.
     */
    var rollbackUndo by mutableStateOf<TextFieldValue?>(null)
        private set

    /** Replace the text with a rolled-back version, as one undo step. */
    fun applyRollback(restored: TextFieldValue) {
        val before = value
        value = restored
        rollbackUndo = before
    }

    /** The toast's Undo: the same as ⌘Z right after the rollback. */
    fun undoRollback() {
        if (rollbackUndo == null) return
        rollbackUndo = null
        undo()
    }

    /** The rollback's Undo no longer applies (typing after it would be lost, or it timed out). */
    fun dropRollbackUndo(only: TextFieldValue? = null) {
        if (only == null || rollbackUndo === only) rollbackUndo = null
    }

    /** The file was renamed or moved on disk: this tab follows it. */
    fun relocate(to: File) { file = to }

    /** The editor's ⌘F / ⌘R bar. */
    val find = FindState()

    /** Go to Declaration's back/forward trail and usages list. */
    val nav = NavigationState()

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

    /**
     * Take the file's current content from disk, dropping the editor's copy (after a git
     * rollback, which is meant to throw the edits away). Folds and caret survive where they fit.
     */
    suspend fun replaceFromDisk() {
        val f = file ?: return
        store.readOrNull(f)?.let(::takeDiskText)
    }

    /**
     * After git rewrote the working tree: take the file's new content, or return false when the
     * file doesn't exist any more (the tab should close).
     */
    suspend fun syncWithDisk(): Boolean {
        val f = file ?: return true
        val disk = store.readOrNull(f) ?: return false
        if (disk != text) takeDiskText(disk)
        return true
    }

    private fun takeDiskText(disk: String) {
        val sel = value.selection.let { if (it.max > disk.length) TextRange(disk.length) else it }
        value = TextFieldValue(disk, sel)
        savedText = disk
        saveError = null
    }

    companion object {
        /**
         * Opens [file] with its current text. The read blocks the caller: opening is a user action
         * on small text files, and the tab must exist with its content right away.
         */
        fun open(file: File, store: DocumentStore) = Document(file, store.readNow(file), store)
    }
}

/** Preview actions that can be triggered without clicking the preview toolbar. */
enum class PreviewAction { COPY_IMAGE, EXPORT_SVG, EXPORT_PNG, EXPORT_PDF }
