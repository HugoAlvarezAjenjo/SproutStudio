package es.hugoalvarezajenjo.sproutstudio.ui

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
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
        val dirty = docs.filter { it.dirty }
        if (dirty.isEmpty()) then() else pending = PendingClose(dirty, then)
    }

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
        state = rememberWindowState(size = DpSize(1320.dp, 840.dp)),
        visible = win.revealed,
    ) {
        MenuBar {
            Menu("File") {
                Item("New Diagram", shortcut = shortcut(Key.N)) { p.newDocument() }
                Item("Open Diagram…", shortcut = shortcut(Key.O, shift = true)) { Dialogs.openDiagram()?.let { AppState.openFile(it) } }
                Item("Open Folder…", shortcut = shortcut(Key.O)) { Dialogs.openFolder()?.let { p.openRoot(it) } }
                Item("New Window") { AppState.newProjectWindow() }
                Separator()
                Item("Save", shortcut = shortcut(Key.S), enabled = p.active != null) { save(p.active) }
                Item("Save As…", shortcut = shortcut(Key.S, shift = true), enabled = p.active != null) {
                    p.active?.let { d -> Dialogs.saveAs(d.name, d.dir ?: p.root)?.let { d.save(it); p.refreshTree() } }
                }
                Item("Close Tab", shortcut = shortcut(Key.W), enabled = p.active != null) {
                    p.active?.let { d -> closeDocs(listOf(d)) { p.closeDoc(d) } }
                }
            }
            Menu("View") {
                CheckboxItem("Show Preview", checked = p.previewVisible, shortcut = shortcut(Key.P)) { p.previewVisible = it }
                CheckboxItem("Show Project Panel", checked = p.sidebarVisible, shortcut = shortcut(Key.One)) { p.showSidebar(it) }
                CheckboxItem("Dark Theme", checked = ThemePrefs.dark) { ThemePrefs.toggle() }
                Separator()
                Item("Refresh Files", shortcut = shortcut(Key.R)) { p.refreshTree() }
            }
        }

        PumlTheme {
            Surface(color = ide.panel) {
                if (p.root == null && p.docs.isEmpty()) {
                    Welcome(p)
                } else {
                    Workspace(p, onSave = { save(it) }, onCloseTab = { d -> closeDocs(listOf(d)) { p.closeDoc(d) } })
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

    Column(Modifier.fillMaxSize().background(c.panel)) {
        Row(Modifier.weight(1f)) {
            if (p.root != null) {
                ToolStripe(p)
                VLine()
                if (p.sidebarVisible) {
                    Sidebar(p)
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
        Row(Modifier.fillMaxSize()) {
            CodeEditor(
                doc,
                errorLine = preview.error?.line,
                modifier = Modifier.weight(if (p.previewVisible) 1f - p.previewFraction else 1f).fillMaxHeight(),
                onCaretMoved = onCaret,
            )
            if (p.previewVisible) {
                Splitter(onDrag = { deltaPx ->
                    val deltaFrac = with(density) { deltaPx.toDp() } / total
                    p.previewFraction = (p.previewFraction - deltaFrac).coerceIn(0.2f, 0.8f)
                })
                PreviewPane(
                    preview = preview,
                    text = doc.text,
                    baseDir = doc.dir,
                    baseName = doc.baseName,
                    modifier = Modifier.weight(p.previewFraction).fillMaxHeight(),
                    onJumpToLine = { doc.jumpRequest = it },
                    leadingTools = {
                        Text("Preview", style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.padding(start = 6.dp, end = 4.dp))
                        ToolButton(Icons.Outlined.VisibilityOff, "Hide preview (⌘P)") { p.previewVisible = false }
                    },
                )
            }
        }
        if (!p.previewVisible) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 42.dp, end = 10.dp)) {
                ToolButton(Icons.Outlined.Visibility, "Show preview (⌘P)", label = "Preview") { p.previewVisible = true }
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
        ToolButton(
            Icons.Outlined.Folder,
            if (p.sidebarVisible) "Hide Project (⌘1)" else "Show Project (⌘1)",
            selected = p.sidebarVisible,
        ) { p.showSidebar(!p.sidebarVisible) }
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
            ToolButton(Icons.Outlined.Refresh, "Refresh (⌘R)") { p.refreshTree() }
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
        Text(
            node.file.name,
            style = MaterialTheme.typography.bodyMedium,
            color = c.text,
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
        Row(Modifier.fillMaxWidth().height(36.dp).background(c.panel).horizontalScroll(rememberScrollState())) {
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
        HLine()
    }
}

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
            StatusText(if (doc.dirty) "Modified" else "Saved")
        }
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
