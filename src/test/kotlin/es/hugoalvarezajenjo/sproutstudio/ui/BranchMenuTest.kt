package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.git.GitRepo
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The status-bar branch button: switch, create and delete branches. */
@OptIn(ExperimentalTestApi::class)
class BranchMenuTest {
    private val dir: File = Files.createTempDirectory("sprout-branch").toFile()
    @AfterTest fun cleanup() { dir.deleteRecursively() }

    private val a get() = File(dir, "a.puml")

    /** main: a.puml = "main"; feature: a.puml = "feature" + only.puml. */
    private fun twoBranches() {
        GitRepo.init(dir).close()
        File(dir, ".git/config").appendText("[user]\n\tname = T\n\temail = t@e\n[commit]\n\tgpgsign = false\n")
        GitRepo.find(dir)!!.use { r ->
            a.writeText("main\n"); r.commit(listOf(a), "main")
            r.createBranch("feature")
            a.writeText("feature\n")
            val only = File(dir, "only.puml").apply { writeText("x\n") }
            r.commit(listOf(a, only), "feature")
            r.checkout("main")
        }
    }

    @Test fun repoBranchOperations() {
        twoBranches()
        GitRepo.find(dir)!!.use { r ->
            assertEquals(listOf("feature", "main"), r.branches())
            assertEquals("main\n", a.readText())
            assertFalse(File(dir, "only.puml").exists())
            assertFailsWith<IllegalArgumentException> { r.createBranch("feature") }
            assertFailsWith<IllegalArgumentException> { r.createBranch("bad..name") }
            assertFailsWith<IllegalArgumentException> { r.deleteBranch("main") }
            // feature has a commit main doesn't: not deleted.
            assertFailsWith<IllegalStateException> { r.deleteBranch("feature") }
            r.createBranch("spike"); r.checkout("main"); r.deleteBranch("spike") // merged (same commit)
            assertEquals(listOf("feature", "main"), r.branches())
        }
    }

    @Test fun conflictingEditsBlockTheSwitchAndSayWhy() {
        twoBranches()
        val p = ProjectState().apply { openRoot(dir) }
        runBlocking {
            p.git.open(dir)
            a.writeText("uncommitted\n")
            assertFalse(p.git.checkout("feature"))
            assertTrue(p.git.error!!.contains("a.puml"), p.git.error)
            assertEquals("main", p.git.status.branch)
            assertEquals("uncommitted\n", a.readText()) // nothing lost
        }
        p.git.close()
    }

    @Test fun switchingReloadsOpenTabsAndClosesMissingOnes() {
        twoBranches()
        val p = ProjectState().apply { openRoot(dir); open(a) }
        runBlocking {
            p.git.open(dir)
            assertTrue(p.switchingBranches { p.git.checkout("feature") })
            assertEquals("feature\n", p.active!!.text)
            p.open(File(dir, "only.puml"))
            assertTrue(p.switchingBranches { p.git.checkout("main") })
        }
        assertEquals(listOf("a.puml"), p.docs.map { it.name }) // only.puml isn't on main
        assertEquals("main\n", p.docs.single().text)
        p.git.close()
    }

    @Test fun popupSwitchesAndCreatesBranches() {
        twoBranches()
        val p = ProjectState().apply { openRoot(dir); open(a) }
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) { Surface(Modifier.size(1200.dp, 700.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) } }
            }
            waitUntil(timeoutMillis = 10_000) { p.git.branches.size == 2 }
            onNodeWithTag("branch-button").performClick()
            onNodeWithTag("branch:feature").performClick()
            waitUntil(timeoutMillis = 10_000) { p.git.status.branch == "feature" }
            waitUntil(timeoutMillis = 5_000) { p.active?.text == "feature\n" }

            onNodeWithTag("branch-button").performClick()
            onNode(hasText("New Branch…") and hasClickAction()).performClick()
            onNodeWithTag("new-branch-name").performTextInput("try this")
            onNode(hasText("Create") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 10_000) { p.git.status.branch == "try-this" }
            assertEquals(listOf("feature", "main", "try-this"), p.git.branches)
        }
        assertNotNull(GitRepo.find(dir)).use { assertEquals("try-this", it.branch()) }
    }
}
