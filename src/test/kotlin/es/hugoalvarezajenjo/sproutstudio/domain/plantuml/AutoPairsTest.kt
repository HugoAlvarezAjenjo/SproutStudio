package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoPairsTest {

    /** `|` marks the caret. */
    private fun v(marked: String): TextFieldValue {
        val caret = marked.indexOf('|')
        return TextFieldValue(marked.removeRange(caret, caret + 1), TextRange(caret))
    }

    private fun shown(v: TextFieldValue?): String? = v?.let { it.text.substring(0, it.selection.start) + "|" + it.text.substring(it.selection.end) }

    /** What the text field would hand over after typing [ch] at the caret of [before]. */
    private fun typed(before: String, ch: Char): String? {
        val old = v(before)
        val c = old.selection.start
        val new = TextFieldValue(old.text.substring(0, c) + ch + old.text.substring(c), TextRange(c + 1))
        return shown(AutoPairs.adjust(old, new))
    }

    private fun backspace(before: String): String? {
        val old = v(before)
        val c = old.selection.start
        val new = TextFieldValue(old.text.removeRange(c - 1, c), TextRange(c - 1))
        return shown(AutoPairs.adjust(old, new))
    }

    @Test
    fun `an opening bracket gets its closer and the caret stays between`() {
        assertEquals("class A {|}", typed("class A |", '{'))
        assertEquals("\$f(|)", typed("\$f|", '('))
        assertEquals("A -[|]", typed("A -|", '['))
        assertEquals("note \"|\"", typed("note |", '"'))
    }

    @Test
    fun `no closer is added in front of a word`() {
        assertNull(typed("|foo", '('))
        assertNull(typed("|\$x", '{'))
    }

    @Test
    fun `typing the closer that is already next to the caret steps over it`() {
        assertEquals("\$f()|", typed("\$f(|)", ')'))
        assertEquals("class A {}|", typed("class A {|}", '}'))
        assertEquals("\"a\"|", typed("\"a|\"", '"'))
    }

    @Test
    fun `a closer with nothing to step over is typed normally`() {
        assertNull(typed("\$f(x|", ')'))
        assertNull(typed("|)", '}'))
    }

    @Test
    fun `a quote that closes an open string is not paired`() {
        assertNull(typed("A : \"say hi|", '"'))
    }

    @Test
    fun `backspace on an empty pair removes both halves`() {
        assertEquals("class A |", backspace("class A {|}"))
        assertEquals("\$f|", backspace("\$f(|)"))
        assertEquals("|", backspace("\"|\""))
    }

    @Test
    fun `backspace elsewhere is left alone`() {
        assertNull(backspace("\$f(x|)"))
        assertNull(backspace("ab|"))
    }

    @Test
    fun `typing a bracket over a selection wraps it`() {
        val old = TextFieldValue("A : hi", TextRange(4, 6))
        val new = TextFieldValue("A : (", TextRange(5))
        val r = AutoPairs.adjust(old, new)!!
        assertEquals("A : (hi)", r.text)
        assertEquals(TextRange(5, 7), r.selection)
    }

    @Test
    fun `other edits are not touched`() {
        assertNull(typed("A -|", '>'))
        assertNull(typed("A |", '\''))
        val old = TextFieldValue("ab", TextRange(2))
        assertNull(AutoPairs.adjust(old, TextFieldValue("abcd", TextRange(4)))) // a paste
    }
}
