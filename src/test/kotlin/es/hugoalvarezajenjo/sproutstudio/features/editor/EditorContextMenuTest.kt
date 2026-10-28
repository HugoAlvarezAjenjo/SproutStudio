package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.MenuItem
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.MenuSeparator
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The editor's right-click menu: what the screen adds, then the text field's Cut/Copy/Paste. */
@OptIn(ExperimentalTestApi::class)
class EditorContextMenuTest {

    private val text = "@startuml\nAlice -> Bob: hello\n@enduml\n"

    private fun ComposeUiTest.editor(doc: es.hugoalvarezajenjo.sproutstudio.features.document.Document, extra: () -> List<androidx.compose.foundation.ContextMenuItem>) =
        setContent {
            PumlTheme(dark = true) {
                Surface(color = ide.panel) { Box(Modifier.size(500.dp, 300.dp)) { CodeEditor(doc, errorLine = null, contextMenu = extra) } }
            }
        }

    private fun ComposeUiTest.count(tag: String) = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    /** Right-click at [x], [y] dp inside the text field. */
    private fun ComposeUiTest.rightClick(x: Float, y: Float) {
        onNodeWithTag("code-editor-field", useUnmergedTree = true).performMouseInput {
            val at = Offset(x.dp.toPx(), y.dp.toPx())
            moveTo(at); press(MouseButton.Secondary); release(MouseButton.Secondary)
        }
        waitForIdle()
    }

    @Test fun `the screen's entries come first, then the clipboard ones`() {
        var picked = false
        runComposeUiTest {
            editor(TestGraph.document(null, text)) { listOf(MenuItem("Go to Declaration") { picked = true }) }
            rightClick(40f, 30f)
            listOf("Go to Declaration", "Cut", "Copy", "Paste", "Select All").forEach { assertEquals(1, count("menu-item:$it"), it) }
            onNodeWithTag("menu-item:Go to Declaration").performClick()
            waitForIdle()
        }
        assertTrue(picked)
    }

    @Test fun `right-click moves the caret to the clicked line`() {
        val doc = TestGraph.document(null, text)
        runComposeUiTest {
            editor(doc) { emptyList() }
            assertEquals(0, doc.value.selection.start)
            rightClick(40f, 30f) // on the second line
            val line = doc.text.substring(0, doc.value.selection.start).count { it == '\n' } + 1
            assertEquals(2, line)
        }
    }

    @Test fun `right-click inside the selection keeps it`() {
        val doc = TestGraph.document(null, text)
        runComposeUiTest {
            editor(doc) { emptyList() }
            waitForIdle()
            doc.value = doc.value.copy(selection = TextRange(10, doc.text.indexOf("\n@enduml")))
            waitForIdle()
            rightClick(40f, 30f)
            assertEquals(TextRange(10, doc.text.indexOf("\n@enduml")), doc.value.selection)
        }
    }

    @Test fun `one separator between the screen's entries and the clipboard ones`() {
        val a = MenuItem("a"); val cut = MenuItem("Cut")
        assertEquals(listOf(a, MenuSeparator, cut), editorMenuItems(listOf(a), listOf(cut)))
        assertEquals(listOf(cut), editorMenuItems(emptyList(), listOf(cut)))
    }
}
