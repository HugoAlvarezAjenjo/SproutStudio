package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Soft wrap: a long line fits the editor's width instead of running off to the right. */
@OptIn(ExperimentalTestApi::class)
class SoftWrapUiTest {

    private val text = "@startuml\n" + "Alice -> Bob: " + "a long message that goes on ".repeat(8) + "\n@enduml\n"
    private val editorWidth = 360.dp

    /** The text field's width and height with [softWrap] on or off. */
    private fun fieldSize(softWrap: Boolean) = run {
        var w = 0.dp
        var h = 0.dp
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) {
                    Surface(color = ide.panel) {
                        Box(Modifier.size(editorWidth, 100.dp)) {
                            CodeEditor(TestGraph.document(null, text), errorLine = null, softWrap = softWrap)
                        }
                    }
                }
            }
            waitForIdle()
            // The node's own size: bounds in root are clipped to the viewport and hide the overflow.
            val node = onNodeWithTag("code-editor-field", useUnmergedTree = true).fetchSemanticsNode()
            with(density) {
                w = node.size.width.toDp()
                h = node.size.height.toDp()
            }
        }
        w to h
    }

    @Test fun `without soft wrap the long line makes the field wider than the editor`() {
        assertTrue(fieldSize(softWrap = false).first > editorWidth)
    }

    @Test fun `with soft wrap the field fits the editor and the line takes more rows`() {
        val (wrappedW, wrappedH) = fieldSize(softWrap = true)
        val (_, plainH) = fieldSize(softWrap = false)
        // The tagged node is the text area, inside the field's side padding.
        assertEquals(editorWidth - EditorMetrics.gutter - EditorMetrics.textStart - EditorMetrics.textEnd, wrappedW)
        // The editor is short, so the field's height is the text's own: more rows, taller field.
        assertTrue(wrappedH > plainH, "wrapped $wrappedH vs plain $plainH")
    }
}
