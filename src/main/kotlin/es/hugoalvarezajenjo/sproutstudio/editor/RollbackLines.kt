package es.hugoalvarezajenjo.sproutstudio.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import es.hugoalvarezajenjo.sproutstudio.git.LineChange
import es.hugoalvarezajenjo.sproutstudio.git.LineDiff
import es.hugoalvarezajenjo.sproutstudio.model.Document

/**
 * "Rollback Lines": put the committed version of one changed block back, in the editor (not on
 * disk; autosave writes it like any edit). Remembers the previous text for [undoRollback].
 */
fun Document.rollbackChange(change: LineChange): Boolean {
    val base = gitLines.base ?: return false
    val before = value
    val r = LineDiff.revert(base, before.text, change)
    if (r.text == before.text) return false
    value = TextFieldValue(r.text, TextRange(r.caret))
    rollbackUndo = before
    return true
}

/** The block under the caret, if it has one. */
fun Document.changeAtCaret(): LineChange? {
    val t = text
    val caret = value.selection.start.coerceIn(0, t.length)
    val line = t.substring(0, caret).count { it == '\n' }
    return gitLines.changeForCaret(line)
}

fun Document.rollbackAtCaret(): Boolean = changeAtCaret()?.let { rollbackChange(it) } ?: false

/** Undo of the last rollback, as long as nothing was typed since. */
fun Document.undoRollback() {
    val prev = rollbackUndo ?: return
    value = prev
    rollbackUndo = null
}
