package es.hugoalvarezajenjo.sproutstudio.git

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Git state of one project window, observable by Compose. Every JGit call runs on one
 * background thread ([io]), so the UI never waits on the disk and calls never overlap.
 */
@Stable
class GitState {
    var repo by mutableStateOf<GitRepo?>(null)
        private set
    var status by mutableStateOf(GitStatus.Empty)
        private set
    /** Local branches, refreshed with [status]. */
    var branches by mutableStateOf<List<String>>(emptyList())
        private set
    /** The commit HEAD points at (for "Undo Last Commit" and amend). */
    var lastCommit by mutableStateOf<CommitInfo?>(null)
        private set
    var stashes by mutableStateOf<List<StashInfo>>(emptyList())
        private set
    /** Branch whose checkout was refused because of local changes (offers "stash and switch"). */
    var blockedCheckout by mutableStateOf<String?>(null)
    /** Bumped by "Stash Changes…" in a menu to open the Commit panel's stash popup. */
    var stashPopupTick by mutableStateOf(0)
    /** Bumped to ask for a status refresh (after a save, focus regained...). */
    var refreshTick by mutableStateOf(0)
        private set
    /** Last error from a git operation, shown in the Commit panel. */
    var error by mutableStateOf<String?>(null)
    private var root: File? = null
    private val headCache = HashMap<Pair<File, String?>, String?>()

    // ── Commit window state (kept here so it survives hiding the panel) ──
    var commitMessage by mutableStateOf("")
    /** Checkbox overrides by path; unset = default (tracked changes in, unversioned files out). */
    val selection = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    /** "Committed 3 files (a1b2c3d)", shown under the Commit button. */
    var lastResult by mutableStateOf<String?>(null)
    /** "Amend" ticked: the next commit replaces the last one instead of adding a new one. */
    var amend by mutableStateOf(false)

    fun isIncluded(c: FileChange) = selection[c.path] ?: (c.type != ChangeType.UNVERSIONED && c.type != ChangeType.CONFLICT)
    fun setIncluded(c: FileChange, on: Boolean) { selection[c.path] = on }
    val included: List<FileChange> get() = status.changes.filter { isIncluded(it) && it.type != ChangeType.CONFLICT }

    val isRepo: Boolean get() = repo != null

    /** Change of [f] against HEAD, or null if unchanged / not in the repo. */
    fun changeOf(f: File): FileChange? {
        if (status.changes.isEmpty()) return null
        return repo?.relPath(f)?.let { status.byPath[it] }
    }

    /** True when .gitignore hides [f] (or a folder above it). */
    fun isIgnored(f: File): Boolean {
        val ig = status.ignoredPaths
        if (ig.isEmpty()) return false
        val p = repo?.relPath(f) ?: return false
        return ig.any { p == it || p.startsWith("$it/") }
    }

    /** True when a folder holds tracked changes somewhere below it. */
    fun dirChanged(dir: File): Boolean {
        val d = status.changedDirs
        if (d.isEmpty()) return false
        return repo?.relPath(dir)?.let { it in d } ?: false
    }

    fun requestRefresh() { refreshTick++ }

    /** The project folder changed: find its repository (if any) and read its status. */
    suspend fun open(dir: File?) {
        val old = repo
        val found = withContext(io) { dir?.let(GitRepo::find) }
        root = dir?.absoluteFile
        repo = found
        status = GitStatus.Empty
        headCache.clear()
        withContext(io) { old?.close() }
        refresh()
    }

    suspend fun refresh() {
        val r = repo ?: run { status = GitStatus.Empty; return }
        runCatching { withContext(io) { Snapshot(r.status(root), r.branches(), r.lastCommit(), r.stashes()) } }
            .onSuccess { status = it.status; branches = it.branches; lastCommit = it.last; stashes = it.stashes }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
    }

    /** HEAD version of [f] (cached per commit), or null when it isn't committed. */
    suspend fun headText(f: File): String? {
        val r = repo ?: return null
        val key = f.absoluteFile to status.head
        if (key.second == null) return null
        synchronized(headCache) { if (key in headCache) return headCache[key] }
        val text = runCatching { withContext(io) { r.headText(f) } }.getOrNull()
        synchronized(headCache) { headCache[key] = text }
        return text
    }

    suspend fun author(): GitRepo.Author? = repo?.let { r -> runCatching { withContext(io) { r.author() } }.getOrNull() }

    /** `git init` in the project folder, optionally with a starter .gitignore. */
    suspend fun init(gitignore: Boolean = true) {
        val dir = root ?: return
        runCatching { withContext(io) { GitRepo.init(dir, gitignore) } }
            .onSuccess { repo = it; error = null; refresh() }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
    }

    /** Commits [files]; returns the new short id, or null with [error] set. */
    suspend fun commit(files: List<File>, message: String, amend: Boolean = false): String? {
        val r = repo ?: return null
        return runCatching { withContext(io) { r.commit(files, message, amend) } }
            .onSuccess { error = null }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
            .getOrNull()
            .also { refresh() }
    }

    suspend fun rollback(f: File): Boolean {
        val r = repo ?: return false
        return runCatching { withContext(io) { r.rollback(f) } }
            .onSuccess { error = null }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
            .isSuccess
            .also { refresh() }
    }

    /** Runs a branch operation; on failure keeps the message in [error] and returns false. */
    private suspend fun branchOp(op: (GitRepo) -> Unit): Boolean {
        val r = repo ?: return false
        return runCatching { withContext(io) { op(r) } }
            .onSuccess { error = null }
            .onFailure { error = describe(it) }
            .isSuccess
            .also { refresh() }
    }

    suspend fun checkout(name: String): Boolean {
        val ok = branchOp { it.checkout(name) }
        blockedCheckout = if (ok) null else name.takeIf { error?.startsWith(CONFLICT_PREFIX) == true }
        return ok
    }

    /** "Stash and switch": put local changes aside, then check out [name]. */
    suspend fun stashAndCheckout(name: String): Boolean {
        if (!branchOp { it.stash("Before switching to $name") }) return false
        return checkout(name)
    }

    /** Undo Last Commit; returns the undone commit so its message can be reused. */
    suspend fun undoLastCommit(): CommitInfo? {
        val r = repo ?: return null
        return runCatching { withContext(io) { r.undoLastCommit() } }
            .onSuccess { error = null }
            .onFailure { error = describe(it) }
            .getOrNull()
            .also { refresh() }
    }

    suspend fun history(f: File): List<CommitInfo> =
        repo?.let { r -> runCatching { withContext(io) { r.history(f) } }.getOrDefault(emptyList()) } ?: emptyList()

    /** Text of [f] at [rev] (a commit id, or "<id>^" for its parent); commits never change, so cached. */
    suspend fun textAt(rev: String, f: File): String? {
        val r = repo ?: return null
        val key = f.absoluteFile to rev
        synchronized(revCache) { if (key in revCache) return revCache[key] }
        val text = runCatching { withContext(io) { r.textAt(rev, f) } }.getOrNull()
        synchronized(revCache) { revCache[key] = text }
        return text
    }
    private val revCache = HashMap<Pair<File, String>, String?>()

    suspend fun stash(message: String?): Boolean {
        val r = repo ?: return false
        return runCatching { withContext(io) { r.stash(message) } }
            .onSuccess { error = if (it) null else "No local changes to stash" }
            .onFailure { error = describe(it) }
            .getOrDefault(false)
            .also { refresh() }
    }
    suspend fun popStash(index: Int) = branchOp { it.popStash(index) }
    suspend fun dropStash(index: Int) = branchOp { it.dropStash(index) }
    suspend fun createBranch(name: String) = branchOp { it.createBranch(name) }
    suspend fun deleteBranch(name: String) = branchOp { it.deleteBranch(name) }

    private fun describe(t: Throwable): String = when (t) {
        is org.eclipse.jgit.api.errors.CheckoutConflictException ->
            CONFLICT_PREFIX + t.conflictingPaths.joinToString()
        else -> t.message ?: t.javaClass.simpleName
    }

    fun close() {
        repo?.close()
        repo = null
    }

    private data class Snapshot(val status: GitStatus, val branches: List<String>, val last: CommitInfo?, val stashes: List<StashInfo>)

    private companion object {
        const val CONFLICT_PREFIX = "Your uncommitted changes would be overwritten: "
        @OptIn(ExperimentalCoroutinesApi::class)
        val io = Dispatchers.IO.limitedParallelism(1)
    }
}
