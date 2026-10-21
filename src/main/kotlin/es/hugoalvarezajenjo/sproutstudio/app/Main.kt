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
import es.hugoalvarezajenjo.sproutstudio.platform.AppHooks
import es.hugoalvarezajenjo.sproutstudio.services.render.PlantUmlEngine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.AppIcon
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ProvideLanguage
import kotlinx.coroutines.delay
import java.io.File
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    // Offline lockdown before any PlantUML class reads its configuration.
    PlantUmlEngine.configureOffline()
    val graph = AppGraph.default
    val windows = graph.windows
    // OS hooks (macOS: Finder "open file" events, ⌘Q, the app menu) before the UI starts, so a
    // cold-start double-click is not lost.
    graph.platform.integrate(
        AppHooks(
            openFiles = { files -> files.forEach(windows::openFile) },
            openSettings = windows::openSettings,
            beforeQuit = { prepareQuit(graph) },
            dark = graph.theme.dark.value,
        ),
    )

    // Our logo in the Dock instead of the Java/Kotlin one (matters for `./gradlew run`).
    // MUST come after integrate(): touching Taskbar finishes AppKit's launch, and an
    // "open file" event that arrives before setOpenFileHandler is dropped (double-click lost).
    AppIcon.installInDock()

    // Command line: `sproutstudio diagram.puml` -> quick preview, `sproutstudio folder/` -> project.
    // Run on the UI thread, like the OS "open file" hook, so the two can't interleave
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
