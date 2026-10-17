package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.TestGraph

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.features.workspace.Workspace
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Editor only / editor + preview / preview only, switchable from the tab bar and remembered. */
@OptIn(ExperimentalTestApi::class)
class EditorLayoutTest {

    private lateinit var saved: EditorLayout
    @BeforeTest fun save() { saved = TestGraph.graph.layout.editorLayout.value }
    @AfterTest fun restore() { TestGraph.graph.layout.editorLayout.value = saved } // don't leave the user's prefs changed

    private fun project() = TestGraph.workspace().apply {
        openRoot(File("samples").absoluteFile)
        open(File("samples/pizza.puml"))
        changeLayout(EditorLayout.SPLIT)
    }

    @Test fun cmdPTogglesBetweenEditorOnlyAndSplit() {
        val p = project()
        p.togglePreview(); assertEquals(EditorLayout.EDITOR, p.layout)
        p.togglePreview(); assertEquals(EditorLayout.SPLIT, p.layout)
        p.changeLayout(EditorLayout.PREVIEW)
        p.togglePreview(); assertEquals(EditorLayout.EDITOR, p.layout)
    }

    @Test fun needingTheCodeLeavesPreviewOnlyForSplit() {
        val p = project()
        p.changeLayout(EditorLayout.PREVIEW)
        p.revealEditor(); assertEquals(EditorLayout.SPLIT, p.layout)
        p.changeLayout(EditorLayout.EDITOR)
        p.revealEditor(); assertEquals(EditorLayout.EDITOR, p.layout) // already visible: no change
    }

    @Test fun layoutIsRemembered() {
        project().changeLayout(EditorLayout.PREVIEW)
        assertEquals(EditorLayout.PREVIEW, TestGraph.workspace().layout)
    }

    @Test fun switcherButtonsShowAndHideTheEditorAndPreview() {
        val p = project()
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) {
                    Surface(Modifier.size(1200.dp, 700.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) }
                }
            }
            fun editors() = onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size
            fun previews() = onAllNodes(androidx.compose.ui.test.hasText("Preview")).fetchSemanticsNodes().size
            waitForIdle()
            assertEquals(1, editors()); assertEquals(1, previews())

            onNodeWithContentDescription("Preview Only", substring = true).performClick()
            waitForIdle()
            assertEquals(EditorLayout.PREVIEW, p.layout)
            assertEquals(0, editors()); assertEquals(1, previews())

            onNodeWithContentDescription("Editor Only", substring = true).performClick()
            waitForIdle()
            assertEquals(0, previews()); assertEquals(1, editors())

            onNodeWithContentDescription("Editor and Preview", substring = true).performClick()
            waitForIdle()
            assertEquals(1, editors()); assertEquals(1, previews())
        }
    }
}
