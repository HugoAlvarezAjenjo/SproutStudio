package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MoveLinesTest {

    private fun at(text: String, caret: Int) = TextFieldValue(text, TextRange(caret))

    @Test
    fun `moves the caret line up and down, caret included`() {
        val up = MoveLines.up(at("A\nBB\nC", 3))
        assertEquals("BB\nA\nC", up.text)
        assertEquals(TextRange(1), up.selection)
        val down = MoveLines.down(up)
        assertEquals("A\nBB\nC", down.text)
        assertEquals(TextRange(3), down.selection)
    }

    @Test
    fun `moves every line the selection touches and keeps it selected`() {
        // "B" and "C" selected, up to the start of "D" (that line isn't touched).
        val v = TextFieldValue("A\nB\nC\nD", TextRange(2, 6))
        val down = MoveLines.down(v)
        assertEquals("A\nD\nB\nC", down.text)
        assertEquals(TextRange(4, 7), down.selection)
        assertEquals("A\nB\nC\nD", MoveLines.up(down).text)
    }

    @Test
    fun `the last line, without a trailing newline, moves up`() {
        assertEquals("A\nC\nB", MoveLines.up(at("A\nB\nC", 4)).text)
    }

    @Test
    fun `nothing moves past the top or the bottom`() {
        val top = at("A\nB", 0)
        assertSame(top, MoveLines.up(top))
        val bottom = at("A\nB", 3)
        assertSame(bottom, MoveLines.down(bottom))
    }
}
