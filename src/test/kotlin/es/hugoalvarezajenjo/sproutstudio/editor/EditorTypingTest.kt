package es.hugoalvarezajenjo.sproutstudio.editor

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.model.Document
import es.hugoalvarezajenjo.sproutstudio.ui.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.ide
import kotlin.test.Test
import kotlin.test.assertEquals

/** Types into the real editor (composition + layout + draw per keystroke), off-screen. */
@OptIn(ExperimentalTestApi::class)
class EditorTypingTest {

    private fun typeAtEnd(initial: String, chars: String): String {
        val doc = Document(null, initial)
        doc.value = doc.value.copy(selection = TextRange(initial.length))
        runComposeUiTest {
            setContent { PumlTheme(dark = true) { Surface(color = ide.panel) { CodeEditor(doc, errorLine = null) } } }
            waitForIdle()
            val field = onNode(hasSetTextAction())
            // One character at a time, each followed by a full frame, like real typing.
            for (ch in chars) { field.performTextInput(ch.toString()); waitForIdle() }
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
}
