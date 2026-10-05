package es.hugoalvarezajenjo.sproutstudio.services.git

import java.io.Closeable
import java.io.File

/**
 * One git repository. Every call BLOCKS on the disk and none is thread-safe: callers run them
 * on one background thread, one at a time (the git ViewModel does). Refusals and failures are
 * thrown as [GitException].
 */
interface GitRepository : Closeable {
    val workTree: File

    /** Path of [f] inside the work tree ("docs/a.puml"), or null if it lives outside. */
    fun relPath(f: File): String?

    fun branch(): String?
    fun headId(): String?

    /** Changes under [scope] (the project folder; the whole work tree when null or the root). */
    fun status(scope: File? = null): GitStatus

    /** Text of [f] in the last commit, or null if it isn't committed (new file, no commits yet). */
    fun headText(f: File): String?

    /** Text of [f] at revision [rev] ("HEAD", a commit id, "abc123^"...), or null if absent there. */
    fun textAt(rev: String, f: File): String?

    fun author(): GitAuthor

    /** Commits exactly [files] (like `git commit --only`); returns the new commit's short id. */
    fun commit(files: List<File>, message: String, amend: Boolean = false): String

    /** Throws away the local changes of [f]. A file new since HEAD is only un-added, never deleted. */
    fun rollback(f: File)

    fun lastCommit(): CommitInfo?

    /** `git reset --soft HEAD~1`; returns the undone commit. */
    fun undoLastCommit(): CommitInfo

    /** Commits that touched [f], newest first. */
    fun history(f: File, max: Int = 300): List<CommitInfo>

    /** `git stash`; false if there was nothing to put aside. */
    fun stash(message: String?): Boolean
    fun stashes(): List<StashInfo>
    fun popStash(index: Int)
    fun dropStash(index: Int)

    fun branches(): List<String>
    fun checkout(name: String)
    fun createBranch(name: String)
    fun deleteBranch(name: String)
}
