package es.hugoalvarezajenjo.sproutstudio.features.search

import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.features.workspace.Workspace
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** ⇧⌘F end to end: the panel in a real workspace, over files on disk. */
@OptIn(ExperimentalTestApi::class)
class SearchPanelTest {
    private val dir: File = Files.createTempDirectory("sprout-search").toFile()
    private var sidebar = true
    @BeforeTest fun save() { sidebar = TestGraph.graph.layout.sidebarVisible.value }
    @AfterTest fun cleanup() { TestGraph.graph.layout.sidebarVisible.value = sidebar; dir.deleteRecursively() }

    @Test
    fun typingListsTheHitsAndClickingOneOpensItSelected() {
        val text = "@startuml\nUser -> OrderService : buy\n@enduml\n"
        val a = File(dir, "flows/a.puml").apply { parentFile.mkdirs(); writeText(text) }
        File(dir, "b.puml").writeText("@startuml\nA -> B\n@enduml\n")
        val p = TestGraph.workspace().apply { openRoot(dir); findInFiles() }
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) { Surface(Modifier.size(1200.dp, 700.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) } }
            }
            onNodeWithTag("search-field").performTextInput("orderservice")
            waitUntil(timeoutMillis = 5_000) { p.search.result.hitCount == 1 }
            assertEquals(1, onAllNodesWithTag("search-hit").fetchSemanticsNodes().size)

            onNodeWithTag("search-hit").performClick()
            waitForIdle()
            val doc = p.active!!
            assertEquals(a.absoluteFile, doc.file?.absoluteFile)
            val start = text.indexOf("OrderService")
            assertEquals(TextRange(start, start + "OrderService".length), doc.value.selection)
        }
        p.close()
    }
}
