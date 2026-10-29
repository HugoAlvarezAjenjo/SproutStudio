package es.hugoalvarezajenjo.sproutstudio.features.outline

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.features.workspace.SidebarTool
import es.hugoalvarezajenjo.sproutstudio.features.workspace.Workspace
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OutlinePanelTest {

    private lateinit var saved: EditorLayout
    @BeforeTest fun save() { saved = TestGraph.graph.layout.editorLayout.value }
    @AfterTest fun restore() { TestGraph.graph.layout.editorLayout.value = saved }

    private val text = "@startuml\npackage shop {\n  class Order\n}\nactor User\nUser -> Order\n@enduml\n"

    @Test fun `clicking an entry selects its name in the editor, and the toggle folds it`() {
        val dir = Files.createTempDirectory("outline").toFile()
        val file = dir.resolve("a.puml").apply { writeText(text) }
        // Preview only: picking an entry has to bring the editor back.
        val p = TestGraph.workspace().apply { openRoot(dir); open(file); changeLayout(EditorLayout.PREVIEW); showTool(SidebarTool.OUTLINE) }
        val doc = p.active!!
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) {
                    Surface(Modifier.size(1200.dp, 700.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) }
                }
            }
            onNodeWithTag("outline-row-Order").performClick()
            waitForIdle()
            val at = text.indexOf("Order")
            assertEquals(TextRange(at, at + "Order".length), doc.value.selection)
            assertEquals(EditorLayout.SPLIT, p.layout)

            onNodeWithTag("outline-toggle-shop").performClick()
            waitForIdle()
            onNodeWithTag("outline-row-Order").assertDoesNotExist()
            onNodeWithTag("outline-row-User").assertExists()
        }
        dir.deleteRecursively()
    }

    @Test fun `a diagram that declares nothing says so`() {
        val dir = Files.createTempDirectory("outline").toFile()
        val file = dir.resolve("m.puml").apply { writeText("@startmindmap\n* root\n@endmindmap\n") }
        val p = TestGraph.workspace().apply { openRoot(dir); open(file); showTool(SidebarTool.OUTLINE) }
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) {
                    Surface(Modifier.size(1200.dp, 700.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) }
                }
            }
            onNodeWithText("Nothing to outline").assertExists()
        }
        dir.deleteRecursively()
    }
}
