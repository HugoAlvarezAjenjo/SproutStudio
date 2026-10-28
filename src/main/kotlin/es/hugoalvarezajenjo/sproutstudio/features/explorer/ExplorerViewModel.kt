package es.hugoalvarezajenjo.sproutstudio.features.explorer

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Template
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.services.files.FileError
import es.hugoalvarezajenjo.sproutstudio.services.files.FileOpException
import es.hugoalvarezajenjo.sproutstudio.services.files.TreeNode
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The reason inside a failed file operation (anything unexpected becomes [FileError.Failed]). */
val Throwable.fileError: FileError
    get() = (this as? FileOpException)?.error ?: FileError.Failed(message ?: javaClass.simpleName)

/**
 * The project tree: what it shows, which folders are open, the pending name/confirm dialog, and
 * creating, renaming, moving and trashing in it. Open tabs are kept in step (a renamed file's tab
 * follows, a deleted file's tab closes), which is why it holds its parent [ws]: the tree view
 * state is its own, the tab coupling runs through the workspace — the same split as GitWorkflow.
 *
 * Not a [ViewModel]: it has no scope of its own. File operations run in [ws]'s scope (the views
 * launch them there), so a panel closing mid-operation never cancels them. Refusals come back as a
 * [FileError] inside a [Result].
 */
@Stable
class ExplorerViewModel(private val ws: WorkspaceViewModel) {
    private val store get() = ws.deps.documents
    private val files get() = ws.deps.files
    private val root get() = ws.root

    /** The visible rows under the project folder; rebuilt by [refreshTree]. */
    var tree by mutableStateOf<List<TreeNode>>(emptyList())
        private set

    /** The in-flight tree rebuild, cancelled when a newer one starts (latest wins). */
    private var refreshJob: Job? = null

    /** Folders the user has opened. */
    val expanded = mutableStateListOf<File>()

    /** The project folder's own row is collapsible too, like every folder under it. */
    var rootCollapsed by mutableStateOf(false)
        private set

    fun toggleRootCollapsed() { rootCollapsed = !rootCollapsed }
    fun expandRoot() { rootCollapsed = false }

    /** New / rename / delete dialog the tree is showing, if any. */
    var fileAction by mutableStateOf<FileAction?>(null)
        private set

    fun showFileAction(action: FileAction) { fileAction = action }
    fun dismissFileAction() { fileAction = null }

    /** Reset to the welcome state (the project was closed). */
    fun clear() {
        expanded.clear()
        rootCollapsed = false
        fileAction = null
        tree = emptyList()
    }

    /** A project folder was opened: forget the old tree state and show the new one. */
    fun onProjectOpened() {
        rootCollapsed = false
        expanded.clear()
        refreshTreeBlocking()
    }

    fun toggleDir(dir: File) {
        if (dir in expanded) expanded.remove(dir) else expanded.add(dir)
        refreshTreeBlocking()
    }

    fun collapseAll() {
        expanded.clear()
        refreshTreeBlocking()
    }

    /** Opens [dir] and every folder below it. */
    fun expandAllUnder(dir: File) {
        files.foldersUnder(dir.absoluteFile).forEach { if (it !in expanded) expanded.add(it) }
        if (dir.absoluteFile == root?.absoluteFile) expandRoot()
        refreshTreeBlocking()
    }

    /** Closes [dir] and every folder below it (the root's row stays open: it's [rootCollapsed]). */
    fun collapseAllUnder(dir: File) {
        val d = dir.absoluteFile
        expanded.removeAll { it.absoluteFile == d || it.absoluteFile.startsWith(d) }
        refreshTreeBlocking()
    }

    /** [f]'s path from the project folder, with `/` on every OS ("docs/a.puml"); null outside it. */
    fun relativePath(f: File): String? {
        val r = root?.absoluteFile ?: return null
        val a = f.absoluteFile
        if (a != r && !a.startsWith(r)) return null
        return a.relativeTo(r).invariantSeparatorsPath.ifEmpty { "." }
    }

    /**
     * Rebuild the tree for a direct navigation action (open a folder, toggle a row, Collapse All):
     * one walk the user is waiting on, so it's done inline. The repeated case — a refresh after
     * every save, rename, move or commit — goes through [refreshTreeNow]/[refreshTree] off the UI
     * thread instead.
     */
    private fun refreshTreeBlocking() {
        ws.git.requestRefresh()
        val r = root ?: run { tree = emptyList(); return }
        tree = files.tree(r, expanded.toList())
    }

    fun refreshTree() {
        refreshJob?.cancel()
        refreshJob = ws.scope.launch { refreshTreeNow() }
    }

    /**
     * Rebuild the tree now, walking the folder off the UI thread, and return once [tree] holds
     * the result. File operations await this so the new state is visible when they return; the
     * fire-and-forget [refreshTree] is for the git workflow and the Refresh button.
     */
    suspend fun refreshTreeNow() {
        ws.git.requestRefresh()
        val r = root ?: run { tree = emptyList(); return }
        // Listing a folder is disk I/O: do it on io, then publish on the UI thread.
        val built = withContext(ws.deps.dispatchers.io) { files.tree(r, expanded.toList()) }
        tree = built
    }

    /** Every diagram under the root, expanded or not (for the command palette's "go to file"). */
    fun allDiagrams(limit: Int = 2000): List<File> {
        val r = root ?: return emptyList()
        return files.allDiagrams(r, limit)
    }

    // ── File operations (keep the open tabs in step) ─────────────────────────────────────────

    /** A new diagram file: empty, or holding [template] with the caret on its `$0` marker. */
    suspend fun newDiagramIn(dir: File, name: String, template: Template? = null): Result<File> = runCatching {
        if (name.isBlank()) throw FileOpException(FileError.EmptyName)
        val n = files.diagramName(name)
        files.validate(dir, n)?.let { throw FileOpException(it) }
        val f = File(dir, n)
        store.write(f, template?.previewSource ?: "")
        expandTo(dir)
        refreshTreeNow()
        ws.open(f)
        template?.body?.indexOf("\$0")?.takeIf { it >= 0 }?.let { at ->
            ws.active?.let { d -> d.value = d.value.copy(selection = TextRange(at)) }
        }
        f
    }

    suspend fun newFolderIn(dir: File, name: String): Result<File> = runCatching {
        val f = files.createFolder(dir, name)
        expandTo(dir)
        if (f !in expanded) expanded.add(f)
        refreshTreeNow()
        f
    }

    suspend fun rename(target: File, newName: String): Result<File> = runCatching {
        val from = target.absoluteFile
        val to = files.renameTarget(root, from, newName)
        // Compare paths, not Files: on Windows File equality ignores case, so a.puml -> A.puml
        // would look like "nothing to do".
        if (to.path == from.path) from else relocate(from, to)
    }

    /**
     * Drag & drop: moves [target] (a file or a folder) into [destDir], keeping its name.
     * Refuses a move onto itself, into its own subfolder, or over an existing name.
     */
    suspend fun move(target: File, destDir: File): Result<File> = runCatching {
        val from = target.absoluteFile
        val dest = destDir.absoluteFile
        val to = files.moveTarget(root, from, dest) ?: return@runCatching from
        relocate(from, to).also { expandTo(dest); if (dest != root?.absoluteFile && dest !in expanded) expanded.add(dest); refreshTreeNow() }
    }

    /** Whether dropping [target] on [destDir] would do something (for the drop highlight). */
    fun canMove(target: File, destDir: File): Boolean = files.canMove(target, destDir)

    /** A copy of the diagram [target] next to it, opened in a tab. */
    suspend fun duplicate(target: File): Result<File> = runCatching {
        // The copy takes what the editor shows, unsaved edits included.
        ws.deps.autoSaver.flush(ws.docs.toList())
        val copy = withContext(ws.deps.dispatchers.io) { files.duplicate(target) }
        refreshTreeNow()
        ws.open(copy)
        copy
    }

    /** Moves [target] to the Trash and closes its tabs (or every tab under it, for a folder). */
    suspend fun delete(target: File): Result<Unit> = runCatching {
        val f = target.absoluteFile
        if (f == root?.absoluteFile) throw FileOpException(FileError.ProjectFolder)
        val inside = ws.docs.filter { d -> d.file?.absoluteFile?.let { it == f || it.startsWith(f) } == true }
        // Close first: autosave must not write a deleted file back.
        inside.forEach { ws.closeDoc(it) }
        files.moveToTrash(root, f)
        expanded.removeAll { it.absoluteFile == f || it.absoluteFile.startsWith(f) }
        refreshTreeNow()
    }

    /** The shared part of rename and move: moves on disk, then tabs and expanded folders follow. */
    private suspend fun relocate(from: File, to: File): File {
        // Unsaved edits go to the old path first, so the move carries them.
        ws.deps.autoSaver.flush(ws.docs.toList())
        // No save may land on either path while it moves.
        store.exclusive(from, to) { files.move(from, to) }
        for (d in ws.docs) d.file?.absoluteFile?.let { f -> moved(f, from, to)?.let { d.relocate(it) } }
        val exp = expanded.toList(); expanded.clear(); exp.forEach { e -> expanded.add(moved(e.absoluteFile, from, to) ?: e) }
        refreshTreeNow()
        return to
    }

    /** Expands every folder from the root down to [dir], so a new item is visible. */
    private fun expandTo(dir: File) {
        val r = root?.absoluteFile ?: return
        var d: File? = dir.absoluteFile
        val chain = ArrayList<File>()
        while (d != null && d != r && d.startsWith(r)) { chain += d; d = d.parentFile }
        chain.forEach { if (it !in expanded) expanded.add(it) }
        expandRoot()
    }
}

/** [f] after [from] became [to]: itself, or a file inside a renamed folder. Null if unaffected. */
private fun moved(f: File, from: File, to: File): File? = when {
    f == from -> to
    f.startsWith(from) -> File(to, f.relativeTo(from).path)
    else -> null
}
