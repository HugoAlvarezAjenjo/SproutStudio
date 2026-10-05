package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.services.git.FileChange
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Git operations that also touch the open tabs and the project tree: unsaved editor text is
 * written first, the files git rewrites are held so no autosave lands mid-operation, and the
 * tabs and the tree catch up afterwards.
 *
 * Everything runs in the git ViewModel's scope, so closing the panel or popup that started an
 * operation doesn't cancel it. The views only call these methods.
 */
class GitWorkflow(private val ws: WorkspaceViewModel) {
    private val git get() = ws.git

    /** Commit window: commit (or amend) what's ticked, as the editors show it. */
    fun commit(): Job = git.scope.launch {
        if (!git.canCommit) return@launch
        // Like IntelliJ: commit what you see, so unsaved editor text is written first.
        ws.saveDirty()
        if (git.commitSelected()) ws.refreshTree()
    }

    /** Undo Last Commit, from the Commit panel, the Git menu or the palette. */
    fun undoLastCommit(showCommitPanel: Boolean = false): Job = git.scope.launch {
        if (!git.undoLast()) return@launch
        if (showCommitPanel) ws.showTool(SidebarTool.COMMIT)
        ws.refreshTree()
    }

    /** Throw away the uncommitted changes of one file, in the editor too. */
    fun rollback(ch: FileChange): Job = git.scope.launch {
        // Save the editor's copy first so autosave can't write it back over the restored file.
        val doc = ws.docs.firstOrNull { d -> d.file?.let { git.changeOf(it)?.path == ch.path } ?: false }
        doc?.takeIf { it.dirty }?.save()
        // Hold the file: an autosave landing mid-rollback would write the old text back.
        val restored = ws.deps.documents.exclusive(ch.file) { git.rollback(ch.file) }
        if (restored) {
            doc?.replaceFromDisk()
            ws.refreshTree()
        }
    }

    fun init(withGitignore: Boolean = true): Job = git.scope.launch { git.init(withGitignore) }

    /**
     * Checkout, new branch, stash, pop: [op] rewrites the working tree. The editors' unsaved text
     * is written first (it would be lost or block the switch), no save may write an open file
     * while git works, and afterwards open tabs take the files from disk; a tab whose file
     * doesn't exist any more is closed. [onDone] gets whether [op] succeeded, on the UI thread.
     */
    fun changeWorkingTree(op: suspend () -> Boolean, onDone: (Boolean) -> Unit = {}): Job = git.scope.launch {
        onDone(changingWorkingTree(op))
    }

    suspend fun changingWorkingTree(op: suspend () -> Boolean): Boolean {
        ws.saveDirty()
        val open = ws.docs.mapNotNull { it.file }.toTypedArray()
        val ok = ws.deps.documents.exclusive(*open) { op() }
        if (ok) {
            ws.docs.toList().forEach { d -> if (!d.syncWithDisk()) ws.closeDoc(d) }
            ws.refreshTree()
        }
        return ok
    }
}
