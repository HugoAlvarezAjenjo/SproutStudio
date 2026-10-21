package es.hugoalvarezajenjo.sproutstudio

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Not unit-testable at runtime (needs a real Finder launch), so guard the startup ORDER in
 * Main.kt: touching the Dock (Taskbar) before the Desktop handlers are registered (MacPlatform.integrate) makes macOS
 * deliver the cold-start "open file" event too early, it is dropped, and a double-click on a
 * .puml opened the welcome window instead of (or next to) the preview.
 */
class StartupOrderTest {
    private val main = File("src/main/kotlin/es/hugoalvarezajenjo/sproutstudio/app/Main.kt").readText()

    @Test
    fun `dock icon is installed only after the open-file and quit handlers`() {
        // The handlers are registered by Platform.integrate (MacPlatform), so Main must call it first.
        val dock = main.indexOf("AppIcon.installInDock()")
        val integrate = main.indexOf("graph.platform.integrate(")
        assertTrue(dock > 0 && integrate > 0, "expected calls not found in Main.kt")
        assertTrue(dock > integrate, "installInDock() must come after platform.integrate()")
        val mac = File("src/main/kotlin/es/hugoalvarezajenjo/sproutstudio/platform/MacPlatform.kt").readText()
        val body = mac.substringAfter("override fun integrate(")
        assertTrue("setOpenFileHandler" in body && "setQuitHandler" in body, "MacPlatform.integrate must register both handlers")
    }

    @Test
    fun `boot welcome is decided on the UI thread`() {
        val boot = main.indexOf("windows.openBootWelcome()")
        val edt = main.indexOf("SwingUtilities.invokeAndWait")
        assertTrue(edt in 0 until boot, "openBootWelcome() must run inside SwingUtilities.invokeAndWait")
    }
}
