package es.hugoalvarezajenjo.sproutstudio

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.window.application
import es.hugoalvarezajenjo.sproutstudio.model.AppState
import es.hugoalvarezajenjo.sproutstudio.model.AppWindow
import es.hugoalvarezajenjo.sproutstudio.render.PlantUmlRenderer
import es.hugoalvarezajenjo.sproutstudio.ui.ProjectWindow
import es.hugoalvarezajenjo.sproutstudio.ui.QuickPreviewWindow
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.io.File
import javax.swing.SwingUtilities

fun main(args: Array<String>) {
    // Offline lockdown before any PlantUML class reads its configuration.
    PlantUmlRenderer.configureOffline()
    System.setProperty("apple.awt.application.name", "SproutStudio")
    // Native title bar follows the app theme (read once at startup; AWT can't switch it live).
    System.setProperty(
        "apple.awt.application.appearance",
        if (es.hugoalvarezajenjo.sproutstudio.ui.ThemePrefs.dark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua",
    )

    // macOS delivers Finder double-clicks as an "open file" event, not as argv.
    // Register before the UI starts so a cold-start double-click is not lost.
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_OPEN_FILE)) {
        Desktop.getDesktop().setOpenFileHandler { e ->
            SwingUtilities.invokeLater { e.files.forEach { AppState.openFile(it) } }
        }
    }

    // Command line: `sproutstudio diagram.puml` -> quick preview, `sproutstudio folder/` -> project.
    args.map(::File).filter { it.exists() }.forEach { AppState.openFile(it) }

    // Compose ends the application if its first composition has no window, so a plain launch
    // composes the welcome window right away, but hidden (see AppState.openBootWelcome).
    if (AppState.windows.isEmpty()) AppState.openBootWelcome()

    application {
        // No file arrived while we started up: this was a plain launch, show the welcome.
        LaunchedEffect(Unit) {
            delay(1200)
            AppState.revealBootWelcome()
        }
        val sawWindow = remember { mutableStateOf(false) }
        LaunchedEffect(AppState.windows.size) {
            if (AppState.windows.isNotEmpty()) sawWindow.value = true
            else if (sawWindow.value) exitApplication()
        }

        for (w in AppState.windows) {
            key(w.id) {
                when (w) {
                    is AppWindow.QuickPreview -> QuickPreviewWindow(w)
                    is AppWindow.Project -> ProjectWindow(w)
                }
            }
        }
    }
}
