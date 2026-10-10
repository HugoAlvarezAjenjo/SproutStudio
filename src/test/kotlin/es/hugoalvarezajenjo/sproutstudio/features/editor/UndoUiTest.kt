package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PlantUmlFormatter
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import androidx.compose.material3.Surface
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals

/** ⌘Z / ⇧⌘Z in the real editor, for typing and for edits made by commands. */
@OptIn(ExperimentalTestApi::class)
class UndoUiTest {

    private val cmd = if (Os.isMac) Key.MetaLeft else Key.CtrlLeft

    private fun SemanticsNodeInteraction.undo() = performKeyInput { withKeyDown(cmd) { pressKey(Key.Z) } }
    private fun SemanticsNodeInteraction.redo() =
        performKeyInput { withKeyDown(cmd) { withKeyDown(Key.ShiftLeft) { pressKey(Key.Z) } } }

    private fun editor(doc: Document, block: (SemanticsNodeInteraction) -> Unit) = runComposeUiTest {
        setContent { PumlTheme(dark = true) { Surface(color = ide.panel) { CodeEditor(doc, errorLine = null) } } }
        waitForIdle()
        block(onNode(hasSetTextAction()).apply { requestFocus() }) // key presses go to the focused field
        waitForIdle()
    }

    @Test
    fun undoesAndRedoesMoveLine() {
        val start = "a\nb\nc\n"
        val doc = TestGraph.document(null, start).apply { value = value.copy(selection = TextRange(2)) }
        editor(doc) { field ->
            field.performKeyInput { withKeyDown(Key.AltLeft) { pressKey(Key.DirectionUp) } }
            assertEquals("b\na\nc\n", doc.text)
            field.undo()
            assertEquals(start, doc.text)
            assertEquals(TextRange(2), doc.value.selection)
            field.redo()
            assertEquals("b\na\nc\n", doc.text)
        }
    }

    @Test
    fun undoesAReformatDoneFromOutsideTheEditor() {
        val start = "@startuml\n  A->B\n@enduml\n"
        val doc = TestGraph.document(null, start)
        editor(doc) { field ->
            doc.value = PlantUmlFormatter.format(doc.value) // what ⌥⌘L does
            assertEquals("@startuml\nA -> B\n@enduml\n", doc.text)
            field.undo()
            assertEquals(start, doc.text)
        }
    }

    @Test
    fun typingIsUndoneAsOneStepAndCommandsAsAnother() {
        val doc = TestGraph.document(null, "x\n").apply { value = value.copy(selection = TextRange(1)) }
        editor(doc) { field ->
            for (ch in "yz") field.performTextInput(ch.toString())
            field.performKeyInput { withKeyDown(cmd) { pressKey(Key.D) } } // duplicate line
            assertEquals("xyz\nxyz\n", doc.text)
            field.undo()
            assertEquals("xyz\n", doc.text)
            field.undo()
            assertEquals("x\n", doc.text)
        }
    }
}
