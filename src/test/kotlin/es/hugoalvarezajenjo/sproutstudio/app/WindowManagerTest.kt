package es.hugoalvarezajenjo.sproutstudio.app

import java.io.File
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
}
