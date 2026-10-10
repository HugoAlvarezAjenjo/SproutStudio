package es.hugoalvarezajenjo.sproutstudio.domain.editing

import androidx.compose.ui.text.input.TextFieldValue

/**
 * Undo / redo of one document's text. Every text change is recorded as a step holding the value
 * before and after it, so undo also puts back the caret and selection the edit started from.
 *
 * Typing is grouped: keystrokes that follow each other within [groupWindowMs] become one step,
 * like in IntelliJ. A command (reformat, move line, comment...), a caret move or a pause ends
 * the group, and every command is a step of its own.
 */
class EditHistory(private val limit: Int = DEFAULT_LIMIT, private val groupWindowMs: Long = GROUP_WINDOW_MS) {

    /** How an edit was made; only [TYPING] merges with the step before it. */
    enum class Kind { TYPING, COMMAND }

    private class Step(val before: TextFieldValue, var after: TextFieldValue, val kind: Kind, var at: Long)

    private val undoStack = ArrayDeque<Step>()
    private val redoStack = ArrayDeque<Step>()
    /** False once something ended the current typing group. */
    private var grouping = false

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Record a change of the text from [before] to [after]. Same text: nothing to record. */
    fun record(before: TextFieldValue, after: TextFieldValue, kind: Kind, nowMs: Long) {
        if (before.text == after.text) return
        redoStack.clear()
        val last = undoStack.lastOrNull()
        if (kind == Kind.TYPING && grouping && last != null && last.kind == Kind.TYPING && nowMs - last.at <= groupWindowMs) {
            last.after = after
            last.at = nowMs
        } else {
            undoStack.addLast(Step(before, after, kind, nowMs))
            if (undoStack.size > limit) undoStack.removeFirst()
        }
        grouping = kind == Kind.TYPING
    }

    /** The caret moved, or anything else that should start a new typing step. */
    fun breakGroup() { grouping = false }

    /** The value to show after undoing the last step, or null when there is nothing to undo. */
    fun undo(): TextFieldValue? {
        val step = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(step)
        grouping = false
        return step.before
    }

    /** The value to show after redoing the last undone step, or null when there is none. */
    fun redo(): TextFieldValue? {
        val step = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(step)
        grouping = false
        return step.after
    }

    companion object {
        const val DEFAULT_LIMIT = 1000
        const val GROUP_WINDOW_MS = 1000L
    }
}
