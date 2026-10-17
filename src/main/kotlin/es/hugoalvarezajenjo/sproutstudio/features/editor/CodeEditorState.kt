package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import es.hugoalvarezajenjo.sproutstudio.domain.git.LineChange
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.AutoPairs
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Completion
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.CompletionEngine
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.CompletionRequest
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PathLister
import es.hugoalvarezajenjo.sproutstudio.features.document.Document

/**
 * What the editor is doing with one document besides holding its text: the completion popup,
 * the git change popup and the ⌘-hover link. A new one per document, so nothing leaks between tabs.
 */
@Stable
internal class CodeEditorState(val doc: Document, private val paths: PathLister) {
    var completion by mutableStateOf<CompletionRequest?>(null)
        private set
    /** Highlighted row of [completion]. */
    var selected by mutableStateOf(0)
        private set
    /** Change block whose popup is open, and the y (px, in the scrolled content) to show it at. */
    var hunk by mutableStateOf<Pair<LineChange, Float>?>(null)
        private set

    /** ⌘ (Ctrl off macOS) is held: what's under the mouse can be followed. */
    var linkKeyDown by mutableStateOf(false)
    var hoverPos by mutableStateOf<Offset?>(null)
    /** The span under the mouse that a ⌘-click would follow (underlined). */
    var link by mutableStateOf<IntRange?>(null)
    /** Offset a ⌘-press landed on, followed when the button comes up. */
    var pressedLink by mutableStateOf<Int?>(null)

    /** The brackets this editor closed itself, so only those are stepped over. */
    private val autoPairs = AutoPairs()

    /** Apply an edit: [typed] by the user (grouped for undo, reopens completion) or made by a command. */
    fun update(newValue: TextFieldValue, typed: Boolean) {
        if (!typed) {
            doc.value = newValue
            autoPairs.observe(newValue)
            return
        }
        val value = autoPairs.adjust(doc.value, newValue) ?: newValue
        doc.type(value)
        doc.dropRollbackUndo() // typing after a rollback: its Undo would lose that typing
        hunk = null
        doc.nav.closeUsages()
        link = null
        show(complete(value.text, value.selection.start))
    }

    /** ⌘Z / ⇧⌘Z. Ours, not the field's: its own undo misses edits made by commands. */
    fun undo(redo: Boolean) {
        closePopups()
        doc.dropRollbackUndo()
        if (redo) doc.redo() else doc.undo()
    }

    /** Ctrl+Space: complete even where typing alone wouldn't. */
    fun forceCompletion() = show(complete(doc.text, doc.value.selection.start, force = true))

    fun moveCompletion(delta: Int) {
        val n = completion?.items?.size ?: return
        selected = (selected + delta + n) % n
    }

    fun acceptSelected() {
        completion?.let { accept(it.items[selected]) }
    }

    fun accept(item: Completion) {
        val req = completion ?: return
        val applied = CompletionEngine.apply(doc.text, req, item, doc.value.selection.start)
        doc.value = TextFieldValue(applied.text, TextRange(applied.caret))
        // A folder (`common/`, `C4/`): keep walking into it.
        show(if (item.insert.endsWith('/')) complete(applied.text, applied.caret) else null)
    }

    fun closeCompletion() {
        completion = null
    }

    /** The popups that follow the caret: completion and the usages list. */
    fun closePopups() {
        completion = null
        doc.nav.closeUsages()
    }

    /** A click on a change bar: open its popup at [y], or close it if it was already open. */
    fun toggleHunk(change: LineChange, y: Float) {
        hunk = if (hunk?.first == change) null else change to y
    }

    fun closeHunk() {
        hunk = null
    }

    private fun show(req: CompletionRequest?) {
        completion = req
        selected = 0
    }

    private fun complete(text: String, at: Int, force: Boolean = false) =
        CompletionEngine.complete(text, at, force, doc.file?.absolutePath, paths)
}
