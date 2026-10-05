package es.hugoalvarezajenjo.sproutstudio.features.workspace

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

private fun shortcut(key: Key, shift: Boolean = false) =
    KeyShortcut(key, meta = Os.isMac, ctrl = !Os.isMac, shift = shift)

@Composable
fun ProjectWindow(p: WorkspaceViewModel, revealed: Boolean, onClose: () -> Unit) {
    // Autosave: 1 s after you stop typing, and whenever you switch tab. Runs in p's own scope.
    LaunchedEffect(p) { p.startAutoSave() }

    DisposableEffect(p) { onDispose { p.close() } }

    var paletteOpen by remember { mutableStateOf(false) }
    var galleryOpen by remember { mutableStateOf(false) }
    fun pickTemplate(t: es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Template) {
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
            Menu("File") {
                Item("New Diagram", shortcut = shortcut(Key.N)) { p.newDocument() }
                Item("New from Template…", shortcut = shortcut(Key.N, shift = true)) { galleryOpen = true }
                Item("Open Diagram…", shortcut = shortcut(Key.O, shift = true)) { p.openDiagramDialog() }
                Item("Open Folder…", shortcut = shortcut(Key.O)) { p.chooseFolder()?.let { p.openRoot(it) } }
                Item("New Window") { p.deps.windows.newProjectWindow() }
                Separator()
                Item("Save", shortcut = shortcut(Key.S), enabled = p.active != null) { p.save(p.active) }
                CheckboxItem("Save Automatically", checked = p.deps.autoSaver.enabled) { p.setAutoSave(it) }
                Item("Save As…", shortcut = shortcut(Key.S, shift = true), enabled = p.active != null) {
                    p.active?.let { p.saveAs(it) }
                }
                Item("Close Tab", shortcut = shortcut(Key.W), enabled = p.active != null) {
                    p.active?.let { p.requestCloseTab(it) }
                }
                Separator()
                Item("Close Project", enabled = p.root != null || p.docs.isNotEmpty()) { p.requestCloseProject() }
            }
            Menu("Edit") {
                val d = p.active
                Item("Find…", shortcut = shortcut(Key.F), enabled = d != null) { d?.let { p.revealEditor(); it.find.open(replace = false, seed = it.selectedText()) } }
                Item("Replace…", shortcut = shortcut(Key.R), enabled = d != null) { d?.let { p.revealEditor(); it.find.open(replace = true, seed = it.selectedText()) } }
                Item("Find Next", shortcut = shortcut(Key.G), enabled = d != null) { p.revealEditor(); d?.findNext(true) }
                Item("Find Previous", shortcut = shortcut(Key.G, shift = true), enabled = d != null) { p.revealEditor(); d?.findNext(false) }
            }
            Menu("Code") {
                val d = p.active
                Item("Fold Block", shortcut = shortcut(Key.Minus), enabled = d != null) { p.revealEditor(); d?.foldAtCaret() }
                Item("Unfold Block", shortcut = shortcut(Key.Equals), enabled = d != null) { p.revealEditor(); d?.unfoldAtCaret() }
                Item("Fold All", shortcut = shortcut(Key.Minus, shift = true), enabled = d != null) { p.revealEditor(); d?.foldAll() }
                Item("Unfold All", shortcut = shortcut(Key.Equals, shift = true), enabled = d != null) { p.revealEditor(); d?.unfoldAll() }
            }
            Menu("View") {
                EditorLayout.entries.forEach { l ->
                    RadioButtonItem(
                        l.label,
                        selected = p.layout == l,
                        shortcut = KeyShortcut(l.shortcutKey, meta = Os.isMac, ctrl = !Os.isMac, alt = true),
                    ) { p.changeLayout(l) }
                }
                CheckboxItem("Show Preview", checked = p.previewVisible, shortcut = shortcut(Key.P)) { p.togglePreview() }
                Separator()
                CheckboxItem("Show Project Panel", checked = p.sidebarVisible && p.sidebarTool == SidebarTool.PROJECT, shortcut = shortcut(Key.One)) { p.toggleTool(SidebarTool.PROJECT) }
                CheckboxItem("Show Commit Panel", checked = p.sidebarVisible && p.sidebarTool == SidebarTool.COMMIT, shortcut = shortcut(Key.Zero)) { p.toggleTool(SidebarTool.COMMIT) }
                CheckboxItem("Show Templates Panel", checked = p.sidebarVisible && p.sidebarTool == SidebarTool.TEMPLATES) { p.toggleTool(SidebarTool.TEMPLATES) }
                CheckboxItem("Show Problems", checked = p.problemsExpanded, shortcut = shortcut(Key.Six)) { p.toggleProblems() }
                CheckboxItem("Dark Theme", checked = p.deps.theme.dark.value) { p.deps.theme.toggle() }
                CheckboxItem("Dark Diagram Preview", checked = p.deps.diagram.dark.value) { p.deps.diagram.toggle() }
                Separator()
                Item("Refresh Files", shortcut = KeyShortcut(Key.Y, meta = Os.isMac, ctrl = !Os.isMac, alt = true)) { p.refreshTree() }
            }
            Menu("Git") {
                val g = p.git
                Item("Commit…", shortcut = shortcut(Key.K), enabled = p.root != null) { p.focusCommit() }
                Item("Compare Diagram with HEAD", enabled = canCompareWithHead(p, p.active?.file)) {
                    p.active?.file?.let { p.deps.windows.compareWithHead(p, it) }
                }
                Item(
                    "Rollback Lines",
                    shortcut = KeyShortcut(Key.Z, meta = Os.isMac, ctrl = !Os.isMac, alt = true),
                    enabled = p.active?.changeAtCaret() != null,
                ) { p.revealEditor(); p.active?.rollbackAtCaret() }
                Item("Show Diff with HEAD", enabled = hasCommittedChanges(p, p.active?.file)) {
                    p.active?.file?.let { p.deps.windows.compareWithHead(p, it, textFirst = true) }
                }
                Item("Show History for Current File", enabled = g.status.head != null && p.active?.file != null) {
                    p.active?.file?.let { p.deps.windows.showHistory(p, it) }
                }
                Separator()
                Item("Undo Last Commit", enabled = (g.lastCommit?.parents ?: 0) > 0) {
                    p.gitFlow.undoLastCommit(showCommitPanel = true)
                }
                Item("Stash Changes…", enabled = g.status.head != null) { p.showTool(SidebarTool.COMMIT); g.requestStashPopup() }
                Separator()
                if (!g.isRepo) Item("Create Git Repository", enabled = p.root != null) { p.gitFlow.init() }
                Item("Refresh Git Status", enabled = g.isRepo) { g.requestRefresh() }
            }
            Menu("Help") {
                Item("Find Action…", shortcut = shortcut(Key.P, shift = true)) { paletteOpen = true }
            }
        }

        PumlTheme(dark = p.deps.theme.dark.value) {
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
                    title = { Text("Save changes?", style = MaterialTheme.typography.titleMedium) },
                    text = { Text("${pc.docs.joinToString { it.name }} has unsaved changes.", style = MaterialTheme.typography.bodyMedium) },
                    confirmButton = {
                        TextButton(onClick = { p.saveAndClose() }) { Text("Save") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { p.cancelClose() }) { Text("Cancel") }
                            TextButton(onClick = { p.discardAndClose() }) { Text("Don't Save", color = ide.error) }
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
