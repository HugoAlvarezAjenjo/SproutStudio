package es.hugoalvarezajenjo.sproutstudio.features.quickpreview


import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceDeps
import es.hugoalvarezajenjo.sproutstudio.features.workspace.openDiagramDialog
import es.hugoalvarezajenjo.sproutstudio.features.workspace.openFolderDialog
import java.io.File
import es.hugoalvarezajenjo.sproutstudio.core.os.Os

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
import es.hugoalvarezajenjo.sproutstudio.features.preview.PreviewPane
import es.hugoalvarezajenjo.sproutstudio.features.preview.rememberPreviewViewModel
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.AppIcon
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.EmptyState
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Tag
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import kotlinx.coroutines.delay

/**
 * The "just show me the diagram" window opened by double-clicking a file.
 * It watches the file, so editing it anywhere else refreshes the picture.
 */
@Composable
fun QuickPreviewWindow(file: File, deps: WorkspaceDeps, onClose: () -> Unit) {
    var text by remember { mutableStateOf(runCatching { file.readText() }.getOrDefault("")) }
    var missing by remember { mutableStateOf(!file.exists()) }

    // Watch the file for outside edits; the disk is read off the UI thread.
    LaunchedEffect(file) {
        val io = deps.dispatchers.io
        var stamp = kotlinx.coroutines.withContext(io) { file.lastModified() }
        while (true) {
            delay(700)
            val (exists, now) = kotlinx.coroutines.withContext(io) { file.exists() to file.lastModified() }
            missing = !exists
            if (exists && now != stamp) {
                stamp = now
                kotlinx.coroutines.withContext(io) { runCatching { file.readText() }.getOrNull() }?.let { text = it }
            }
        }
    }

    Window(
        onCloseRequest = onClose,
        title = "${file.name} — SproutStudio",
        icon = AppIcon.painter,
        state = rememberWindowState(size = DpSize(980.dp, 720.dp)),
    ) {
        MenuBar {
            Menu("File") {
                Item("Edit This Diagram", shortcut = androidx.compose.ui.input.key.KeyShortcut(Key.E, meta = Os.isMac, ctrl = !Os.isMac)) {
                    deps.windows.editFile(file)
                }
                Item("Open Diagram…") { deps.openDiagramDialog() }
                Item("Open Folder…") { deps.openFolderDialog() }
                Separator()
                Item("Close", shortcut = androidx.compose.ui.input.key.KeyShortcut(Key.W, meta = Os.isMac, ctrl = !Os.isMac)) { onClose() }
            }
            Menu("View") {
                CheckboxItem("Dark Theme", checked = deps.theme.dark.value) { deps.theme.toggle() }
                CheckboxItem("Dark Diagram Preview", checked = deps.diagram.dark.value) { deps.diagram.toggle() }
            }
        }
        PumlTheme(dark = deps.theme.dark.value) {
            Surface(Modifier.fillMaxSize(), color = ide.panel) {
                if (missing) {
                    EmptyState(Icons.Outlined.ErrorOutline, "File not found", file.absolutePath)
                } else {
                    val preview = rememberPreviewViewModel(deps, text, file.parentFile, debounceMs = 50)
                    PreviewPane(
                        preview = preview,
                        baseDir = file.parentFile,
                        baseName = file.nameWithoutExtension,
                        leadingTools = {
                            Tag(file.name, Icons.Outlined.Schema)
                            Spacer(Modifier.width(4.dp))
                            ToolButton(Icons.Outlined.Edit, "Open in the editor (⌘E)", label = "Edit") { deps.windows.editFile(file) }
                        },
                    )
                }
            }
        }
    }
}
