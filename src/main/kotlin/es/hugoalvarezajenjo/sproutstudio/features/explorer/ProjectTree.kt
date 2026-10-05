package es.hugoalvarezajenjo.sproutstudio.features.explorer

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction
import es.hugoalvarezajenjo.sproutstudio.features.explorer.FileActionDialog
import es.hugoalvarezajenjo.sproutstudio.features.explorer.TreeDrag
import es.hugoalvarezajenjo.sproutstudio.features.explorer.canMove
import es.hugoalvarezajenjo.sproutstudio.features.explorer.describe
import es.hugoalvarezajenjo.sproutstudio.features.explorer.fileError
import es.hugoalvarezajenjo.sproutstudio.features.explorer.move
import es.hugoalvarezajenjo.sproutstudio.features.explorer.treeDrag
import es.hugoalvarezajenjo.sproutstudio.features.git.canCompareWithHead
import es.hugoalvarezajenjo.sproutstudio.features.git.hasCommittedChanges
import es.hugoalvarezajenjo.sproutstudio.features.git.vcsColor
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.services.files.TreeNode
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Dot
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ListRow
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import kotlinx.coroutines.launch

@Composable
internal fun ProjectTree(p: WorkspaceViewModel) {
    val c = ide
    val root = p.root ?: return
    val drag = remember(p) { TreeDrag { what, where -> p.canMove(what, where) } }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(4000); message = null } }
    val onDrop: (java.io.File, java.io.File) -> Unit = { what, where ->
        p.scope.launch { p.move(what, where).exceptionOrNull()?.let { message = it.fileError.describe() } }
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    // zIndex: the drag label may run past the panel, over the editor.
    Box(Modifier.width(p.sidebarWidth.dp).fillMaxHeight().zIndex(if (drag.source != null) 2f else 0f).onGloballyPositioned { origin = it.positionInWindow() }) {
    Column(Modifier.fillMaxSize().background(c.panel).testTag("project-tree")) {
        // Tool window header: title + actions, 36dp like IntelliJ.
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Project", style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
            ToolButton(Icons.AutoMirrored.Outlined.NoteAdd, "New Diagram…") { targetDir(p)?.let { p.showFileAction(FileAction.NewDiagram(it)) } }
            ToolButton(Icons.Outlined.CreateNewFolder, "New Folder…") { targetDir(p)?.let { p.showFileAction(FileAction.NewFolder(it)) } }
            ToolButton(Icons.Outlined.UnfoldLess, "Collapse All") { p.collapseAll() }
            ToolButton(Icons.Outlined.Refresh, "Refresh (⌥⌘Y)") { p.refreshTree() }
            ToolButton(Icons.Outlined.Remove, "Hide (⌘1)") { p.showSidebar(false) }
        }
        // Root row: a folder like any other, so it collapses too.
        ContextMenuArea(items = { folderMenu(p, root, isRoot = true) }) {
            ListRow(
                onClick = { p.toggleRootCollapsed() }, height = 26, startPadding = 6,
                modifier = Modifier.treeDrag(drag, "root", root, file = null, onDrop).dropHighlight(drag.target == root.absoluteFile),
            ) {
                Icon(if (p.rootCollapsed) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
                Icon(if (p.rootCollapsed) Icons.Outlined.Folder else Icons.Outlined.FolderOpen, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                Text(root.name, style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("tree-root"))
                Text(
                    root.parent?.replace(System.getProperty("user.home"), "~") ?: "",
                    fontSize = 12.sp, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!p.rootCollapsed) {
            if (p.tree.isEmpty()) {
                Text("No diagrams in this folder", fontSize = 12.sp, color = c.textMuted, modifier = Modifier.padding(start = 34.dp, top = 6.dp))
            }
            LazyColumn(Modifier.weight(1f, fill = false).padding(horizontal = 4.dp)) {
                items(p.tree, key = { it.file.absolutePath }) { node -> TreeRow(p, node, drag, onDrop) }
            }
        }
        // The empty space below the tree: right-click creates in the project folder.
        ContextMenuArea(items = { folderMenu(p, root, isRoot = true) }) {
            Box(Modifier.weight(1f).fillMaxWidth().treeDrag(drag, "empty", root, file = null, onDrop))
        }
        message?.let {
            Text(it, color = c.error, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().background(c.errorBg).padding(horizontal = 12.dp, vertical = 6.dp).testTag("tree-message"))
        }
    }
    // What's being dragged, following the pointer.
    drag.source?.let { src ->
        val d = LocalDensity.current
        val at = drag.pointer - origin
        Row(
            Modifier.offset { IntOffset(at.x.toInt() + with(d) { 14.dp.roundToPx() }, at.y.toInt() + with(d) { 6.dp.roundToPx() }) }
                .clip(RoundedCornerShape(6.dp)).background(c.popup).border(1.dp, if (drag.target != null) c.accent else c.popupBorder, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(if (src.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Schema, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
            Text(drag.target?.let { "${src.name} → ${if (it == root.absoluteFile) root.name else it.name}" } ?: src.name, color = c.text, fontSize = 12.sp)
        }
    }
    }
    FileActionDialog(p)
}

/** Accent outline on the folder row a drag would drop into. */
private fun Modifier.dropHighlight(on: Boolean) = composed {
    if (on) border(1.5.dp, ide.accent, RoundedCornerShape(4.dp)).background(ide.accent.copy(alpha = 0.12f), RoundedCornerShape(4.dp)) else this
}

/** Where the header's New buttons create: the active file's folder inside the project, else the root. */
private fun targetDir(p: WorkspaceViewModel): java.io.File? {
    val root = p.root?.absoluteFile ?: return null
    val dir = p.active?.file?.absoluteFile?.parentFile
    return if (dir != null && (dir == root || dir.startsWith(root))) dir else root
}

private fun folderMenu(p: WorkspaceViewModel, dir: java.io.File, isRoot: Boolean) = buildList {
    add(ContextMenuItem("New Diagram…") { p.showFileAction(FileAction.NewDiagram(dir)) })
    add(ContextMenuItem("New Folder…") { p.showFileAction(FileAction.NewFolder(dir)) })
    if (!isRoot) {
        add(ContextMenuItem("Rename…") { p.showFileAction(FileAction.Rename(dir)) })
        add(ContextMenuItem("Move to Trash…") { p.showFileAction(FileAction.Delete(dir)) })
    } else {
        add(ContextMenuItem(if (p.rootCollapsed) "Expand" else "Collapse") { p.toggleRootCollapsed() })
    }
    add(ContextMenuItem(if (Os.isMac) "Reveal in Finder" else "Open Containing Folder") { p.deps.platform.reveal(dir) })
}

@Composable
private fun TreeRow(p: WorkspaceViewModel, node: TreeNode, drag: TreeDrag, onDrop: (java.io.File, java.io.File) -> Unit) {
    val f = node.file
    DisposableEffect(f) { onDispose { drag.forget(f.absolutePath) } }
    ContextMenuArea(items = {
        if (node.isDir) folderMenu(p, f, isRoot = false) else buildList {
            val dir = f.absoluteFile.parentFile
            add(ContextMenuItem("Open") { p.open(f) })
            add(ContextMenuItem("New Diagram…") { p.showFileAction(FileAction.NewDiagram(dir)) })
            add(ContextMenuItem("New Folder…") { p.showFileAction(FileAction.NewFolder(dir)) })
            add(ContextMenuItem("Rename…") { p.showFileAction(FileAction.Rename(f)) })
            add(ContextMenuItem("Move to Trash…") { p.showFileAction(FileAction.Delete(f)) })
            add(ContextMenuItem(if (Os.isMac) "Reveal in Finder" else "Open Containing Folder") { p.deps.platform.reveal(f) })
            if (p.git.status.head != null) {
                if (canCompareWithHead(p, f)) add(ContextMenuItem("Compare Diagram with HEAD") { p.deps.windows.compareWithHead(p, f) })
                if (hasCommittedChanges(p, f)) add(ContextMenuItem("Show Diff with HEAD") { p.deps.windows.compareWithHead(p, f, textFirst = true) })
                add(ContextMenuItem("Show History") { p.deps.windows.showHistory(p, f) })
            }
        }
    }) { TreeRowContent(p, node, drag, onDrop) }
}

@Composable
private fun TreeRowContent(p: WorkspaceViewModel, node: TreeNode, drag: TreeDrag, onDrop: (java.io.File, java.io.File) -> Unit) {
    val c = ide
    val isActive = p.active?.file?.absoluteFile == node.file.absoluteFile
    val open = node.isDir && node.file in p.expanded
    ListRow(
        selected = isActive,
        onClick = { if (node.isDir) p.toggleDir(node.file) else p.open(node.file) },
        height = 26,
        startPadding = 6 + (node.depth + 1) * 16,
        modifier = Modifier.testTag("tree-row-${node.file.name}")
            .treeDrag(drag, node.file.absolutePath, folder = if (node.isDir) node.file else node.file.absoluteFile.parentFile, file = node.file, onDrop)
            .dropHighlight(node.isDir && drag.target == node.file.absoluteFile)
            .then(if (drag.source?.absoluteFile == node.file.absoluteFile) Modifier.alpha(0.45f) else Modifier),
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
