package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The editor's right-click menu inside the workspace: the workspace's actions, then the clipboard. */
@OptIn(ExperimentalTestApi::class)
class EditorMenuTest {
    private val dir: File = Files.createTempDirectory("editor-menu").toFile()
    private val file = File(dir, "a.puml").apply { writeText("@startuml\nAlice -> Bob: hello\n\n@enduml\n") }

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    private fun ComposeUiTest.count(tag: String) = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().size

    private fun ComposeUiTest.show(p: WorkspaceViewModel) = setContent {
        PumlTheme(dark = true) { Surface(Modifier.size(1100.dp, 600.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) } }
    }

    /** The menu as it would open with the caret at [offset] (opening it is EditorContextMenuTest's job). */
    private fun labels(p: WorkspaceViewModel, offset: Int): List<String> {
        val d = p.active!!
        d.value = d.value.copy(selection = TextRange(offset))
        return EditorMenu(p).items(d).map { it.label }.filter { it.isNotEmpty() }
    }

    @Test fun `navigation entries only on a symbol`() {
        val p = TestGraph.workspace().apply { openRoot(dir); open(file) }
        val onAlice = file.readText().indexOf("Alice") + 1
        val onBlank = file.readText().indexOf("\n\n") + 1
        assertTrue("Go to Declaration" in labels(p, onAlice))
        assertTrue("Go to Declaration" !in labels(p, onBlank))
        // Outside a repository: no git entries.
        assertTrue(labels(p, onAlice).none { it.contains("History") || it.contains("HEAD") })
    }

    @Test fun `comment from the menu comments the caret's line`() {
        val p = TestGraph.workspace().apply { openRoot(dir); open(file) }
        runComposeUiTest {
            show(p)
            waitForIdle()
            val d = p.active!!
            d.value = d.value.copy(selection = TextRange(d.text.indexOf("Alice")))
            EditorMenu(p).items(d).first { it.label == "Comment / Uncomment Lines" }.onClick()
            waitForIdle()
            assertTrue(d.text.contains("' Alice -> Bob"), d.text)
        }
    }

    @Test fun `the menu opens in the editor with the workspace entries and the clipboard ones`() {
        val p = TestGraph.workspace().apply { openRoot(dir); open(file) }
        runComposeUiTest {
            show(p)
            waitForIdle()
            onNodeWithTag("code-editor-field", useUnmergedTree = true).performMouseInputRightClick()
            waitForIdle()
            listOf("Comment / Uncomment Lines", "Find…", "Copy Diagram Image", "Export", "Copy", "Paste").forEach {
                assertEquals(1, count("menu-item:$it"), it)
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.SemanticsNodeInteraction.performMouseInputRightClick() =
    performMouseInput { moveTo(androidx.compose.ui.geometry.Offset(60.dp.toPx(), 30.dp.toPx())); press(androidx.compose.ui.test.MouseButton.Secondary); release(androidx.compose.ui.test.MouseButton.Secondary) }
