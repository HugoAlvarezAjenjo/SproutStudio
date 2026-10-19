package es.hugoalvarezajenjo.sproutstudio.features.preview

import es.hugoalvarezajenjo.sproutstudio.TestGraph
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.features.workspace.Workspace
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The preview toolbar's theme dropdown writes the `!theme` line of the shown diagram. */
@OptIn(ExperimentalTestApi::class)
class ThemeMenuTest {

    private lateinit var saved: EditorLayout
    @BeforeTest fun save() { saved = TestGraph.graph.layout.editorLayout.value }
    @AfterTest fun restore() { TestGraph.graph.layout.editorLayout.value = saved }

    @Test fun pickingAThemeEditsTheDiagramAndDefaultTakesItOut() {
        val dir = Files.createTempDirectory("theme-menu").toFile()
        val file = dir.resolve("a.puml").apply { writeText("@startuml\nAlice -> Bob: hi\n@enduml\n") }
        val p = TestGraph.workspace().apply { openRoot(dir); open(file); changeLayout(EditorLayout.SPLIT) }
        val doc = p.active!!
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) {
                    Surface(Modifier.size(1200.dp, 700.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) }
                }
            }
            // Enabled once the first render has counted the diagrams.
            waitUntil(timeoutMillis = 10_000) {
                onAllNodes(hasContentDescription("Diagram theme", substring = true) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }

            onNodeWithContentDescription("Diagram theme", substring = true).performClick()
            onNodeWithText("cerulean").performClick()
            waitForIdle()
            assertEquals("@startuml\n!theme cerulean\nAlice -> Bob: hi\n@enduml\n", doc.text)
            onNodeWithContentDescription("Diagram theme: cerulean", substring = true).assertExists()

            onNodeWithContentDescription("Diagram theme", substring = true).performClick()
            onNodeWithText("Default").performClick()
            waitForIdle()
            assertEquals("@startuml\nAlice -> Bob: hi\n@enduml\n", doc.text)
        }
        dir.deleteRecursively()
    }
}
