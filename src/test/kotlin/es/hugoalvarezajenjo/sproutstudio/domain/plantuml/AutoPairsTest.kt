package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AutoPairsTest {

    /** An editor: feeds keystrokes to [AutoPairs] the way the text field reports them. `|` marks the caret. */
    private class Editor(start: String) {
        val pairs = AutoPairs()
        var value = start.indexOf('|').let { TextFieldValue(start.removeRange(it, it + 1), TextRange(it)) }
            .also { pairs.observe(it) }

        fun type(chars: String) = apply {
            for (ch in chars) {
                val c = value.selection.start
                val raw = TextFieldValue(value.text.substring(0, c) + ch + value.text.substring(value.selection.end), TextRange(c + 1))
                value = pairs.adjust(value, raw) ?: raw
            }
        }

        fun backspace() = apply {
            val c = value.selection.start
            val raw = TextFieldValue(value.text.removeRange(c - 1, c), TextRange(c - 1))
            value = pairs.adjust(value, raw) ?: raw
        }

        /** The caret moved without typing (arrow key, click). */
        fun caret(at: Int) = apply { value = value.copy(selection = TextRange(at)); pairs.observe(value) }

        /** A command (Enter's indent, undo, a completion...) replaced the text. */
        fun command(marked: String) = apply {
            val at = marked.indexOf('|')
            value = TextFieldValue(marked.removeRange(at, at + 1), TextRange(at)); pairs.observe(value)
        }

        override fun toString() = value.text.substring(0, value.selection.start) + "|" + value.text.substring(value.selection.end)
    }

    private fun typing(start: String, chars: String) = Editor(start).type(chars).toString()

    @Test
    fun `an opening bracket gets its closer and the caret stays between`() {
        assertEquals("class A {|}", typing("class A |", "{"))
        assertEquals("\$f(|)", typing("\$f|", "("))
        assertEquals("A -[|]", typing("A -|", "["))
        assertEquals("note \"|\"", typing("note |", "\""))
    }

    @Test
    fun `no closer is added in front of a word`() {
        assertEquals("(|foo", typing("|foo", "("))
        assertEquals("{|\$x", typing("|\$x", "{"))
    }

    @Test
    fun `typing the closer it added steps over it`() {
        assertEquals("\$f()|", typing("\$f|", "()"))
        assertEquals("class A {}|", typing("class A |", "{}"))
        assertEquals("\"a\"|", typing("|", "\"a\""))
        assertEquals("\$f(\$g(x))|", typing("\$f|", "(\$g(x))")) // nested pairs, both stepped over
    }

    @Test
    fun `a closer that was already in the text is typed, not stepped over`() {
        // The issue: adding a paren in front of one that was already there.
        assertEquals("foo(bar)|)", typing("foo(bar|)", ")"))
        assertEquals("note \"a\"|\"", typing("note \"a|\"", "\""))
    }

    @Test
    fun `leaving the pair forgets it`() {
        val e = Editor("x |").type("(") // x (|)
        e.caret(0).caret(3) // out and back in
        assertEquals("x ()|)", e.type(")").toString())
    }

    @Test
    fun `a command that changes the text forgets every pair`() {
        val e = Editor("class A |").type("{") // class A {|}
        e.command("class A {\n  |\n}") // Enter: the closer goes to its own line
        assertEquals("class A {\n  }|\n}", e.type("}").toString())
    }

    @Test
    fun `typing inside the pair keeps it`() {
        assertEquals("note \"hello world\"|", typing("note |", "\"hello world\""))
    }

    @Test
    fun `a quote that closes an open string is not paired`() {
        assertEquals("A : \"say hi\"|", typing("A : \"say hi|", "\""))
    }

    @Test
    fun `backspace on a pair it just added removes both halves`() {
        assertEquals("class A |", Editor("class A |").type("{").backspace().toString())
        assertEquals("|", Editor("|").type("\"").backspace().toString())
    }

    @Test
    fun `backspace on a pair that was already in the text removes only the opener`() {
        assertEquals("class A |}", Editor("class A {|}").backspace().toString())
    }

    @Test
    fun `typing a bracket over a selection wraps it`() {
        val pairs = AutoPairs()
        val old = TextFieldValue("A : hi", TextRange(4, 6))
        pairs.observe(old)
        val r = pairs.adjust(old, TextFieldValue("A : (", TextRange(5)))!!
        assertEquals("A : (hi)", r.text)
        assertEquals(TextRange(5, 7), r.selection)
    }

    @Test
    fun `other edits are not touched`() {
        assertEquals("A ->|", typing("A -|", ">"))
        assertEquals("A '|", typing("A |", "'"))
        val pairs = AutoPairs()
        val old = TextFieldValue("ab", TextRange(2))
        pairs.observe(old)
        assertNull(pairs.adjust(old, TextFieldValue("abcd", TextRange(4)))) // a paste
    }

    @Test
    fun `a paste in front of a pair keeps it`() {
        val e = Editor("|").type("(") // (|)
        val old = e.value
        val pasted = TextFieldValue("(ab)", TextRange(3))
        e.value = e.pairs.adjust(old, pasted) ?: pasted
        assertEquals("(ab)|", e.type(")").toString())
    }
}
