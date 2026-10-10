package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.app.AppGraph
import es.hugoalvarezajenjo.sproutstudio.domain.session.OpenTab
import es.hugoalvarezajenjo.sproutstudio.domain.session.ProjectSession
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Reopening a project brings back its tabs, the active one and each caret. */
class WorkspaceSessionTest {
    private val graph = AppGraph.forTests() // its own settings: restore on, nothing recorded yet
    private val dir = Files.createTempDirectory("sprout-session").toFile()

    private fun file(name: String, text: String = "@startuml\nAlice -> Bob : hi\n@enduml\n") =
        File(dir, name).apply { parentFile.mkdirs(); writeText(text) }

    @AfterTest
    fun tearDown() { dir.deleteRecursively() }

    /** A window that opens [dir], is used by [use], then closes (which records the session). */
    private fun session(use: WorkspaceViewModel.() -> Unit) {
        graph.newWorkspace().apply { openRoot(dir); use(); close() }
    }

    private fun reopened() = graph.newWorkspace().apply { openRoot(dir) }

    @Test
    fun tabsActiveTabAndCaretComeBack() {
        val a = file("a.puml"); val b = file("sub/b.puml"); file("c.puml")
        session {
            open(a); open(b)
            active!!.value = active!!.value.copy(selection = TextRange(10, 15))
            selectTab(1)
        }
        val p = reopened()
        assertEquals(listOf(a.absoluteFile, b.absoluteFile), p.docs.map { it.file })
        assertEquals(1, p.activeIndex)
        assertEquals(TextRange(10, 15), p.docs[1].value.selection)
        assertTrue(p.docs.none { it.dirty } && p.docs.none { it.canUndo }, "reopening isn't an edit")
    }

    @Test
    fun turnedOffNothingIsReopened() {
        val a = file("a.puml")
        session { open(a) }
        graph.session.restore.value = false
        assertTrue(reopened().docs.isEmpty())
    }

    @Test
    fun turningItBackOnReopensTheLatestTabs() {
        val a = file("a.puml"); val b = file("b.puml")
        graph.session.restore.value = false
        session { open(a) }
        session { open(b) } // nothing reopened while off, but still recorded
        graph.session.restore.value = true
        assertEquals(listOf(b.absoluteFile), reopened().docs.map { it.file })
    }

    @Test
    fun aDeletedFileIsSkippedAndTheActiveTabFollows() {
        val a = file("a.puml"); val b = file("b.puml")
        session { open(a); open(b); selectTab(1) }
        b.delete()
        val p = reopened()
        assertEquals(listOf(a.absoluteFile), p.docs.map { it.file })
        assertEquals(0, p.activeIndex)
    }

    @Test
    fun untitledDiagramsAreNotRecorded() {
        val a = file("a.puml")
        session { open(a); docs += Document(null, "x", graph.documents) }
        assertEquals(listOf(a.absoluteFile), reopened().docs.map { it.file })
    }

    @Test
    fun anOpenWindowRecordsItsTabsAsTheyChange() {
        val a = file("a.puml")
        val p = graph.newWorkspace().apply { openRoot(dir); startSessionTracking() }
        p.open(a)
        p.active!!.value = p.active!!.value.copy(selection = TextRange(12))
        Snapshot.sendApplyNotifications() // what a frame does in the app
        val expected = ProjectSession(listOf(OpenTab("a.puml", 12, 12)), 0)
        val deadline = System.currentTimeMillis() + 5000
        while (graph.session.sessionToReopen(dir) != expected && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertEquals(expected, graph.session.sessionToReopen(dir), "recorded without closing the window (a crash keeps it)")
        p.close()
    }

    @Test
    fun aCaretPastAShortenedFileIsClamped() {
        val a = file("a.puml")
        session { open(a); active!!.value = active!!.value.copy(selection = TextRange(30)) }
        a.writeText("@startuml\n")
        assertEquals(TextRange(10), reopened().docs.single().value.selection)
    }

    @Test
    fun closingTheProjectRecordsItsTabs() {
        val a = file("a.puml")
        graph.newWorkspace().apply { openRoot(dir); open(a); closeProject() }
        assertEquals(listOf(a.absoluteFile), reopened().docs.map { it.file })
    }

    @Test
    fun aHugeSessionKeepsItsFirstTabs() {
        val tabs = (1..400).map { OpenTab("folder-with-a-long-name/diagram-number-$it.puml", 0, 0) }
        graph.session.save(dir, ProjectSession(tabs, active = 399))
        val kept = graph.session.sessionToReopen(dir)!!
        assertTrue(kept.tabs.size in 1 until 400)
        assertEquals(tabs.take(kept.tabs.size), kept.tabs)
        assertEquals(kept.tabs.lastIndex, kept.active)
    }
}
