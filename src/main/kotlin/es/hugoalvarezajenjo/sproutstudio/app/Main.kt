package es.hugoalvarezajenjo.sproutstudio.app

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.application
import es.hugoalvarezajenjo.sproutstudio.features.git.DiagramDiffWindow
import es.hugoalvarezajenjo.sproutstudio.features.git.HistoryWindow
import es.hugoalvarezajenjo.sproutstudio.features.quickpreview.QuickPreviewWindow
import es.hugoalvarezajenjo.sproutstudio.features.settings.SettingsWindow
import es.hugoalvarezajenjo.sproutstudio.features.workspace.ProjectWindow
import es.hugoalvarezajenjo.sproutstudio.services.render.PlantUmlEngine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.AppIcon
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ProvideLanguage
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.io.File
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    // Offline lockdown before any PlantUML class reads its configuration.
    PlantUmlEngine.configureOffline()
    val graph = AppGraph.default
    val windows = graph.windows
    System.setProperty("apple.awt.application.name", "SproutStudio")
    // Native title bar follows the app theme (read once at startup; AWT can't switch it live).
    System.setProperty(
        "apple.awt.application.appearance",
        if (graph.theme.dark.value) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua",
    )

    // macOS delivers Finder double-clicks as an "open file" event, not as argv.
    // Register before the UI starts so a cold-start double-click is not lost.
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
        Desktop.getDesktop().setOpenFileHandler { e ->
            SwingUtilities.invokeLater { e.files.forEach { windows.openFile(it) } }
        }
    }

    // ⌘Q quits without closing windows one by one, so flush autosave first (no edit lost) and
    // record the session for the next launch.
    // This runs on AppKit's thread, not the UI thread, so the blocking flush can't deadlock.
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
        Desktop.getDesktop().setQuitHandler { _, response ->
            prepareQuit(graph)
            response.performQuit()
        }
    }

    // "SproutStudio → Settings…" (⌘,) in the macOS app menu.
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_PREFERENCES)) {
        Desktop.getDesktop().setPreferencesHandler { SwingUtilities.invokeLater { windows.openSettings() } }
    }

    // Our logo in the Dock instead of the Java/Kotlin one (matters for `./gradlew run`).
    // MUST come after the Desktop handlers: touching Taskbar finishes AppKit's launch, and an
    // "open file" event that arrives before setOpenFileHandler is dropped (double-click lost).
    AppIcon.installInDock()

    // Command line: `sproutstudio diagram.puml` -> quick preview, `sproutstudio folder/` -> project.
    // Run on the UI thread, like the Finder "open file" handler, so the two can't interleave
    // (a file landing between the isEmpty() check and openBootWelcome() gave two windows).
    SwingUtilities.invokeAndWait {
        args.map(::File).filter { it.exists() }.forEach { windows.openFile(it) }
        // Compose ends the application if its first composition has no window, so a plain launch
        // composes the welcome window right away, but hidden (see WindowManager.openBootWelcome).
        if (windows.windows.isEmpty()) windows.openBootWelcome()
    }

    application {
        // No file arrived while we started up: this was a plain launch, show the welcome.
        LaunchedEffect(Unit) {
            delay(1200)
            windows.revealBootWelcome()
        }
        // File → Exit (Windows, Linux): the same as ⌘Q, then end the Compose application.
        windows.onQuit = { prepareQuit(graph); exitApplication() }
        val sawWindow = remember { mutableStateOf(false) }
        LaunchedEffect(windows.windows.size) {
            if (windows.windows.isNotEmpty()) sawWindow.value = true
            else if (sawWindow.value) exitApplication()
        }

        // Every window, title and menu in the language picked in Settings, switching live.
        ProvideLanguage(graph.language.resolved) {
            for (w in windows.windows) {
                key(w.id) {
                    val close = { windows.close(w) }
                    when (w) {
                        is AppWindow.QuickPreview -> QuickPreviewWindow(w.file, graph.deps, onClose = close)
                        is AppWindow.Project -> ProjectWindow(w.state, w.revealed, onClose = close)
                        is AppWindow.DiagramDiff -> DiagramDiffWindow(w.project, w.file, w.left, w.right, w.textFirst, onClose = close)
                        is AppWindow.History -> HistoryWindow(w.project, w.file, onClose = close)
                        is AppWindow.Settings -> SettingsWindow(graph.deps, onClose = close)
                    }
                }
            }
        }
    }
}

/** Before the app ends: flush autosave (no edit lost) and record the session for the next launch. */
private fun prepareQuit(graph: AppGraph) {
    graph.autoSaver.flushNow(graph.windows.windows.filterIsInstance<AppWindow.Project>().flatMap { it.state.docs.toList() })
    graph.windows.rememberSession()
}
