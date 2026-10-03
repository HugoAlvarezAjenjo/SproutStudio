package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.git.ChangeType
import es.hugoalvarezajenjo.sproutstudio.git.GitRepo
import es.hugoalvarezajenjo.sproutstudio.model.LayoutPrefs
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import es.hugoalvarezajenjo.sproutstudio.model.SidebarTool
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Commit tool window against a real (temporary) repository. */
@OptIn(ExperimentalTestApi::class)
class CommitPanelTest {
    private val dir: File = Files.createTempDirectory("sprout-commit").toFile()
    private var sidebar = true
    @BeforeTest fun save() { sidebar = LayoutPrefs.sidebarVisible }
    @AfterTest fun cleanup() { LayoutPrefs.sidebarVisible = sidebar; dir.deleteRecursively() }

    private fun configure() =
        File(dir, ".git/config").appendText("[user]\n\tname = Test\n\temail = test@example.com\n[commit]\n\tgpgsign = false\n")

    private fun write(name: String, text: String) = File(dir, name).apply { writeText(text) }

    /** a.puml committed then edited, b.puml new. */
    private fun repoWithChanges(): Pair<File, File> {
        GitRepo.init(dir).close(); configure()
        val a = write("a.puml", "@startuml\nA -> B\n@enduml\n")
        GitRepo.find(dir)!!.use { it.commit(listOf(a), "init") }
        a.writeText("@startuml\nA -> B\nB -> C\n@enduml\n")
        val b = write("b.puml", "@startuml\nX -> Y\n@enduml\n")
        return a to b
    }

    private fun project() = ProjectState().apply { openRoot(dir); showTool(SidebarTool.COMMIT) }

    @Test fun commitsTheCheckedFilesWithTheMessage() {
        val (a, b) = repoWithChanges()
        val p = project()
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) { Surface(Modifier.size(1200.dp, 700.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) } }
            }
            waitUntil(timeoutMillis = 10_000) { p.git.status.changes.size == 2 }
            // Tracked changes are in by default, new files are not (like IntelliJ).
            assertTrue(p.git.isIncluded(p.git.status.changes.first { it.path == "a.puml" }))
            assertEquals(ChangeType.UNVERSIONED, p.git.status.changes.first { it.path == "b.puml" }.type)
            assertEquals(listOf("a.puml"), p.git.included.map { it.path })

            onNodeWithContentDescription("Include b.puml").performClick()
            waitForIdle()
            assertEquals(listOf("a.puml", "b.puml"), p.git.included.map { it.path })

            onNodeWithTag("commit-message").performTextInput("Add B and C")
            onNode(hasText("Commit") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 10_000) { p.git.status.changes.isEmpty() && p.git.lastResult != null }
            assertEquals("", p.git.commitMessage)
        }
        GitRepo.find(dir)!!.use { r ->
            assertEquals(a.readText(), r.headText(a))
            assertEquals(b.readText(), r.headText(b))
        }
    }

    @Test fun uncheckedFilesStayOutOfTheCommit() {
        val (a, b) = repoWithChanges()
        val p = project()
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) { Surface(Modifier.size(1200.dp, 700.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) } }
            }
            waitUntil(timeoutMillis = 10_000) { p.git.status.changes.size == 2 }
            onNodeWithContentDescription("Include b.puml").performClick()
            onNodeWithContentDescription("Include a.puml").performClick() // a out, b in
            onNodeWithTag("commit-message").performTextInput("Only B")
            onNode(hasText("Commit") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 10_000) { p.git.lastResult != null }
            waitUntil(timeoutMillis = 10_000) { p.git.status.changes.map { it.path } == listOf("a.puml") }
        }
        GitRepo.find(dir)!!.use { r ->
            assertEquals("@startuml\nA -> B\n@enduml\n", r.headText(a)) // edit not committed
            assertNotNull(r.headText(b))
        }
    }

    @Test fun createsARepositoryForAFolderWithoutOne() {
        write("doc.puml", "@startuml\nA -> B\n@enduml\n")
        val p = project()
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) { Surface(Modifier.size(1200.dp, 700.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) } }
            }
            waitForIdle()
            onNode(hasText("Create Git Repository") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 10_000) { p.git.isRepo && p.git.status.changes.isNotEmpty() }
            assertEquals("main", p.git.status.branch)
            // The starter .gitignore is on by default.
            assertEquals(
                listOf(".gitignore" to ChangeType.UNVERSIONED, "doc.puml" to ChangeType.UNVERSIONED),
                p.git.status.changes.map { it.path to it.type },
            )
            assertTrue(File(dir, ".gitignore").readText().contains(".DS_Store"))
        }
        assertTrue(File(dir, ".git").isDirectory)
    }

    @Test fun rollbackRestoresTheFileAndTheOpenEditor() {
        val (a, _) = repoWithChanges()
        val p = ProjectState().apply { openRoot(dir); open(a) }
        val doc = p.active!!
        runBlocking {
            p.git.open(dir)
            assertEquals(ChangeType.MODIFIED, p.git.changeOf(a)?.type)
            assertTrue(p.git.rollback(a))
        }
        doc.replaceFromDisk()
        assertEquals("@startuml\nA -> B\n@enduml\n", a.readText())
        assertEquals(a.readText(), doc.text)
        assertTrue(!doc.dirty)
        assertNull(p.git.changeOf(a))
        p.git.close()
    }
}
