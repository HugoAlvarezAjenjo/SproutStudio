package es.hugoalvarezajenjo.sproutstudio.ui

import es.hugoalvarezajenjo.sproutstudio.editor.foldAll
import es.hugoalvarezajenjo.sproutstudio.editor.changeAtCaret
import es.hugoalvarezajenjo.sproutstudio.editor.rollbackAtCaret
import es.hugoalvarezajenjo.sproutstudio.editor.foldAtCaret
import es.hugoalvarezajenjo.sproutstudio.editor.unfoldAll
import es.hugoalvarezajenjo.sproutstudio.editor.unfoldAtCaret
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.VerticalSplit
import es.hugoalvarezajenjo.sproutstudio.model.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.model.SidebarTool
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import es.hugoalvarezajenjo.sproutstudio.model.AutoSave
import es.hugoalvarezajenjo.sproutstudio.model.AutoSavePrefs
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import es.hugoalvarezajenjo.sproutstudio.editor.CodeEditor
import es.hugoalvarezajenjo.sproutstudio.editor.findNext
import es.hugoalvarezajenjo.sproutstudio.editor.selectedText
import es.hugoalvarezajenjo.sproutstudio.lang.PlantUmlLanguage
import es.hugoalvarezajenjo.sproutstudio.model.AppState
import es.hugoalvarezajenjo.sproutstudio.model.AppWindow
import es.hugoalvarezajenjo.sproutstudio.model.Document
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import es.hugoalvarezajenjo.sproutstudio.model.Recents
import es.hugoalvarezajenjo.sproutstudio.model.TreeNode
import es.hugoalvarezajenjo.sproutstudio.preview.PreviewPane
import es.hugoalvarezajenjo.sproutstudio.preview.rememberLivePreview
import java.awt.Cursor

private fun shortcut(key: Key, shift: Boolean = false) =
    KeyShortcut(key, meta = Dialogs.isMac, ctrl = !Dialogs.isMac, shift = shift)

/** What to do once the user answers the "unsaved changes" question. */
private class PendingClose(val docs: List<Document>, val then: () -> Unit)

@Composable
fun ProjectWindow(win: AppWindow.Project) {
    val p = win.state
    var pending by remember { mutableStateOf<PendingClose?>(null) }

    fun closeDocs(docs: List<Document>, then: () -> Unit) {
        AutoSave.flush(docs) // with autosave on, only untitled diagrams still need the question
        val dirty = docs.filter { it.dirty }
        if (dirty.isEmpty()) then() else pending = PendingClose(dirty, then)
    }

    // Autosave: 1 s after you stop typing, and whenever you switch tab (see below).
    LaunchedEffect(p) { AutoSave.watch({ p.docs.toList() }) }
    LaunchedEffect(p.activeIndex) { AutoSave.flush(p.docs.toList()) }

    DisposableEffect(p) { onDispose { p.git.close() } }

    var paletteOpen by remember { mutableStateOf(false) }
    val gitScope = androidx.compose.runtime.rememberCoroutineScope()
    val doubleShift = remember { DoubleShiftDetector() }

    fun save(doc: Document?) {
        doc ?: return
        if (doc.file == null) Dialogs.saveAs(doc.name, p.root)?.let { doc.save(it); p.refreshTree() } else doc.save()
    }

    val title = buildString {
        p.active?.let { append(if (it.dirty) "● " else ""); append(it.name); append(" — ") }
        append(p.root?.name ?: "SproutStudio")
    }

    Window(
        onCloseRequest = { closeDocs(p.docs.toList()) { AppState.close(win) } },
        title = title,
        icon = AppIcon.painter,
        state = rememberWindowState(size = DpSize(1320.dp, 840.dp)),
        visible = win.revealed,
        onPreviewKeyEvent = { e ->
            val isShift = e.key == Key.ShiftLeft || e.key == Key.ShiftRight
            when {
                doubleShift.onKey(isShift, down = e.type == KeyEventType.KeyDown) -> { paletteOpen = true; true }
                // ⇧⌘A (IntelliJ's Find Action) as a second way in; ⇧⌘P is on the menu item.
                e.type == KeyEventType.KeyDown && e.key == Key.A && e.isShiftPressed &&
                    (if (Dialogs.isMac) e.isMetaPressed else e.isCtrlPressed) -> { paletteOpen = true; true }
                else -> false
            }
        },
    ) {
        // ...and when the window loses focus (you go to another app), like IntelliJ.
        DisposableEffect(window) {
            val l = object : WindowAdapter() {
                override fun windowLostFocus(e: WindowEvent?) = AutoSave.flush(p.docs.toList())
                // Back from the terminal (a commit, a checkout...): re-read git status.
                override fun windowGainedFocus(e: WindowEvent?) = p.git.requestRefresh()
            }
            window.addWindowFocusListener(l)
            onDispose { window.removeWindowFocusListener(l) }
        }
        MenuBar {
            Menu("File") {
                Item("New Diagram", shortcut = shortcut(Key.N)) { p.newDocument() }
                Item("Open Diagram…", shortcut = shortcut(Key.O, shift = true)) { Dialogs.openDiagram()?.let { AppState.openFile(it) } }
                Item("Open Folder…", shortcut = shortcut(Key.O)) { Dialogs.openFolder()?.let { p.openRoot(it) } }
                Item("New Window") { AppState.newProjectWindow() }
                Separator()
                Item("Save", shortcut = shortcut(Key.S), enabled = p.active != null) { save(p.active) }
                CheckboxItem("Save Automatically", checked = AutoSavePrefs.enabled) {
                    AutoSavePrefs.enabled = it
                    if (it) AutoSave.flush(p.docs.toList())
                }
                Item("Save As…", shortcut = shortcut(Key.S, shift = true), enabled = p.active != null) {
                    p.active?.let { d -> Dialogs.saveAs(d.name, d.dir ?: p.root)?.let { d.save(it); p.refreshTree() } }
                }
                Item("Close Tab", shortcut = shortcut(Key.W), enabled = p.active != null) {
                    p.active?.let { d -> closeDocs(listOf(d)) { p.closeDoc(d) } }
                }
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
                        shortcut = KeyShortcut(l.shortcutKey, meta = Dialogs.isMac, ctrl = !Dialogs.isMac, alt = true),
                    ) { p.changeLayout(l) }
                }
                CheckboxItem("Show Preview", checked = p.previewVisible, shortcut = shortcut(Key.P)) { p.togglePreview() }
                Separator()
                CheckboxItem("Show Project Panel", checked = p.sidebarVisible && p.sidebarTool == SidebarTool.PROJECT, shortcut = shortcut(Key.One)) { p.toggleTool(SidebarTool.PROJECT) }
                CheckboxItem("Show Commit Panel", checked = p.sidebarVisible && p.sidebarTool == SidebarTool.COMMIT, shortcut = shortcut(Key.Zero)) { p.toggleTool(SidebarTool.COMMIT) }
                CheckboxItem("Dark Theme", checked = ThemePrefs.dark) { ThemePrefs.toggle() }
                CheckboxItem("Dark Diagram Preview", checked = DiagramPrefs.dark) { DiagramPrefs.toggle() }
                Separator()
                Item("Refresh Files", shortcut = KeyShortcut(Key.Y, meta = Dialogs.isMac, ctrl = !Dialogs.isMac, alt = true)) { p.refreshTree() }
            }
            Menu("Git") {
                val g = p.git
                Item("Commit…", shortcut = shortcut(Key.K), enabled = p.root != null) { p.focusCommit() }
                Item("Compare Diagram with HEAD", enabled = canCompareWithHead(p, p.active?.file)) {
                    p.active?.file?.let { AppState.compareWithHead(p, it) }
                }
                Item(
                    "Rollback Lines",
                    shortcut = KeyShortcut(Key.Z, meta = Dialogs.isMac, ctrl = !Dialogs.isMac, alt = true),
                    enabled = p.active?.changeAtCaret() != null,
                ) { p.revealEditor(); p.active?.rollbackAtCaret() }
                Item("Show Diff with HEAD", enabled = hasCommittedChanges(p, p.active?.file)) {
                    p.active?.file?.let { AppState.compareWithHead(p, it, textFirst = true) }
                }
                Item("Show History for Current File", enabled = g.status.head != null && p.active?.file != null) {
                    p.active?.file?.let { AppState.showHistory(p, it) }
                }
                Separator()
                Item("Undo Last Commit", enabled = (g.lastCommit?.parents ?: 0) > 0) {
                    gitScope.launch {
                        g.undoLastCommit()?.let { u ->
                            if (g.commitMessage.isBlank()) g.commitMessage = u.fullMessage
                            g.lastResult = "Undid ${u.short}: its changes are back in the list"
                            p.showTool(SidebarTool.COMMIT); p.refreshTree()
                        }
                    }
                }
                Item("Stash Changes…", enabled = g.status.head != null) { p.showTool(SidebarTool.COMMIT); g.stashPopupTick++ }
                Separator()
                if (!g.isRepo) Item("Create Git Repository", enabled = p.root != null) { gitScope.launch { g.init() } }
                Item("Refresh Git Status", enabled = g.isRepo) { g.requestRefresh() }
            }
            Menu("Help") {
                Item("Find Action…", shortcut = shortcut(Key.P, shift = true)) { paletteOpen = true }
            }
        }

        PumlTheme {
            Box(Modifier.fillMaxSize()) {
            Surface(color = ide.panel) {
                if (p.root == null && p.docs.isEmpty()) {
                    Welcome(p)
                } else {
                    Workspace(p, onSave = { save(it) }, onCloseTab = { d -> closeDocs(listOf(d)) { p.closeDoc(d) } })
                }
            }

            if (paletteOpen) {
                val commands = projectCommands(
                    p,
                    onSave = { save(p.active) },
                    onSaveAs = { p.active?.let { d -> Dialogs.saveAs(d.name, d.dir ?: p.root)?.let { d.save(it); p.refreshTree() } } },
                    onCloseTab = { p.active?.let { d -> closeDocs(listOf(d)) { p.closeDoc(d) } } },
                )
                CommandPalette(
                    commands,
                    onDismiss = { paletteOpen = false; p.active?.let { it.focusTick++ } },
                    onRun = { cmd ->
                        paletteOpen = false
                        PaletteRecents.add(cmd.id)
                        cmd.run()
                        // Hand the keyboard back to the (possibly new) active editor.
                        if (cmd.restoresFocus) p.active?.let { it.focusTick++ }
                    },
                )
            }
            }

            pending?.let { pc ->
                AlertDialog(
                    onDismissRequest = { pending = null },
                    containerColor = ide.popup,
                    shape = RoundedCornerShape(10.dp),
                    title = { Text("Save changes?", style = MaterialTheme.typography.titleMedium) },
                    text = { Text("${pc.docs.joinToString { it.name }} has unsaved changes.", style = MaterialTheme.typography.bodyMedium) },
                    confirmButton = {
                        TextButton(onClick = { pc.docs.forEach { save(it) }; pending = null; if (pc.docs.none { it.dirty }) pc.then() }) { Text("Save") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { pending = null }) { Text("Cancel") }
                            TextButton(onClick = { pending = null; pc.then() }) { Text("Don't Save", color = ide.error) }
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

@Composable
internal fun Welcome(p: ProjectState) {
    val c = ide
    Box(Modifier.fillMaxSize().background(c.editor), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppMark(44)
                Column {
                    Text("SproutStudio", style = MaterialTheme.typography.headlineMedium, color = c.text)
                    Text("PlantUML, offline. Version 0.1", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton(Icons.Outlined.FolderOpen, label = "Open Folder", primary = true) { Dialogs.openFolder()?.let { p.openRoot(it) } }
                ToolButton(Icons.Outlined.Description, label = "Open File") { Dialogs.openDiagram()?.let { AppState.openFile(it) } }
                ToolButton(Icons.AutoMirrored.Outlined.NoteAdd, label = "New Diagram") { p.newDocument() }
            }
            val recents = remember { Recents.folders() }
            if (recents.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Recent", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Column {
                    recents.forEach { dir ->
                        ListRow(onClick = { p.openRoot(dir) }) {
                            Initials(dir.name)
                            Column(Modifier.weight(1f)) {
                                Text(dir.name, style = MaterialTheme.typography.labelLarge, color = c.text)
                                Text(
                                    dir.parent?.replace(System.getProperty("user.home"), "~") ?: "",
                                    fontSize = 11.sp, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Tip: double-click any .puml file in Finder for an instant preview.", fontSize = 12.sp, color = c.textMuted)
        }
    }
}

/** Gradient-free app mark: rounded square with an accent "S". */
@Composable
private fun AppMark(size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(Color(0xFF2FA36B)),
        contentAlignment = Alignment.Center,
    ) { Text("S", color = Color.White, fontSize = (size * 0.5).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
}

/** JetBrains recent-project tile: two initials on a tinted square. */
@Composable
private fun Initials(name: String) {
    val hues = listOf(0xFF3574F0, 0xFF2FA36B, 0xFFC77DBB, 0xFFCF8E6D, 0xFF2AACB8)
    val tint = Color(hues[(name.hashCode() and 0x7fffffff) % hues.size])
    val letters = name.split('-', '_', ' ', '.').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(tint), contentAlignment = Alignment.Center) {
        Text(letters, color = Color.White, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Workspace: project panel | tabs + editor | preview
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun Workspace(p: ProjectState, onSave: (Document) -> Unit, onCloseTab: (Document) -> Unit) {
    var caretLine by remember { mutableStateOf(1) }
    var caretCol by remember { mutableStateOf(1) }
    var status by remember { mutableStateOf<StatusInfo?>(null) }
    val c = ide

    // Git: find the repository of the folder, and re-read status on demand / after each save.
    LaunchedEffect(p.root) { p.git.open(p.root) }
    LaunchedEffect(p.git.refreshTick) { if (p.git.refreshTick > 0) { kotlinx.coroutines.delay(150); p.git.refresh() } }
    LaunchedEffect(p) { snapshotFlow { p.docs.map { it.savedText } }.collect { p.git.requestRefresh() } }

    Column(Modifier.fillMaxSize().background(c.panel)) {
        Row(Modifier.weight(1f)) {
            if (p.root != null) {
                ToolStripe(p)
                VLine()
                if (p.sidebarVisible) {
                    if (p.sidebarTool == SidebarTool.COMMIT) CommitPanel(p) else Sidebar(p)
                    SidebarSplitter(p)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().background(c.editor)) {
                TabsBar(p, onCloseTab)
                val doc = p.active
                if (doc == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(Icons.Outlined.Schema, "No file open", "Pick a diagram on the left, or press ⌘N")
                    }
                } else {
                    key(doc) {
                        val preview = rememberLivePreview(doc.text, doc.dir)
                        EditorAndPreview(p, doc, preview, onCaret = { l, col -> caretLine = l; caretCol = col })
                        val info = StatusInfo(
                            kind = PlantUmlLanguage.kindAt(doc.text, doc.value.selection.start).label,
                            diagrams = preview.diagramCount,
                            errorLine = preview.error?.let { it.line ?: 0 },
                        )
                        SideEffect { status = info }
                    }
                }
            }
        }
        HLine()
        StatusBar(p, caretLine, caretCol, status)
    }
}

/** What the active editor knows, for the status bar. */
private data class StatusInfo(val kind: String, val diagrams: Int, val errorLine: Int?)

@Composable
private fun EditorAndPreview(p: ProjectState, doc: Document, preview: es.hugoalvarezajenjo.sproutstudio.preview.PreviewState, onCaret: (Int, Int) -> Unit) {
    val c = ide
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val total = maxWidth
        val density = LocalDensity.current
        val split = p.layout == EditorLayout.SPLIT
        Row(Modifier.fillMaxSize()) {
            if (p.editorVisible) {
                CodeEditor(
                    doc,
                    errorLine = preview.error?.line,
                    lineChanges = rememberLineChanges(p.git, doc.file, doc.text),
                    modifier = Modifier.weight(if (split) 1f - p.previewFraction else 1f).fillMaxHeight(),
                    onCaretMoved = onCaret,
                )
            }
            if (split) {
                Splitter(onDrag = { deltaPx ->
                    val deltaFrac = with(density) { deltaPx.toDp() } / total
                    p.previewFraction = (p.previewFraction - deltaFrac).coerceIn(0.2f, 0.8f)
                })
            }
            if (p.previewVisible) {
                PreviewPane(
                    preview = preview,
                    text = doc.text,
                    baseDir = doc.dir,
                    baseName = doc.baseName,
                    modifier = Modifier.weight(if (split) p.previewFraction else 1f).fillMaxHeight(),
                    // An error link needs the code: from preview-only, open the editor beside it.
                    onJumpToLine = { p.revealEditor(); doc.jumpRequest = it },
                    request = doc.previewRequest,
                    onRequestHandled = { doc.previewRequest = null },
                    leadingTools = {
                        Text("Preview", style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.padding(start = 6.dp, end = 4.dp))
                    },
                )
            }
        }
    }
}

/**
 * JetBrains-style tool window stripe: always visible on the left, its button toggles the
 * Project panel, so a collapsed panel is one click (or ⌘1) away.
 */
@Composable
private fun ToolStripe(p: ProjectState) {
    Column(
        Modifier.width(40.dp).fillMaxHeight().background(ide.panel).padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val project = p.sidebarVisible && p.sidebarTool == SidebarTool.PROJECT
        val commit = p.sidebarVisible && p.sidebarTool == SidebarTool.COMMIT
        ToolButton(
            Icons.Outlined.Folder,
            if (project) "Hide Project (${shortcutHint("1")})" else "Show Project (${shortcutHint("1")})",
            selected = project,
        ) { p.toggleTool(SidebarTool.PROJECT) }
        ToolButton(
            Icons.Outlined.Commit,
            if (commit) "Hide Commit (${shortcutHint("0")})" else "Commit (${shortcutHint("0")})",
            selected = commit,
        ) { p.toggleTool(SidebarTool.COMMIT) }
    }
}

/** Drag to resize the Project panel, double-click to collapse it. */
@Composable
private fun SidebarSplitter(p: ProjectState) {
    val density = LocalDensity.current
    Splitter(
        onDrag = { deltaPx -> p.resizeSidebar(p.sidebarWidth + with(density) { deltaPx.toDp() }.value) },
        onDragStopped = { p.persistSidebarWidth() },
        onDoubleClick = { p.showSidebar(false) },
    )
}

@Composable
private fun Sidebar(p: ProjectState) {
    val c = ide
    Column(Modifier.width(p.sidebarWidth.dp).fillMaxHeight().background(c.panel)) {
        // Tool window header: title + actions, 36dp like IntelliJ.
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Project", style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
            ToolButton(Icons.AutoMirrored.Outlined.NoteAdd, "New diagram (⌘N)") { p.newDocument() }
            ToolButton(Icons.Outlined.Refresh, "Refresh (⌥⌘Y)") { p.refreshTree() }
            ToolButton(Icons.Outlined.Remove, "Hide (⌘1)") { p.showSidebar(false) }
        }
        // Root row.
        Row(
            Modifier.fillMaxWidth().height(26.dp).padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.ExpandMore, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
            Icon(Icons.Outlined.Folder, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
            Text(p.root?.name ?: "", style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                p.root?.parent?.replace(System.getProperty("user.home"), "~") ?: "",
                fontSize = 12.sp, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (p.tree.isEmpty()) {
            Text("No diagrams in this folder", fontSize = 12.sp, color = c.textMuted, modifier = Modifier.padding(start = 34.dp, top = 6.dp))
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal = 4.dp)) {
            items(p.tree, key = { it.file.absolutePath }) { node -> TreeRow(p, node) }
        }
    }
}

@Composable
private fun TreeRow(p: ProjectState, node: TreeNode) {
    val git = p.git
    if (node.isDir || git.status.head == null) return TreeRowContent(p, node)
    androidx.compose.foundation.ContextMenuArea(items = {
        buildList {
            add(androidx.compose.foundation.ContextMenuItem("Open") { p.open(node.file) })
            if (canCompareWithHead(p, node.file)) add(androidx.compose.foundation.ContextMenuItem("Compare Diagram with HEAD") { AppState.compareWithHead(p, node.file) })
            if (hasCommittedChanges(p, node.file)) add(androidx.compose.foundation.ContextMenuItem("Show Diff with HEAD") { AppState.compareWithHead(p, node.file, textFirst = true) })
            add(androidx.compose.foundation.ContextMenuItem("Show History") { AppState.showHistory(p, node.file) })
        }
    }) { TreeRowContent(p, node) }
}

@Composable
private fun TreeRowContent(p: ProjectState, node: TreeNode) {
    val c = ide
    val isActive = p.active?.file?.absoluteFile == node.file.absoluteFile
    val open = node.isDir && node.file in p.expanded
    ListRow(
        selected = isActive,
        onClick = { if (node.isDir) p.toggleDir(node.file) else p.open(node.file) },
        height = 26,
        startPadding = 6 + (node.depth + 1) * 16,
    ) {
        if (node.isDir) {
            Icon(if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
            Icon(if (open) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
        } else {
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Outlined.Schema, null, tint = c.syntax.arrow, modifier = Modifier.size(16.dp))
        }
        val git = p.git
        val vcs = when {
            node.isDir -> if (git.dirChanged(node.file)) c.vcsModified else if (git.isIgnored(node.file)) c.vcsIgnored else null
            else -> c.vcsColor(git.changeOf(node.file)?.type) ?: if (git.isIgnored(node.file)) c.vcsIgnored else null
        }
        Text(
            node.file.name,
            style = MaterialTheme.typography.bodyMedium,
            color = vcs ?: c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!node.isDir && p.docs.any { it.file?.absoluteFile == node.file.absoluteFile && it.dirty }) Dot(c.accent, 6)
    }
}

@Composable
private fun TabsBar(p: ProjectState, onCloseTab: (Document) -> Unit) {
    if (p.docs.isEmpty()) return
    val c = ide
    Column {
        Row(Modifier.fillMaxWidth().height(36.dp).background(c.panel), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState())) {
            p.docs.forEachIndexed { i, d ->
                val active = i == p.activeIndex
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Box(
                    Modifier
                        .fillMaxHeight()
                        .background(if (active) c.editor else if (hovered) c.hover else Color.Transparent)
                        .hoverable(interaction)
                        .clickable { p.activeIndex = i },
                ) {
                    Row(
                        Modifier.fillMaxHeight().padding(start = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Schema, null, tint = c.syntax.arrow, modifier = Modifier.size(15.dp))
                        Text(d.name, style = MaterialTheme.typography.bodyMedium, color = if (active) c.text else c.textMuted)
                        Box(
                            Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).clickable { onCloseTab(d) },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                d.dirty && !hovered -> Dot(c.accent, 6)
                                hovered || active -> Icon(Icons.Outlined.Close, "Close", tint = c.textMuted, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                    // Active-tab indicator, like the New UI.
                    if (active) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(c.accent))
                }
                VLine()
            }
        }
            LayoutSwitcher(p)
        }
        HLine()
    }
}

/** IntelliJ's top-right editor switcher: code only, code + preview, preview only. */
@Composable
private fun LayoutSwitcher(p: ProjectState) {
    Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        LAYOUT_BUTTONS.forEach { (l, icon) ->
            ToolButton(icon, "${l.label} (${l.shortcutHint})", selected = p.layout == l) { p.changeLayout(l) }
        }
    }
}

private val LAYOUT_BUTTONS = listOf(
    EditorLayout.EDITOR to Icons.Outlined.Code,
    EditorLayout.SPLIT to Icons.Outlined.VerticalSplit,
    EditorLayout.PREVIEW to Icons.Outlined.Image,
)

private val EditorLayout.shortcutKey: Key
    get() = when (this) { EditorLayout.EDITOR -> Key.One; EditorLayout.SPLIT -> Key.Two; EditorLayout.PREVIEW -> Key.Three }

private val EditorLayout.shortcutHint: String
    get() = (if (Dialogs.isMac) "⌥⌘" else "Ctrl+Alt+") + (ordinal + 1)

@Composable
private fun StatusBar(p: ProjectState, line: Int, col: Int, info: StatusInfo?) {
    val c = ide
    Row(
        Modifier.fillMaxWidth().height(26.dp).background(c.panel).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Breadcrumb: folder › file
        val doc = p.active
        StatusText(listOfNotNull(p.root?.name, doc?.name).joinToString("  ›  "))
        Spacer(Modifier.weight(1f))
        if (doc != null) {
            info?.let {
                when (it.errorLine) {
                    null -> StatusText("No problems", c.textMuted, Icons.Outlined.CheckCircle)
                    0 -> StatusText("Syntax error", c.error, Icons.Outlined.ErrorOutline)
                    else -> StatusText("Error on line ${it.errorLine}", c.error, Icons.Outlined.ErrorOutline)
                }
                StatusText(if (it.diagrams > 1) "${it.kind} · ${it.diagrams} diagrams" else it.kind)
            }
            StatusText("$line:$col")
            when {
                doc.saveError != null -> StatusText("Save failed: ${doc.saveError}", c.error, Icons.Outlined.ErrorOutline)
                doc.dirty -> StatusText(if (doc.file == null) "Not saved yet" else "Modified")
                else -> StatusText(if (AutoSavePrefs.enabled) "Saved automatically" else "Saved")
            }
        }
        BranchButton(p)
        StatusText("Offline", icon = Icons.Outlined.Lock)
    }
}

/** IntelliJ list/tree row: fixed height, 4dp-rounded selection, hover tint. */
@Composable
private fun ListRow(
    selected: Boolean = false,
    onClick: () -> Unit,
    height: Int = 44,
    startPadding: Int = 8,
    content: @Composable RowScope.() -> Unit,
) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    selected -> c.selection
                    hovered -> c.hover
                    else -> Color.Transparent
                },
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(start = startPadding.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}
