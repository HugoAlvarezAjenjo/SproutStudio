package es.hugoalvarezajenjo.sproutstudio.features.explorer

import es.hugoalvarezajenjo.sproutstudio.core.os.shortcutHint
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_new_folder
import es.hugoalvarezajenjo.sproutstudio.resources.tree_collapse_all
import es.hugoalvarezajenjo.sproutstudio.resources.tree_hide
import es.hugoalvarezajenjo.sproutstudio.resources.tree_new_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.tree_no_diagrams
import es.hugoalvarezajenjo.sproutstudio.resources.tree_project
import es.hugoalvarezajenjo.sproutstudio.resources.tree_refresh
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

import androidx.compose.foundation.ContextMenuArea
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
import es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction
import es.hugoalvarezajenjo.sproutstudio.features.explorer.FileActionDialog
import es.hugoalvarezajenjo.sproutstudio.features.explorer.TreeDrag
import es.hugoalvarezajenjo.sproutstudio.features.explorer.describe
import es.hugoalvarezajenjo.sproutstudio.features.explorer.fileError
import es.hugoalvarezajenjo.sproutstudio.features.explorer.treeDrag
import es.hugoalvarezajenjo.sproutstudio.features.git.RollbackDialog
import es.hugoalvarezajenjo.sproutstudio.features.git.vcsColor
import es.hugoalvarezajenjo.sproutstudio.services.git.FileChange
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
    val drag = remember(p) { TreeDrag { what, where -> p.explorer.canMove(what, where) } }
    var message by remember { mutableStateOf<TreeMessage?>(null) }
    LaunchedEffect(message) { if (message != null) { kotlinx.coroutines.delay(4000); message = null } }
    val onDrop: (java.io.File, java.io.File) -> Unit = { what, where ->
        p.scope.launch { p.explorer.move(what, where).exceptionOrNull()?.let { message = TreeMessage(it.fileError.describe()) } }
    }
    var rollback by remember { mutableStateOf<FileChange?>(null) }
    val menus = remember(p) { TreeMenus(p, onRollback = { rollback = it }, onMessage = { message = it }) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    // zIndex: the drag label may run past the panel, over the editor.
    Box(Modifier.width(p.sidebarWidth.dp).fillMaxHeight().zIndex(if (drag.source != null) 2f else 0f).onGloballyPositioned { origin = it.positionInWindow() }) {
    Column(Modifier.fillMaxSize().background(c.panel).testTag("project-tree")) {
        // Tool window header: title + actions, 36dp like IntelliJ.
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr(Res.string.tree_project), style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
            ToolButton(Icons.AutoMirrored.Outlined.NoteAdd, tr(Res.string.tree_new_diagram)) { targetDir(p)?.let { p.explorer.showFileAction(FileAction.NewDiagram(it)) } }
            ToolButton(Icons.Outlined.CreateNewFolder, tr(Res.string.cmd_new_folder)) { targetDir(p)?.let { p.explorer.showFileAction(FileAction.NewFolder(it)) } }
            ToolButton(Icons.Outlined.UnfoldLess, tr(Res.string.tree_collapse_all)) { p.explorer.collapseAll() }
            ToolButton(Icons.Outlined.Refresh, tr(Res.string.tree_refresh, shortcutHint("Y", alt = true))) { p.explorer.refreshTree() }
            ToolButton(Icons.Outlined.Remove, tr(Res.string.tree_hide, shortcutHint("1"))) { p.showSidebar(false) }
        }
        // Root row: a folder like any other, so it collapses too.
        ContextMenuArea(items = { menus.forFolder(root, isRoot = true) }) {
            ListRow(
                onClick = { p.explorer.toggleRootCollapsed() }, height = 26, startPadding = 6,
                modifier = Modifier.treeDrag(drag, "root", root, file = null, onDrop).dropHighlight(drag.target == root.absoluteFile),
            ) {
                Icon(if (p.explorer.rootCollapsed) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
                Icon(if (p.explorer.rootCollapsed) Icons.Outlined.Folder else Icons.Outlined.FolderOpen, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                Text(root.name, style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("tree-root"))
                Text(
                    root.parent?.replace(System.getProperty("user.home"), "~") ?: "",
                    fontSize = 12.sp, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!p.explorer.rootCollapsed) {
            if (p.explorer.tree.isEmpty()) {
                Text(tr(Res.string.tree_no_diagrams), fontSize = 12.sp, color = c.textMuted, modifier = Modifier.padding(start = 34.dp, top = 6.dp))
            }
            LazyColumn(Modifier.weight(1f, fill = false).padding(horizontal = 4.dp)) {
                items(p.explorer.tree, key = { it.file.absolutePath }) { node -> TreeRow(p, node, menus, drag, onDrop) }
            }
        }
        // The empty space below the tree: right-click creates in the project folder.
        ContextMenuArea(items = { menus.forFolder(root, isRoot = true) }) {
            Box(Modifier.weight(1f).fillMaxWidth().treeDrag(drag, "empty", root, file = null, onDrop))
        }
        message?.let {
            Text(
                it.text, color = if (it.error) c.error else c.text, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().background(if (it.error) c.errorBg else c.hover).padding(horizontal = 12.dp, vertical = 6.dp).testTag("tree-message"),
            )
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
    rollback?.let { ch -> RollbackDialog(p, ch, onDismiss = { rollback = null }) }
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

@Composable
private fun TreeRow(p: WorkspaceViewModel, node: TreeNode, menus: TreeMenus, drag: TreeDrag, onDrop: (java.io.File, java.io.File) -> Unit) {
    val f = node.file
    DisposableEffect(f) { onDispose { drag.forget(f.absolutePath) } }
    ContextMenuArea(items = { if (node.isDir) menus.forFolder(f, isRoot = false) else menus.forFile(f) }) {
        TreeRowContent(p, node, drag, onDrop)
    }
}

@Composable
private fun TreeRowContent(p: WorkspaceViewModel, node: TreeNode, drag: TreeDrag, onDrop: (java.io.File, java.io.File) -> Unit) {
    val c = ide
    val isActive = p.active?.file?.absoluteFile == node.file.absoluteFile
    val open = node.isDir && node.file in p.explorer.expanded
    ListRow(
        selected = isActive,
        onClick = { if (node.isDir) p.explorer.toggleDir(node.file) else p.open(node.file) },
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
