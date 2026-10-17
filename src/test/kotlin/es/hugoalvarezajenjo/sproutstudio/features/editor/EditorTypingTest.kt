package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.TestGraph

import androidx.compose.material3.Surface
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.features.editor.CodeEditor
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import kotlin.test.Test
import kotlin.test.assertEquals

/** Types into the real editor (composition + layout + draw per keystroke), off-screen. */
@OptIn(ExperimentalTestApi::class)
class EditorTypingTest {

    private fun typeAtEnd(initial: String, chars: String): String =
        edit(initial, TextRange(initial.length)) { field ->
            // One character at a time, each followed by a full frame, like real typing.
            for (ch in chars) { field.performTextInput(ch.toString()); waitForIdle() }
        }

    /** Opens [initial] with [selection] in the real editor and runs [actions] on its text field. */
    private fun edit(initial: String, selection: TextRange, actions: ComposeUiTest.(SemanticsNodeInteraction) -> Unit): String {
        val doc = TestGraph.document(null, initial)
        doc.value = doc.value.copy(selection = selection)
        runComposeUiTest {
            setContent { PumlTheme(dark = true) { Surface(color = ide.panel) { CodeEditor(doc, errorLine = null) } } }
            waitForIdle()
            actions(onNode(hasSetTextAction()))
        }
        return doc.text
    }

    @Test
    fun typingAtEndOfFileWithCompletionPopupDoesNotCrash() {
        // "par" opens the completion popup (participant) right at the end of the text.
        val initial = "@startuml\nactor User\n"
        assertEquals(initial + "par", typeAtEnd(initial, "par"))
    }

    @Test
    fun typingAtEndOfFileWithoutTrailingNewlineDoesNotCrash() {
        val initial = "@startuml\nUser -> App"
        assertEquals(initial + ": hi", typeAtEnd(initial, ": hi"))
    }

    @Test
    fun bracketsCloseThemselvesAndTheCloserIsSteppedOver() {
        // "{" adds "}" after the caret, and typing ")" next to its pair moves past it.
        assertEquals("class A {x}", typeAtEnd("class A ", "{x"))
        assertEquals("\$f(a)b", typeAtEnd("\$f", "(a)b"))
    }

    @Test
    fun aCloserAlreadyInTheFileIsTypedNotSteppedOver() {
        val text = edit("foo(bar)", TextRange(7)) { field ->
            field.performTextInput(")")
            waitForIdle()
        }
        assertEquals("foo(bar))", text)
    }

    @Test
    fun typingABracketOverASelectionWrapsIt() {
        val initial = "A -> B : hi"
        val text = edit(initial, TextRange(9, 11)) { field ->
            field.performTextInput("(")
            waitForIdle()
        }
        assertEquals("A -> B : (hi)", text)
    }

    @Test
    fun backspaceInsideAPairItJustAddedDeletesBothHalves() {
        val text = edit("class A ", TextRange(8)) { field ->
            field.performTextInput("{")
            waitForIdle()
            field.performKeyInput { pressKey(Key.Backspace) }
            waitForIdle()
        }
        assertEquals("class A ", text)
    }
}
