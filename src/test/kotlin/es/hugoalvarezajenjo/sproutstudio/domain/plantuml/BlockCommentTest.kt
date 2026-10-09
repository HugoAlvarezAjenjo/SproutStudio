package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockCommentTest {

    private fun at(text: String, caret: Int = text.length) = TextFieldValue(text, TextRange(caret))

    @Test
    fun `no selection inserts an empty comment around the caret`() {
        val r = BlockComment.toggle(at("A -> B", 6))
        assertEquals("A -> B/'  '/", r.text)
        assertEquals(TextRange(9), r.selection)
    }

    @Test
    fun `an inline selection is wrapped and unwrapped again`() {
        val on = BlockComment.toggle(TextFieldValue("A -> B : hi", TextRange(9, 11)))
        assertEquals("A -> B : /' hi '/", on.text)
        val off = BlockComment.toggle(on)
        assertEquals("A -> B : hi", off.text)
        assertEquals(TextRange(9, 11), off.selection)
    }

    @Test
    fun `several lines get the markers on lines of their own`() {
        val src = "@startuml\n  A -> B\n  B -> C\n@enduml"
        val on = BlockComment.toggle(TextFieldValue(src, TextRange(12, 26)))
        assertEquals("@startuml\n  /'\n  A -> B\n  B -> C\n  '/\n@enduml", on.text)
        val off = BlockComment.toggle(on)
        assertEquals(src, off.text)
        assertEquals("  A -> B\n  B -> C", src.substring(off.selection.min, off.selection.max))
    }

    @Test
    fun `the comment is removed with the caret anywhere inside it`() {
        val src = "A\n/'\nB\nC\n'/\nD"
        val r = BlockComment.toggle(at(src, src.indexOf('C')))
        assertEquals("A\nB\nC\nD", r.text)
        assertEquals(TextRange("A\nB\n".length), r.selection)
    }

    @Test
    fun `a comment closed before the caret is left alone`() {
        val r = BlockComment.toggle(at("/' x '/ A", 9))
        assertEquals("/' x '/ A/'  '/", r.text)
    }
}
