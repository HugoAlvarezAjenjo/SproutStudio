package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import es.hugoalvarezajenjo.sproutstudio.model.AppState
import es.hugoalvarezajenjo.sproutstudio.model.AppWindow
import es.hugoalvarezajenjo.sproutstudio.preview.PreviewPane
import es.hugoalvarezajenjo.sproutstudio.preview.rememberLivePreview
import kotlinx.coroutines.delay

/**
 * The "just show me the diagram" window opened by double-clicking a file.
 * It watches the file, so editing it anywhere else refreshes the picture.
 */
@Composable
fun QuickPreviewWindow(win: AppWindow.QuickPreview) {
    val file = win.file
    var text by remember { mutableStateOf(runCatching { file.readText() }.getOrDefault("")) }
    var missing by remember { mutableStateOf(!file.exists()) }

    LaunchedEffect(file) {
        var stamp = file.lastModified()
        while (true) {
            delay(700)
            missing = !file.exists()
            val now = file.lastModified()
            if (!missing && now != stamp) {
                stamp = now
                text = runCatching { file.readText() }.getOrDefault(text)
            }
        }
    }

    Window(
        onCloseRequest = { AppState.close(win) },
        title = "${file.name} — SproutStudio",
        icon = AppIcon.painter,
        state = rememberWindowState(size = DpSize(980.dp, 720.dp)),
    ) {
        MenuBar {
            Menu("File") {
                Item("Edit This Diagram", shortcut = androidx.compose.ui.input.key.KeyShortcut(Key.E, meta = Dialogs.isMac, ctrl = !Dialogs.isMac)) {
                    AppState.editFile(file, win)
                }
                Item("Open Diagram…") { Dialogs.openDiagram()?.let { AppState.openFile(it) } }
                Item("Open Folder…") { Dialogs.openFolder()?.let { AppState.openFolder(it) } }
                Separator()
                Item("Close", shortcut = androidx.compose.ui.input.key.KeyShortcut(Key.W, meta = Dialogs.isMac, ctrl = !Dialogs.isMac)) { AppState.close(win) }
            }
            Menu("View") {
                CheckboxItem("Dark Theme", checked = ThemePrefs.dark) { ThemePrefs.toggle() }
                CheckboxItem("Dark Diagram Preview", checked = DiagramPrefs.dark) { DiagramPrefs.toggle() }
            }
        }
        PumlTheme {
            Surface(Modifier.fillMaxSize(), color = ide.panel) {
                if (missing) {
                    EmptyState(Icons.Outlined.ErrorOutline, "File not found", file.absolutePath)
                } else {
                    val preview = rememberLivePreview(text, file.parentFile, debounceMs = 50)
                    PreviewPane(
                        preview = preview,
                        text = text,
                        baseDir = file.parentFile,
                        baseName = file.nameWithoutExtension,
                        leadingTools = {
                            Tag(file.name, Icons.Outlined.Schema)
                            Spacer(Modifier.width(4.dp))
                            ToolButton(Icons.Outlined.Edit, "Open in the editor (⌘E)", label = "Edit") { AppState.editFile(file, win) }
                        },
                    )
                }
            }
        }
    }
}
