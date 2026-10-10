package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.dialog_cancel
import es.hugoalvarezajenjo.sproutstudio.resources.dialog_dont_save
import es.hugoalvarezajenjo.sproutstudio.resources.dialog_save_changes
import es.hugoalvarezajenjo.sproutstudio.resources.dialog_unsaved_changes
import es.hugoalvarezajenjo.sproutstudio.resources.menu_close_project
import es.hugoalvarezajenjo.sproutstudio.resources.menu_close_tab
import es.hugoalvarezajenjo.sproutstudio.resources.menu_code
import es.hugoalvarezajenjo.sproutstudio.resources.menu_commit
import es.hugoalvarezajenjo.sproutstudio.resources.menu_compare_with_head
import es.hugoalvarezajenjo.sproutstudio.resources.menu_create_git_repo
import es.hugoalvarezajenjo.sproutstudio.resources.menu_dark_diagram_preview
import es.hugoalvarezajenjo.sproutstudio.resources.menu_dark_theme
import es.hugoalvarezajenjo.sproutstudio.resources.menu_edit
import es.hugoalvarezajenjo.sproutstudio.resources.menu_file
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_in_files
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_action
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_next
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_previous
import es.hugoalvarezajenjo.sproutstudio.resources.menu_fold_all
import es.hugoalvarezajenjo.sproutstudio.resources.menu_fold_block
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_usages
import es.hugoalvarezajenjo.sproutstudio.resources.menu_rename_symbol
import es.hugoalvarezajenjo.sproutstudio.resources.menu_go_to_declaration
import es.hugoalvarezajenjo.sproutstudio.resources.menu_reformat_code
import es.hugoalvarezajenjo.sproutstudio.resources.menu_navigate_back
import es.hugoalvarezajenjo.sproutstudio.resources.menu_navigate_forward
import es.hugoalvarezajenjo.sproutstudio.resources.menu_git
import es.hugoalvarezajenjo.sproutstudio.resources.menu_help
import es.hugoalvarezajenjo.sproutstudio.resources.menu_new_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.menu_new_from_template
import es.hugoalvarezajenjo.sproutstudio.resources.menu_new_window
import es.hugoalvarezajenjo.sproutstudio.resources.menu_open_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.menu_open_folder
import es.hugoalvarezajenjo.sproutstudio.resources.menu_refresh_files
import es.hugoalvarezajenjo.sproutstudio.resources.menu_refresh_git_status
import es.hugoalvarezajenjo.sproutstudio.resources.menu_replace
import es.hugoalvarezajenjo.sproutstudio.resources.menu_rollback_lines
import es.hugoalvarezajenjo.sproutstudio.resources.menu_save
import es.hugoalvarezajenjo.sproutstudio.resources.menu_save_as
import es.hugoalvarezajenjo.sproutstudio.resources.menu_save_automatically
import es.hugoalvarezajenjo.sproutstudio.resources.menu_settings
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_commit_panel
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_diff_with_head
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_history
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_preview
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_problems
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_project_panel
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_templates_panel
import es.hugoalvarezajenjo.sproutstudio.resources.menu_stash_changes
import es.hugoalvarezajenjo.sproutstudio.resources.menu_undo_last_commit
import es.hugoalvarezajenjo.sproutstudio.resources.menu_unfold_all
import es.hugoalvarezajenjo.sproutstudio.resources.menu_unfold_block
import es.hugoalvarezajenjo.sproutstudio.resources.menu_view
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Template
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

import es.hugoalvarezajenjo.sproutstudio.core.os.Os

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import es.hugoalvarezajenjo.sproutstudio.features.editor.changeAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.editor.findNext
import es.hugoalvarezajenjo.sproutstudio.features.editor.foldAll
import es.hugoalvarezajenjo.sproutstudio.features.editor.foldAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.editor.rollbackAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.editor.selectedText
import es.hugoalvarezajenjo.sproutstudio.features.editor.unfoldAll
import es.hugoalvarezajenjo.sproutstudio.features.editor.unfoldAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.git.canCompareWithHead
import es.hugoalvarezajenjo.sproutstudio.features.git.hasCommittedChanges
import es.hugoalvarezajenjo.sproutstudio.features.palette.CommandPalette
import es.hugoalvarezajenjo.sproutstudio.features.palette.DoubleShiftDetector
import es.hugoalvarezajenjo.sproutstudio.features.palette.projectCommands
import es.hugoalvarezajenjo.sproutstudio.features.templates.TemplateGallery
import es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.features.workspace.SidebarTool
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.AppIcon
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

private fun shortcut(key: Key, shift: Boolean = false, alt: Boolean = false) =
    KeyShortcut(key, meta = Os.isMac, ctrl = !Os.isMac, shift = shift, alt = alt)

@Composable
fun ProjectWindow(p: WorkspaceViewModel, revealed: Boolean, onClose: () -> Unit) {
    // Autosave: 1 s after you stop typing, and whenever you switch tab. Runs in p's own scope.
    LaunchedEffect(p) { p.startAutoSave(); p.startSessionTracking() }

    DisposableEffect(p) { onDispose { p.close() } }

    var paletteOpen by remember { mutableStateOf(false) }
    var galleryOpen by remember { mutableStateOf(false) }
    fun pickTemplate(t: Template) {
        galleryOpen = false
        p.insertTemplate(t)
    }
    val doubleShift = remember { DoubleShiftDetector() }


    val title = buildString {
        p.active?.let { append(if (it.dirty) "● " else ""); append(it.name); append(" — ") }
        append(p.root?.name ?: "SproutStudio")
    }

    Window(
        onCloseRequest = { p.requestClose(p.docs.toList()) { onClose() } },
        title = title,
        icon = AppIcon.painter,
        state = rememberWindowState(size = DpSize(1320.dp, 840.dp)),
        visible = revealed,
        onPreviewKeyEvent = { e ->
            val isShift = e.key == Key.ShiftLeft || e.key == Key.ShiftRight
            when {
                doubleShift.onKey(isShift, down = e.type == KeyEventType.KeyDown) -> { paletteOpen = true; true }
                // ⇧⌘A (IntelliJ's Find Action) as a second way in; ⇧⌘P is on the menu item.
                e.type == KeyEventType.KeyDown && e.key == Key.A && e.isShiftPressed &&
                    (if (Os.isMac) e.isMetaPressed else e.isCtrlPressed) -> { paletteOpen = true; true }
                else -> false
            }
        },
    ) {
        // ...and when the window loses focus (you go to another app), like IntelliJ.
        DisposableEffect(window) {
            val l = object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent?) { p.flushAutoSave() }
                // Back from the terminal (a commit, a checkout...): re-read git status.
                override fun windowGainedFocus(e: WindowEvent?) = p.git.requestRefresh()
            }
            window.addWindowFocusListener(l)
            onDispose { window.removeWindowFocusListener(l) }
        }
        MenuBar {
            Menu(tr(Res.string.menu_file)) {
                Item(tr(Res.string.menu_new_diagram), shortcut = shortcut(Key.N)) { p.newDocument() }
                Item(tr(Res.string.menu_new_from_template), shortcut = shortcut(Key.N, shift = true)) { galleryOpen = true }
                Item(tr(Res.string.menu_open_diagram), shortcut = shortcut(Key.O, shift = true)) { p.openDiagramDialog() }
                Item(tr(Res.string.menu_open_folder), shortcut = shortcut(Key.O)) { p.chooseFolder()?.let { p.openRoot(it) } }
                Item(tr(Res.string.menu_new_window)) { p.deps.windows.newProjectWindow() }
                Separator()
                Item(tr(Res.string.menu_save), shortcut = shortcut(Key.S), enabled = p.active != null) { p.save(p.active) }
                CheckboxItem(tr(Res.string.menu_save_automatically), checked = p.deps.autoSaver.enabled) { p.setAutoSave(it) }
                Item(tr(Res.string.menu_save_as), shortcut = shortcut(Key.S, shift = true), enabled = p.active != null) {
                    p.active?.let { p.saveAs(it) }
                }
                Item(tr(Res.string.menu_close_tab), shortcut = shortcut(Key.W), enabled = p.active != null) {
                    p.active?.let { p.requestCloseTab(it) }
                }
                Separator()
                // On macOS Settings… (⌘,) lives in the app menu (see Main); elsewhere it goes here.
                Item(tr(Res.string.menu_settings), shortcut = if (Os.isMac) null else shortcut(Key.Comma)) { p.deps.windows.openSettings() }
                Item(tr(Res.string.menu_close_project), enabled = p.root != null || p.docs.isNotEmpty()) { p.requestCloseProject() }
            }
            Menu(tr(Res.string.menu_edit)) {
                val d = p.active
                Item(tr(Res.string.menu_find), shortcut = shortcut(Key.F), enabled = d != null) { d?.let { p.revealEditor(); it.find.open(replace = false, seed = it.selectedText()) } }
                Item(tr(Res.string.menu_replace), shortcut = shortcut(Key.R), enabled = d != null) { d?.let { p.revealEditor(); it.find.open(replace = true, seed = it.selectedText()) } }
                Item(tr(Res.string.menu_find_next), shortcut = shortcut(Key.G), enabled = d != null) { p.revealEditor(); d?.findNext(true) }
                Item(tr(Res.string.menu_find_previous), shortcut = shortcut(Key.G, shift = true), enabled = d != null) { p.revealEditor(); d?.findNext(false) }
                Item(tr(Res.string.menu_find_in_files), shortcut = shortcut(Key.F, shift = true), enabled = p.root != null) { p.findInFiles() }
            }
            Menu(tr(Res.string.menu_code)) {
                val d = p.active
                Item(tr(Res.string.menu_fold_block), shortcut = shortcut(Key.Minus), enabled = d != null) { p.revealEditor(); d?.foldAtCaret() }
                Item(tr(Res.string.menu_unfold_block), shortcut = shortcut(Key.Equals), enabled = d != null) { p.revealEditor(); d?.unfoldAtCaret() }
                Item(tr(Res.string.menu_fold_all), shortcut = shortcut(Key.Minus, shift = true), enabled = d != null) { p.revealEditor(); d?.foldAll() }
                Item(tr(Res.string.menu_unfold_all), shortcut = shortcut(Key.Equals, shift = true), enabled = d != null) { p.revealEditor(); d?.unfoldAll() }
                Separator()
                Item(tr(Res.string.menu_reformat_code), shortcut = shortcut(Key.L, alt = true), enabled = d != null) { p.reformatCode() }
                Separator()
                Item(tr(Res.string.menu_go_to_declaration), shortcut = shortcut(Key.B), enabled = d != null) { p.goToDeclaration() }
                Item(tr(Res.string.menu_find_usages), shortcut = KeyShortcut(Key.F7, alt = true), enabled = d != null) { p.findUsages() }
                Item(tr(Res.string.menu_rename_symbol), shortcut = KeyShortcut(Key.F6, shift = true), enabled = d != null) { p.renameSymbol() }
                Item(tr(Res.string.menu_navigate_back), shortcut = shortcut(Key.DirectionLeft, alt = true), enabled = d?.nav?.canGoBack == true) { p.navigateBack() }
                Item(tr(Res.string.menu_navigate_forward), shortcut = shortcut(Key.DirectionRight, alt = true), enabled = d?.nav?.canGoForward == true) { p.navigateForward() }
            }
            Menu(tr(Res.string.menu_view)) {
                EditorLayout.entries.forEach { l ->
                    RadioButtonItem(
                        tr(l.label),
                        selected = p.layout == l,
                        shortcut = KeyShortcut(l.shortcutKey, meta = Os.isMac, ctrl = !Os.isMac, alt = true),
                    ) { p.changeLayout(l) }
                }
                CheckboxItem(tr(Res.string.menu_show_preview), checked = p.previewVisible, shortcut = shortcut(Key.P)) { p.togglePreview() }
                Separator()
                CheckboxItem(tr(Res.string.menu_show_project_panel), checked = p.sidebarVisible && p.sidebarTool == SidebarTool.PROJECT, shortcut = shortcut(Key.One)) { p.toggleTool(SidebarTool.PROJECT) }
                CheckboxItem(tr(Res.string.menu_show_commit_panel), checked = p.sidebarVisible && p.sidebarTool == SidebarTool.COMMIT, shortcut = shortcut(Key.Zero)) { p.toggleTool(SidebarTool.COMMIT) }
                CheckboxItem(tr(Res.string.menu_show_templates_panel), checked = p.sidebarVisible && p.sidebarTool == SidebarTool.TEMPLATES) { p.toggleTool(SidebarTool.TEMPLATES) }
                CheckboxItem(tr(Res.string.menu_show_problems), checked = p.problemsExpanded, shortcut = shortcut(Key.Six)) { p.toggleProblems() }
                CheckboxItem(tr(Res.string.menu_dark_theme), checked = p.deps.theme.dark.value) { p.deps.theme.toggle() }
                CheckboxItem(tr(Res.string.menu_dark_diagram_preview), checked = p.deps.diagram.dark.value) { p.deps.diagram.toggle() }
                Separator()
                Item(tr(Res.string.menu_refresh_files), shortcut = KeyShortcut(Key.Y, meta = Os.isMac, ctrl = !Os.isMac, alt = true)) { p.explorer.refreshTree() }
            }
            Menu(tr(Res.string.menu_git)) {
                val g = p.git
                Item(tr(Res.string.menu_commit), shortcut = shortcut(Key.K), enabled = p.root != null) { p.focusCommit() }
                Item(tr(Res.string.menu_compare_with_head), enabled = canCompareWithHead(p, p.active?.file)) {
                    p.active?.file?.let { p.deps.windows.compareWithHead(p, it) }
                }
                Item(
                    tr(Res.string.menu_rollback_lines),
                    shortcut = KeyShortcut(Key.Z, meta = Os.isMac, ctrl = !Os.isMac, alt = true),
                    enabled = p.active?.changeAtCaret() != null,
                ) { p.revealEditor(); p.active?.rollbackAtCaret() }
                Item(tr(Res.string.menu_show_diff_with_head), enabled = hasCommittedChanges(p, p.active?.file)) {
                    p.active?.file?.let { p.deps.windows.compareWithHead(p, it, textFirst = true) }
                }
                Item(tr(Res.string.menu_show_history), enabled = g.status.head != null && p.active?.file != null) {
                    p.active?.file?.let { p.deps.windows.showHistory(p, it) }
                }
                Separator()
                Item(tr(Res.string.menu_undo_last_commit), enabled = (g.lastCommit?.parents ?: 0) > 0) {
                    p.gitFlow.undoLastCommit(showCommitPanel = true)
                }
                Item(tr(Res.string.menu_stash_changes), enabled = g.status.head != null) { p.showTool(SidebarTool.COMMIT); g.requestStashPopup() }
                Separator()
                if (!g.isRepo) Item(tr(Res.string.menu_create_git_repo), enabled = p.root != null) { p.gitFlow.init() }
                Item(tr(Res.string.menu_refresh_git_status), enabled = g.isRepo) { g.requestRefresh() }
            }
            Menu(tr(Res.string.menu_help)) {
                Item(tr(Res.string.menu_find_action), shortcut = shortcut(Key.P, shift = true)) { paletteOpen = true }
            }
        }

        PumlTheme(dark = p.deps.theme.dark.value, editorFontSize = p.deps.editor.currentFontSize) {
            Box(Modifier.fillMaxSize()) {
            Surface(color = ide.panel) {
                if (p.root == null && p.docs.isEmpty()) {
                    Welcome(p)
                } else {
                    Workspace(p, onSave = { d -> p.save(d) }, onCloseTab = { d -> p.requestCloseTab(d) })
                }
            }

            if (paletteOpen) {
                val commands = projectCommands(
                    p,
                    onSave = { p.save(p.active) },
                    onSaveAs = { p.active?.let { p.saveAs(it) } },
                    onCloseTab = { p.active?.let { p.requestCloseTab(it) } },
                    onOpenGallery = { paletteOpen = false; galleryOpen = true },
                    onCloseProject = { p.requestCloseProject() },
                )
                CommandPalette(
                    commands,
                    recentIds = remember { p.deps.paletteHistory.ids() },
                    onDismiss = { paletteOpen = false; p.active?.requestFocus() },
                    onRun = { cmd ->
                        paletteOpen = false
                        p.deps.paletteHistory.add(cmd.id)
                        cmd.run()
                        // Hand the keyboard back to the (possibly new) active editor.
                        if (cmd.restoresFocus) p.active?.requestFocus()
                    },
                )
            }

            if (galleryOpen) {
                TemplateGallery(p.deps.thumbnails, onDismiss = { galleryOpen = false; p.active?.requestFocus() }, onPick = { pickTemplate(it) })
            }
            }

            p.pendingClose?.let { pc ->
                AlertDialog(
                    onDismissRequest = { p.cancelClose() },
                    containerColor = ide.popup,
                    shape = RoundedCornerShape(10.dp),
                    title = { Text(tr(Res.string.dialog_save_changes), style = MaterialTheme.typography.titleMedium) },
                    text = { Text(tr(Res.string.dialog_unsaved_changes, pc.docs.joinToString { it.name }), style = MaterialTheme.typography.bodyMedium) },
                    confirmButton = {
                        TextButton(onClick = { p.saveAndClose() }) { Text(tr(Res.string.menu_save)) }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { p.cancelClose() }) { Text(tr(Res.string.dialog_cancel)) }
                            TextButton(onClick = { p.discardAndClose() }) { Text(tr(Res.string.dialog_dont_save), color = ide.error) }
                        }
                    },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Welcome — modelled on the JetBrains "Welcome to IntelliJ IDEA" screen
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Workspace: project panel | tabs + editor | preview
// ─────────────────────────────────────────────────────────────────────────────
