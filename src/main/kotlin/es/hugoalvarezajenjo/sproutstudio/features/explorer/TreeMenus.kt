package es.hugoalvarezajenjo.sproutstudio.features.explorer

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.VisibilityOff
import es.hugoalvarezajenjo.sproutstudio.core.os.shortcutHint
import es.hugoalvarezajenjo.sproutstudio.features.git.canCompareWithHead
import es.hugoalvarezajenjo.sproutstudio.features.git.canRollback
import es.hugoalvarezajenjo.sproutstudio.features.git.hasCommittedChanges
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_copy_image
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_new_folder
import es.hugoalvarezajenjo.sproutstudio.resources.git_open
import es.hugoalvarezajenjo.sproutstudio.resources.git_rollback_menu
import es.hugoalvarezajenjo.sproutstudio.resources.git_show_history
import es.hugoalvarezajenjo.sproutstudio.resources.menu_compare_with_head
import es.hugoalvarezajenjo.sproutstudio.resources.menu_refresh_files
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_diff_with_head
import es.hugoalvarezajenjo.sproutstudio.resources.preview_copied
import es.hugoalvarezajenjo.sproutstudio.resources.preview_copy_failed
import es.hugoalvarezajenjo.sproutstudio.resources.preview_export_failed
import es.hugoalvarezajenjo.sproutstudio.resources.preview_export_title
import es.hugoalvarezajenjo.sproutstudio.resources.preview_saved
import es.hugoalvarezajenjo.sproutstudio.resources.tab_copy_path
import es.hugoalvarezajenjo.sproutstudio.resources.tree_add_to_gitignore
import es.hugoalvarezajenjo.sproutstudio.resources.tree_collapse_all
import es.hugoalvarezajenjo.sproutstudio.resources.tree_copy_relative_path
import es.hugoalvarezajenjo.sproutstudio.resources.tree_duplicate
import es.hugoalvarezajenjo.sproutstudio.resources.tree_expand_all
import es.hugoalvarezajenjo.sproutstudio.resources.tree_export
import es.hugoalvarezajenjo.sproutstudio.resources.tree_new_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.tree_open_terminal
import es.hugoalvarezajenjo.sproutstudio.resources.tree_rename
import es.hugoalvarezajenjo.sproutstudio.resources.tree_trash
import es.hugoalvarezajenjo.sproutstudio.features.preview.exportFailure
import es.hugoalvarezajenjo.sproutstudio.features.preview.label
import es.hugoalvarezajenjo.sproutstudio.services.git.FileChange
import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.MenuItem
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.menu
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import java.io.File
import kotlinx.coroutines.launch

/**
 * The project tree's right-click menus. Entries that would do nothing are left out (git ones
 * outside a repository, Rollback on an unchanged file) rather than shown disabled.
 * [onRollback] asks for confirmation first; [onMessage] shows how an operation went, under the tree.
 */
internal class TreeMenus(
    private val p: WorkspaceViewModel,
    private val onRollback: (FileChange) -> Unit,
    private val onMessage: (TreeMessage) -> Unit,
) {
    private val explorer get() = p.explorer
    private val platform get() = p.deps.platform

    fun forFile(f: File): List<ContextMenuItem> = menu {
        val dir = f.absoluteFile.parentFile
        group { add(MenuItem(text(Res.string.git_open)) { p.open(f) }) }
        group { create(dir) }
        group {
            copyPaths(f)
            add(MenuItem(text(Res.string.tree_duplicate), Icons.Outlined.FileCopy) { duplicate(f) })
        }
        group { add(export(f)) }
        group { git(f) }
        group { manage(f) }
    }

    fun forFolder(dir: File, isRoot: Boolean): List<ContextMenuItem> = menu {
        group { create(dir) }
        group { copyPaths(dir) }
        group {
            add(MenuItem(text(Res.string.tree_expand_all), Icons.Outlined.UnfoldMore) { explorer.expandAllUnder(dir) })
            add(MenuItem(text(Res.string.tree_collapse_all), Icons.Outlined.UnfoldLess) {
                if (isRoot) explorer.collapseAll() else explorer.collapseAllUnder(dir)
            })
            if (isRoot) add(MenuItem(text(Res.string.menu_refresh_files), Icons.Outlined.Refresh, shortcut = shortcutHint("Y", alt = true)) { explorer.refreshTree() })
        }
        group { if (!isRoot) ignore(dir) }
        group {
            if (!isRoot) manage(dir) else reveal(dir)
            add(MenuItem(text(Res.string.tree_open_terminal), Icons.Outlined.Terminal) { platform.openTerminal(dir) })
        }
    }

    private fun MutableList<ContextMenuItem>.create(dir: File) {
        add(MenuItem(text(Res.string.tree_new_diagram), Icons.AutoMirrored.Outlined.NoteAdd) { explorer.showFileAction(FileAction.NewDiagram(dir)) })
        add(MenuItem(text(Res.string.cmd_new_folder), Icons.Outlined.CreateNewFolder) { explorer.showFileAction(FileAction.NewFolder(dir)) })
    }

    private fun MutableList<ContextMenuItem>.copyPaths(f: File) {
        add(MenuItem(text(Res.string.tab_copy_path), Icons.Outlined.ContentCopy) { platform.copyTextToClipboard(f.absolutePath) })
        explorer.relativePath(f)?.let { rel ->
            add(MenuItem(text(Res.string.tree_copy_relative_path)) { platform.copyTextToClipboard(rel) })
        }
    }

    private fun MutableList<ContextMenuItem>.git(f: File) {
        if (p.git.status.head == null) return
        if (canCompareWithHead(p, f)) add(MenuItem(text(Res.string.menu_compare_with_head), Icons.Outlined.Difference) { p.deps.windows.compareWithHead(p, f) })
        if (hasCommittedChanges(p, f)) add(MenuItem(text(Res.string.menu_show_diff_with_head)) { p.deps.windows.compareWithHead(p, f, textFirst = true) })
        add(MenuItem(text(Res.string.git_show_history), Icons.Outlined.History) { p.deps.windows.showHistory(p, f) })
        p.git.changeOf(f)?.takeIf(::canRollback)?.let { ch ->
            add(MenuItem(text(Res.string.git_rollback_menu), Icons.AutoMirrored.Outlined.Undo) { onRollback(ch) })
        }
        ignore(f)
    }

    /** Only where there's a repository, and for what it doesn't ignore yet. */
    private fun MutableList<ContextMenuItem>.ignore(f: File) {
        if (p.git.repo == null || p.git.isIgnored(f)) return
        add(MenuItem(text(Res.string.tree_add_to_gitignore), Icons.Outlined.VisibilityOff) { p.scope.launch { p.git.ignore(f) } })
    }

    private fun MutableList<ContextMenuItem>.manage(f: File) {
        add(MenuItem(text(Res.string.tree_rename), Icons.Outlined.DriveFileRenameOutline) { explorer.showFileAction(FileAction.Rename(f)) })
        reveal(f)
        add(MenuItem(text(Res.string.tree_trash), Icons.Outlined.Delete, danger = true) { explorer.showFileAction(FileAction.Delete(f)) })
    }

    private fun MutableList<ContextMenuItem>.reveal(f: File) {
        add(MenuItem(text(OsTexts.reveal), Icons.Outlined.FolderOpen) { platform.reveal(f) })
    }

    private fun duplicate(f: File) {
        p.scope.launch { explorer.duplicate(f).exceptionOrNull()?.let { onMessage(TreeMessage(it.fileError.describe())) } }
    }

    /** Copy and export of the file's first diagram, as the preview toolbar does for the open one. */
    private fun export(f: File) = MenuItem(
        text(Res.string.tree_export), Icons.Outlined.FileDownload,
        children = listOf(
            MenuItem(text(Res.string.cmd_copy_image), Icons.Outlined.Image) {
                p.scope.launch {
                    val r = explorer.copyDiagramImage(f)
                    onMessage(r.exceptionOrNull()?.let { TreeMessage(text(Res.string.preview_copy_failed, exportFailure(it))) } ?: TreeMessage(text(Res.string.preview_copied), error = false))
                }
            },
        ) + ExportFormat.entries.map { format -> MenuItem(format.label()) { exportAs(f, format) } },
    )

    private fun exportAs(f: File, format: ExportFormat) {
        val dir = f.absoluteFile.parentFile
        val chosen = platform.chooseSaveTarget(text(Res.string.preview_export_title, format.label()), "${f.nameWithoutExtension}.${format.extension}", dir) ?: return
        val target = format.withExtension(chosen)
        p.scope.launch {
            val r = explorer.exportDiagram(f, format, target)
            onMessage(r.exceptionOrNull()?.let { TreeMessage(text(Res.string.preview_export_failed, exportFailure(it))) } ?: TreeMessage(text(Res.string.preview_saved, target.name), error = false))
        }
    }
}

/** A line under the tree after an operation: what failed, or what was done. */
internal data class TreeMessage(val text: String, val error: Boolean = true)
