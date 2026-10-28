package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Dragging a selection past the editor's edge scrolls the text and keeps selecting. */
@OptIn(ExperimentalTestApi::class)
class DragSelectScrollUiTest {

    private val lines = 200
    private val text = (1..lines).joinToString("\n") { "line $it" } + "\n"

    private fun Document.lineOf(offset: Int) = text.substring(0, offset).count { it == '\n' } + 1

    /** An editor 200 dp tall (about ten lines) in the middle of a 500 dp window, so the pointer can leave it. */
    private fun ComposeUiTest.editor(doc: Document) {
        mainClock.autoAdvance = false
        setContent {
            PumlTheme(dark = true) {
                Surface(color = ide.panel) {
                    Box(Modifier.size(400.dp, 500.dp)) {
                        Box(Modifier.size(400.dp, 200.dp).align(Alignment.Center)) {
                            CodeEditor(doc, errorLine = null)
                        }
                    }
                }
            }
        }
        frames(6)
    }

    private fun ComposeUiTest.frames(n: Int) = repeat(n) { mainClock.advanceTimeBy(16) }

    /** Press at height [from], drag to [to] (in the window) and hold there for a second, still pressed. */
    private fun ComposeUiTest.dragAndHold(from: Dp, to: Dp) {
        val x = with(density) { 120.dp.toPx() }
        fun y(d: Dp) = with(density) { d.toPx() }
        onRoot().performMouseInput {
            moveTo(Offset(x, y(from))); press()
            moveTo(Offset(x, (y(from) + y(to)) / 2)); moveTo(Offset(x, y(to)))
        }
        frames(60)
    }

    @Test fun `dragging below the editor scrolls down and extends the selection`() {
        val doc = TestGraph.document(null, text)
        runComposeUiTest {
            editor(doc)
            dragAndHold(from = 160.dp, to = 480.dp)
            val sel = doc.value.selection
            assertEquals(1, doc.lineOf(sel.min))
            // Only about ten lines fit: without scrolling the selection would stop there.
            assertTrue(doc.lineOf(sel.max) > 30, "selection ends on line ${doc.lineOf(sel.max)}")
            onRoot().performMouseInput { release() }
        }
    }

    @Test fun `dragging above the editor scrolls up and extends the selection`() {
        val doc = TestGraph.document(null, text)
        runComposeUiTest {
            editor(doc)
            doc.jumpTo(TextRange(doc.text.length)) // the end of the file, a third down the editor
            frames(60)
            dragAndHold(from = 200.dp, to = 20.dp)
            val sel = doc.value.selection
            assertTrue(doc.lineOf(sel.max) > lines - 10, "selection ends on line ${doc.lineOf(sel.max)}")
            assertTrue(doc.lineOf(sel.min) < lines - 30, "selection starts on line ${doc.lineOf(sel.min)}")
            onRoot().performMouseInput { release() }
        }
    }
}
