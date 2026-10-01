package es.hugoalvarezajenjo.sproutstudio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Not unit-testable at runtime (needs a real Finder launch), so guard the startup ORDER in
 * Main.kt: touching the Dock (Taskbar) before the Desktop handlers are registered makes macOS
 * deliver the cold-start "open file" event too early, it is dropped, and a double-click on a
 * .puml opened the welcome window instead of (or next to) the preview.
 */
class StartupOrderTest {
    private val main = File("src/main/kotlin/es/hugoalvarezajenjo/sproutstudio/Main.kt").readText()

    @Test
    fun `dock icon is installed only after the open-file and quit handlers`() {
        val dock = main.indexOf("AppIcon.installInDock()")
        val open = main.indexOf("setOpenFileHandler")
        val quit = main.indexOf("setQuitHandler")
        assertTrue(dock > 0 && open > 0 && quit > 0, "expected calls not found in Main.kt")
        assertTrue(dock > open && dock > quit, "installInDock() must come after the Desktop handlers")
    }

    @Test
    fun `boot welcome is decided on the UI thread`() {
        val boot = main.indexOf("AppState.openBootWelcome()")
        val edt = main.indexOf("SwingUtilities.invokeAndWait")
        assertTrue(edt in 0 until boot, "openBootWelcome() must run inside SwingUtilities.invokeAndWait")
    }
}
