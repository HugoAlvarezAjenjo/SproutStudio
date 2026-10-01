package es.hugoalvarezajenjo.sproutstudio.model

import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Window bookkeeping on launch: a Finder double-click must give ONE window, the preview. */
class AppStateTest {

    private val pizza = File("samples/pizza.puml").absoluteFile

    @BeforeTest fun reset() = AppState.windows.clear()

    @Test
    fun bootWelcomeStartsHidden() {
        AppState.openBootWelcome()
        val w = assertIs<AppWindow.Project>(AppState.windows.single())
        assertFalse(w.revealed)
    }

    @Test
    fun coldStartDoubleClickShowsOnlyThePreview() {
        AppState.openBootWelcome()
        AppState.openFile(pizza) // the Apple "open file" event, arriving after startup
        assertIs<AppWindow.QuickPreview>(AppState.windows.single())
    }

    @Test
    fun lateDoubleClickAfterWelcomeShownStillReplacesUntouchedWelcome() {
        AppState.openBootWelcome()
        AppState.revealBootWelcome() // slow cold start: welcome already visible
        AppState.openFile(pizza)
        assertIs<AppWindow.QuickPreview>(AppState.windows.single())
    }

    @Test
    fun welcomeTheUserIsUsingIsKept() {
        AppState.openBootWelcome()
        AppState.revealBootWelcome()
        (AppState.windows.single() as AppWindow.Project).state.newDocument() // user started typing
        AppState.openFile(pizza)
        assertEquals(2, AppState.windows.size)
    }

    @Test
    fun plainLaunchRevealsWelcome() {
        AppState.openBootWelcome()
        AppState.revealBootWelcome()
        assertTrue((AppState.windows.single() as AppWindow.Project).revealed)
    }
}
