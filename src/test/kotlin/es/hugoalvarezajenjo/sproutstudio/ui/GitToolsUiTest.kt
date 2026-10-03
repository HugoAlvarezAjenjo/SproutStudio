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
import es.hugoalvarezajenjo.sproutstudio.git.DiffSide
import es.hugoalvarezajenjo.sproutstudio.git.GitRepo
import es.hugoalvarezajenjo.sproutstudio.model.LayoutPrefs
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import es.hugoalvarezajenjo.sproutstudio.model.SidebarTool
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Amend, undo, stash, stash-and-switch, history and text diff, through the real UI. */
@OptIn(ExperimentalTestApi::class)
class GitToolsUiTest {
    private val dir: File = Files.createTempDirectory("sprout-tools").toFile()
    private var sidebar = true
    @BeforeTest fun save() { sidebar = LayoutPrefs.sidebarVisible }
    @AfterTest fun cleanup() { LayoutPrefs.sidebarVisible = sidebar; dir.deleteRecursively() }

    private val a get() = File(dir, "a.puml")

    /** Two commits of a.puml ("one" -> v1, "two" -> v2), then an uncommitted edit (v3). */
    private fun repo(): ProjectState {
        GitRepo.init(dir).close()
        File(dir, ".git/config").appendText("[user]\n\tname = T\n\temail = t@e\n[commit]\n\tgpgsign = false\n")
        GitRepo.find(dir)!!.use { r ->
            a.writeText("@startuml\nA -> B: v1\n@enduml\n"); r.commit(listOf(a), "one")
            a.writeText("@startuml\nA -> B: v2\n@enduml\n"); r.commit(listOf(a), "two")
        }
        a.writeText("@startuml\nA -> B: v3\n@enduml\n")
        return ProjectState().apply { openRoot(dir); showTool(SidebarTool.COMMIT) }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.workspace(p: ProjectState) = setContent {
        PumlTheme(dark = true) { Surface(Modifier.size(1300.dp, 760.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) } }
    }

    @Test fun amendReplacesTheLastCommitStartingFromItsMessage() {
        val p = repo()
        runComposeUiTest {
            workspace(p)
            waitUntil(timeoutMillis = 10_000) { p.git.lastCommit?.message == "two" && p.git.status.changes.isNotEmpty() }
            onNodeWithContentDescription("Amend").performClick()
            waitForIdle()
            assertEquals("two", p.git.commitMessage) // pre-filled
            onNodeWithTag("commit-message").performTextInput(" (fixed)")
            onNode(hasText("Amend Commit") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 10_000) { p.git.status.changes.isEmpty() && p.git.lastResult != null }
        }
        GitRepo.find(dir)!!.use { r ->
            val h = r.history(a)
            assertEquals(2, h.size) // replaced, not added
            assertTrue(h[0].message.contains("two") && h[0].message.contains("(fixed)"), h[0].message)
            assertEquals(a.readText(), r.headText(a))
        }
    }

    @Test fun undoLastCommitBringsItsChangesBackAndItsMessage() {
        val p = repo()
        runComposeUiTest {
            workspace(p)
            waitUntil(timeoutMillis = 10_000) { p.git.lastCommit?.message == "two" }
            onNodeWithContentDescription("Undo last commit (keeps its changes)").performClick()
            // The message is filled in right after the reset finishes.
            waitUntil(timeoutMillis = 10_000) { p.git.lastCommit?.message == "one" && p.git.commitMessage.isNotEmpty() }
            assertEquals("two", p.git.commitMessage)
            assertEquals(ChangeType.MODIFIED, p.git.changeOf(a)?.type)
        }
        assertTrue(a.readText().contains("v3")) // disk untouched
    }

    @Test fun stashPopupStashesAndPops() {
        val p = repo()
        runComposeUiTest {
            workspace(p)
            waitUntil(timeoutMillis = 10_000) { p.git.status.changes.isNotEmpty() }
            onNodeWithContentDescription("Stash changes").performClick()
            onNodeWithTag("stash-message").performTextInput("try v3")
            onNode(hasText("Stash") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 10_000) { p.git.stashes.size == 1 && p.git.status.changes.isEmpty() }
            assertTrue(a.readText().contains("v2"))
            onNode(hasText("Pop") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 10_000) { p.git.stashes.isEmpty() && p.git.status.changes.isNotEmpty() }
        }
        assertTrue(a.readText().contains("v3"))
    }

    @Test fun blockedSwitchOffersStashAndSwitch() {
        val p = repo()
        GitRepo.find(dir)!!.use { r ->
            // feature changes a.puml too, so the local edit blocks the switch.
            val keep = a.readText()
            r.stash(null); r.createBranch("feature")
            a.writeText("@startuml\nA -> B: feature\n@enduml\n"); r.commit(listOf(a), "feature")
            r.checkout("main"); r.popStash(0)
            check(a.readText() == keep)
        }
        runComposeUiTest {
            workspace(p)
            waitUntil(timeoutMillis = 10_000) { p.git.branches.size == 2 && p.git.status.changes.isNotEmpty() }
            onNodeWithTag("branch-button").performClick()
            onNodeWithTag("branch:feature").performClick()
            waitUntil(timeoutMillis = 10_000) { p.git.blockedCheckout == "feature" }
            onNode(hasText("Stash changes and switch to feature") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 10_000) { p.git.status.branch == "feature" }
            assertEquals(1, p.git.stashes.size)
        }
        assertTrue(a.readText().contains("feature"))
    }

    @Test fun historyListsCommitsAndTextDiffShowsTheChange() {
        val p = repo()
        kotlinx.coroutines.runBlocking { p.git.open(dir) }
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) { Surface(Modifier.size(1300.dp, 760.dp), color = ide.panel) { HistoryView(p, a) } }
            }
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("one")).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(onAllNodes(hasText("two")).fetchSemanticsNodes().isNotEmpty())
            // Newest selected: "Changes in <two>" in text mode shows v1 -> v2.
            onNode(hasText("Text") and hasClickAction()).performClick()
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("A -> B: v2")).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(onAllNodes(hasText("A -> B: v1")).fetchSemanticsNodes().isNotEmpty())
            assertTrue(onAllNodes(hasText("+1")).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test fun textDiffAgainstHeadForTheWorkingCopy() {
        val p = repo()
        kotlinx.coroutines.runBlocking { p.git.open(dir) }
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) {
                    Surface(Modifier.size(1300.dp, 760.dp), color = ide.panel) { DiffView(p, a, DiffSide.Head, DiffSide.WorkingCopy, textFirst = true) }
                }
            }
            waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText("A -> B: v3")).fetchSemanticsNodes().isNotEmpty() }
            assertTrue(onAllNodes(hasText("A -> B: v2")).fetchSemanticsNodes().isNotEmpty())
            onNodeWithTag("text-diff")
        }
        p.git.close()
    }
}
