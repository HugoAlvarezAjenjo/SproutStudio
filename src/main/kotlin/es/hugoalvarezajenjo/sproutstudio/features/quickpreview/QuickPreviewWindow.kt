package es.hugoalvarezajenjo.sproutstudio.features.quickpreview

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.common_close
import es.hugoalvarezajenjo.sproutstudio.resources.menu_dark_diagram_preview
import es.hugoalvarezajenjo.sproutstudio.resources.menu_dark_theme
import es.hugoalvarezajenjo.sproutstudio.resources.menu_file
import es.hugoalvarezajenjo.sproutstudio.resources.menu_open_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.menu_open_folder
import es.hugoalvarezajenjo.sproutstudio.resources.menu_settings
import es.hugoalvarezajenjo.sproutstudio.resources.menu_view
import es.hugoalvarezajenjo.sproutstudio.resources.quick_edit
import es.hugoalvarezajenjo.sproutstudio.resources.quick_edit_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.quick_not_found
import es.hugoalvarezajenjo.sproutstudio.resources.quick_open_editor
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr


import androidx.compose.ui.input.key.KeyShortcut
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
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.map

/**
 * The "just show me the diagram" window opened by double-clicking a file.
 * It watches the file, so editing it anywhere else refreshes the picture.
 */
@Composable
fun QuickPreviewWindow(file: File, deps: WorkspaceDeps, onClose: () -> Unit) {
    // Follows the file, so editing it anywhere else refreshes the picture; read off the UI thread.
    val disk by remember(file) { deps.documents.watch(file).map { DiskText(it) } }.collectAsState(null)
    val text = disk?.text.orEmpty()
    val missing = disk != null && disk?.text == null

    Window(
        onCloseRequest = onClose,
        title = "${file.name} — SproutStudio",
        icon = AppIcon.painter,
        state = rememberWindowState(size = DpSize(980.dp, 720.dp)),
    ) {
        MenuBar {
            Menu(tr(Res.string.menu_file)) {
                Item(tr(Res.string.quick_edit_diagram), shortcut = KeyShortcut(Key.E, meta = Os.isMac, ctrl = !Os.isMac)) {
                    deps.windows.editFile(file)
                }
                Item(tr(Res.string.menu_open_diagram)) { deps.openDiagramDialog() }
                Item(tr(Res.string.menu_open_folder)) { deps.openFolderDialog() }
                Separator()
                Item(tr(Res.string.common_close), shortcut = KeyShortcut(Key.W, meta = Os.isMac, ctrl = !Os.isMac)) { onClose() }
            }
            Menu(tr(Res.string.menu_view)) {
                CheckboxItem(tr(Res.string.menu_dark_theme), checked = deps.theme.dark.value) { deps.theme.toggle() }
                CheckboxItem(tr(Res.string.menu_dark_diagram_preview), checked = deps.diagram.dark.value) { deps.diagram.toggle() }
                Separator()
                Item(tr(Res.string.menu_settings)) { deps.windows.openSettings() }
            }
        }
        PumlTheme(dark = deps.theme.dark.value) {
            Surface(Modifier.fillMaxSize(), color = ide.panel) {
                if (missing) {
                    EmptyState(Icons.Outlined.ErrorOutline, tr(Res.string.quick_not_found), file.absolutePath)
                } else {
                    val preview = rememberPreviewViewModel(deps, text, file.parentFile, debounceMs = 50)
                    PreviewPane(
                        preview = preview,
                        baseDir = file.parentFile,
                        baseName = file.nameWithoutExtension,
                        leadingTools = {
                            Tag(file.name, Icons.Outlined.Schema)
                            Spacer(Modifier.width(4.dp))
                            ToolButton(Icons.Outlined.Edit, tr(Res.string.quick_open_editor), label = tr(Res.string.quick_edit)) { deps.windows.editFile(file) }
                        },
                    )
                }
            }
        }
    }
}

/** One read of the watched file; [text] null = it doesn't exist. (A null DiskText = not read yet.) */
private class DiskText(val text: String?)
