package es.hugoalvarezajenjo.sproutstudio.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class EditOpsTest {

    private fun at(text: String, caret: Int = text.length) = TextFieldValue(text, TextRange(caret))

    @Test
    fun `newline keeps indentation`() {
        val r = EditOps.newline(at("@startuml\n  A -> B"))
        assertEquals("@startuml\n  A -> B\n  ", r.text)
        assertEquals(r.text.length, r.selection.start)
    }

    @Test
    fun `newline indents after a block opener`() {
        assertEquals("alt ok\n  ", EditOps.newline(at("alt ok")).text)
        assertEquals("class A {\n  ", EditOps.newline(at("class A {")).text)
        assertEquals("  loop 3 times\n    ", EditOps.newline(at("  loop 3 times")).text)
    }

    @Test
    fun `pressing enter after end pulls it back to the opener level`() {
        val r = EditOps.newline(at("alt ok\n  A -> B\n  end"))
        assertEquals("alt ok\n  A -> B\nend\n", r.text)
    }

    @Test
    fun `else is dedented and its body indented`() {
        val r = EditOps.newline(at("alt ok\n  A -> B\n  else"))
        assertEquals("alt ok\n  A -> B\nelse\n  ", r.text)
    }

    @Test
    fun `closer already at the right level is left alone`() {
        val r = EditOps.newline(at("alt ok\n  A -> B\nend"))
        assertEquals("alt ok\n  A -> B\nend\n", r.text)
    }

    @Test
    fun `toggle comment adds and removes`() {
        val on = EditOps.toggleComment(at("  A -> B", 3))
        assertEquals("  ' A -> B", on.text)
        val off = EditOps.toggleComment(on)
        assertEquals("  A -> B", off.text)
    }

    @Test
    fun `toggle comment on a multi-line selection`() {
        val v = TextFieldValue("A\nB\nC", TextRange(0, 3))
        assertEquals("' A\n' B\nC", EditOps.toggleComment(v).text)
    }

    @Test
    fun `indent and outdent selected lines`() {
        val v = TextFieldValue("A\nB", TextRange(0, 3))
        val ind = EditOps.indent(v)
        assertEquals("  A\n  B", ind.text)
        assertEquals("A\nB", EditOps.outdent(ind).text)
    }

    @Test
    fun `duplicate line`() {
        val r = EditOps.duplicateLines(at("A\nB\nC", 2))
        assertEquals("A\nB\nB\nC", r.text)
        assertEquals(4, r.selection.start)
    }

    @Test
    fun `select line`() {
        assertEquals(TextRange(2, 5), EditOps.selectLine("A\nBCD\nE", 2))
    }
}
