package es.hugoalvarezajenjo.sproutstudio.domain.editing

import es.hugoalvarezajenjo.sproutstudio.domain.editing.EditHistory.Kind.COMMAND
import es.hugoalvarezajenjo.sproutstudio.domain.editing.EditHistory.Kind.TYPING
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditHistoryTest {

    private fun v(text: String, caret: Int = text.length) = TextFieldValue(text, TextRange(caret))

    @Test
    fun undoRestoresTextAndSelectionAndRedoGoesForward() {
        val h = EditHistory()
        h.record(v("a\nb", 2), v("b\na", 0), COMMAND, 0)
        assertEquals(v("a\nb", 2), h.undo())
        assertEquals(v("b\na", 0), h.redo())
        assertNull(h.redo())
    }

    @Test
    fun keystrokesCloseInTimeAreOneStep() {
        val h = EditHistory()
        h.record(v(""), v("a"), TYPING, 0)
        h.record(v("a"), v("ab"), TYPING, 500)
        h.record(v("ab"), v("abc"), TYPING, 900)
        assertEquals(v(""), h.undo())
        assertFalse(h.canUndo)
    }

    @Test
    fun aPauseStartsANewTypingStep() {
        val h = EditHistory(groupWindowMs = 1000)
        h.record(v(""), v("a"), TYPING, 0)
        h.record(v("a"), v("ab"), TYPING, 1500)
        assertEquals(v("a"), h.undo())
    }

    @Test
    fun aCommandOrACaretMoveEndsTheTypingGroup() {
        val h = EditHistory()
        h.record(v(""), v("a"), TYPING, 0)
        h.record(v("a"), v("a\na"), COMMAND, 10)
        h.record(v("a\na"), v("a\nab"), TYPING, 20)
        h.breakGroup()
        h.record(v("a\nab", 0), v("xa\nab", 1), TYPING, 30)
        assertEquals(v("a\nab", 0), h.undo())
        assertEquals(v("a\na"), h.undo())
        assertEquals(v("a"), h.undo())
        assertEquals(v(""), h.undo())
    }

    @Test
    fun aNewEditDropsTheRedoTrail() {
        val h = EditHistory()
        h.record(v(""), v("a"), COMMAND, 0)
        h.undo()
        assertTrue(h.canRedo)
        h.record(v(""), v("b"), COMMAND, 10)
        assertFalse(h.canRedo)
    }

    @Test
    fun selectionOnlyChangesAreNotSteps() {
        val h = EditHistory()
        h.record(v("a", 0), v("a", 1), COMMAND, 0)
        assertFalse(h.canUndo)
    }

    @Test
    fun keepsOnlyTheLastStepsUpToTheLimit() {
        val h = EditHistory(limit = 2)
        h.record(v(""), v("a"), COMMAND, 0)
        h.record(v("a"), v("ab"), COMMAND, 0)
        h.record(v("ab"), v("abc"), COMMAND, 0)
        assertEquals(v("ab"), h.undo())
        assertEquals(v("a"), h.undo())
        assertNull(h.undo())
    }
}
