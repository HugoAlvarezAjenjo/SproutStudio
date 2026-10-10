package es.hugoalvarezajenjo.sproutstudio.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Window bookkeeping on launch: a Finder double-click must give ONE window, the preview. */
class WindowManagerTest {

    private val pizza = File("samples/pizza.puml").absoluteFile

    private val graph = AppGraph.forTests()
    private val wm = graph.windows

    @Test
    fun bootWelcomeStartsHidden() {
        wm.openBootWelcome()
        val w = assertIs<AppWindow.Project>(wm.windows.single())
        assertFalse(w.revealed)
    }

    @Test
    fun coldStartDoubleClickShowsOnlyThePreview() {
        wm.openBootWelcome()
        wm.openFile(pizza) // the Apple "open file" event, arriving after startup
        assertIs<AppWindow.QuickPreview>(wm.windows.single())
    }

    @Test
    fun lateDoubleClickAfterWelcomeShownStillReplacesUntouchedWelcome() {
        wm.openBootWelcome()
        wm.revealBootWelcome() // slow cold start: welcome already visible
        wm.openFile(pizza)
        assertIs<AppWindow.QuickPreview>(wm.windows.single())
    }

    @Test
    fun welcomeTheUserIsUsingIsKept() {
        wm.openBootWelcome()
        wm.revealBootWelcome()
        (wm.windows.single() as AppWindow.Project).state.newDocument() // user started typing
        wm.openFile(pizza)
        assertEquals(2, wm.windows.size)
    }

    @Test
    fun plainLaunchRevealsWelcome() {
        wm.openBootWelcome()
        wm.revealBootWelcome()
        assertTrue((wm.windows.single() as AppWindow.Project).revealed)
    }

    // ── Restore session: the projects open at the last quit come back on a plain launch ──

    private val a = Files.createTempDirectory("sprout-a").toFile().absoluteFile
    private val b = Files.createTempDirectory("sprout-b").toFile().absoluteFile

    @AfterTest
    fun tearDown() { a.deleteRecursively(); b.deleteRecursively() }

    private fun relaunch(): WindowManager {
        val next = AppGraph(graph.store).windows // same settings, as on the next launch
        next.openBootWelcome()
        next.revealBootWelcome()
        return next
    }

    private fun WindowManager.roots() = windows.filterIsInstance<AppWindow.Project>().map { it.state.root }

    @Test
    fun quitWithTwoProjectsReopensBoth() {
        wm.openFolder(a); wm.openFolder(b)
        wm.rememberSession()
        wm.windows.toList().forEach(wm::close) // windows going away after ⌘Q don't rewrite it
        val next = relaunch()
        assertEquals(listOf(a, b), next.roots())
        assertTrue(next.windows.all { (it as AppWindow.Project).revealed })
    }

    @Test
    fun closingTheLastWindowReopensItsProject() {
        wm.openFolder(a); wm.openFolder(b)
        wm.windows.toList().forEach(wm::close) // a first, then b: closing b ends the app
        assertEquals(listOf(b), relaunch().roots())
    }

    @Test
    fun quittingFromTheWelcomeShowsTheWelcomeNextTime() {
        wm.openFolder(a)
        (wm.windows.single() as AppWindow.Project).state.closeProject()
        wm.rememberSession()
        val next = relaunch()
        assertEquals(listOf(null), next.roots())
        assertTrue((next.windows.single() as AppWindow.Project).revealed)
    }

    @Test
    fun turnedOffTheWelcomeShows() {
        wm.openFolder(a)
        wm.rememberSession()
        graph.session.restore.value = false
        assertEquals(listOf(null), relaunch().roots())
    }

    @Test
    fun aFileOpenedAtLaunchWinsOverTheSession() {
        wm.openFolder(a)
        wm.rememberSession()
        val next = AppGraph(graph.store).windows
        next.openBootWelcome()
        next.openFile(pizza) // Finder double-click on a cold start
        next.revealBootWelcome()
        assertIs<AppWindow.QuickPreview>(next.windows.single())
    }
}
