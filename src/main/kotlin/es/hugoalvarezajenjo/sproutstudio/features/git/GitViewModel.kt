package es.hugoalvarezajenjo.sproutstudio.features.git

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.hugoalvarezajenjo.sproutstudio.core.concurrency.AppDispatchers
import es.hugoalvarezajenjo.sproutstudio.core.viewmodel.ViewModel
import es.hugoalvarezajenjo.sproutstudio.domain.git.LineDiff
import es.hugoalvarezajenjo.sproutstudio.services.git.ChangeType
import es.hugoalvarezajenjo.sproutstudio.services.git.CommitInfo
import es.hugoalvarezajenjo.sproutstudio.services.git.FileChange
import es.hugoalvarezajenjo.sproutstudio.services.git.GitAuthor
import es.hugoalvarezajenjo.sproutstudio.services.git.GitError
import es.hugoalvarezajenjo.sproutstudio.services.git.GitException
import es.hugoalvarezajenjo.sproutstudio.services.git.GitRepository
import es.hugoalvarezajenjo.sproutstudio.services.git.GitService
import es.hugoalvarezajenjo.sproutstudio.services.git.GitStatus
import es.hugoalvarezajenjo.sproutstudio.services.git.StashInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshots.Snapshot
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Git state of one project window.
 *
 * Threading: every repository call runs on one background thread ([io]), one at a time.
 * State changes only on the UI thread. Opening a folder and reading the status are serialized
 * with [stateLock] and stamped with a [generation], so a status read for a folder you already
 * closed (or a slow read overtaken by a newer one) can never land on screen.
 *
 * Operations run in this ViewModel's own scope: closing a popup or a panel halfway through a
 * commit doesn't cancel it or the refresh after it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Stable
class GitViewModel(
    private val service: GitService,
    dispatchers: AppDispatchers,
    parent: Job? = null,
    private val refreshDelayMs: Long = 150,
) : ViewModel(dispatchers.main, parent) {

    private val io = dispatchers.io.limitedParallelism(1)
    private val cpu = dispatchers.default
    private val stateLock = Mutex()
    private var generation = 0L

    var repo by mutableStateOf<GitRepository?>(null)
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
        private set
    /** Why the last git operation failed; the UI words it. */
    var error by mutableStateOf<GitError?>(null)
        private set
    private var root: File? = null

    // ── Commit window state (kept here so it survives hiding the panel) ──
    var commitMessage by mutableStateOf("")
        private set
    /** Checkbox overrides by path; unset = default (tracked changes in, unversioned files out). */
    private val selection = mutableStateMapOf<String, Boolean>()
    /** What the last commit-window action did, shown under the Commit button. */
    var notice by mutableStateOf<GitNotice?>(null)
        private set
    /** "Amend" ticked: the next commit replaces the last one instead of adding a new one. */
    var amend by mutableStateOf(false)
        private set

    /** Typing in the message box; the last notice no longer applies. */
    fun editMessage(text: String) {
        commitMessage = text
        notice = null
    }

    /** Amending usually keeps the old message: start from it when the box is empty. */
    fun toggleAmend() {
        amend = !amend
        if (amend && commitMessage.isBlank()) commitMessage = lastCommit?.fullMessage.orEmpty()
    }

    val canCommit: Boolean
        get() = commitMessage.isNotBlank() && (if (amend) status.head != null else included.isNotEmpty())

    /**
     * Commits the ticked files with the typed message (or amends). On success the box is cleared
     * and [notice] says what happened; on failure [error] is set.
     */
    suspend fun commitSelected(): Boolean {
        if (!canCommit) return false
        val files = included.map { it.file }
        val amending = amend
        val id = commit(files, commitMessage, amend = amending) ?: return false
        commitMessage = ""
        selection.clear()
        amend = false
        notice = when {
            amending && files.isEmpty() -> GitNotice.Reworded(id)
            amending -> GitNotice.Amended(files.size, id)
            else -> GitNotice.Committed(files.size, id)
        }
        return true
    }

    /** Undo Last Commit, keeping its message in the box so it can be committed again. */
    suspend fun undoLast(): Boolean {
        val undone = undoLastCommit() ?: return false
        if (commitMessage.isBlank()) commitMessage = undone.fullMessage
        amend = false
        notice = GitNotice.Undid(undone.short)
        return true
    }

    private val stashPopup = Channel<Unit>(Channel.CONFLATED)
    /** "Stash Changes…" in a menu asks the Commit panel to open its stash popup. */
    val stashPopupRequests: Flow<Unit> = stashPopup.receiveAsFlow()

    fun requestStashPopup() { stashPopup.trySend(Unit) }

    fun clearError() { error = null }

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

    private var pendingRefresh: Job? = null

    /** Re-read the status soon (after a save, focus regained...). Bursts of requests coalesce. */
    fun requestRefresh() {
        pendingRefresh?.cancel()
        pendingRefresh = scope.launch { delay(refreshDelayMs); refresh() }
    }

    /** The project folder changed: find its repository (if any) and read its status. */
    fun openIn(dir: File?): Job = scope.launch { open(dir) }

    suspend fun open(dir: File?) {
        val old: GitRepository?
        stateLock.withLock {
            generation++
            val found = withContext(io) { dir?.let(service::find) }
            old = repo
            root = dir?.absoluteFile
            repo = found
            Snapshot.withMutableSnapshot {
                status = GitStatus.Empty
                branches = emptyList(); lastCommit = null; stashes = emptyList()
            }
            headCache.clear()
        }
        old?.let { withContext(io + NonCancellable) { it.close() } }
        refresh()
    }

    suspend fun refresh() {
        stateLock.withLock { readStatus() }
    }

    /** Caller holds [stateLock]. */
    private suspend fun readStatus() {
        val r = repo ?: run { status = GitStatus.Empty; return }
        val gen = generation
        val scopeDir = root
        runCatching { withContext(io) { RepoState(r.status(scopeDir), r.branches(), r.lastCommit(), r.stashes()) } }
            .onSuccess {
                if (gen != generation) return // the folder changed meanwhile
                // All at once: a reader on another thread never sees the new branch with the old list.
                Snapshot.withMutableSnapshot {
                    status = it.status; branches = it.branches; lastCommit = it.last; stashes = it.stashes
                }
            }
            .onFailure { if (gen == generation) error = errorOf(it) }
    }

    // Keyed by commit id: the text of a file at a commit never changes.
    private val headCache = ConcurrentHashMap<Pair<File, String>, Optional>()
    private val revCache = ConcurrentHashMap<Pair<File, String>, Optional>()
    private data class Optional(val text: String?)

    /** HEAD version of [f], or null when it isn't committed. */
    suspend fun headText(f: File): String? {
        val r = repo ?: return null
        // Read the file at THIS commit, not at "HEAD": HEAD may move while we read.
        val head = status.head ?: return null
        val key = f.absoluteFile to head
        headCache[key]?.let { return it.text }
        val text = runCatching { withContext(io) { r.textAt(head, f) } }.getOrNull()
        headCache[key] = Optional(text)
        return text
    }

    /** Text of [f] at [rev] (a commit id, or "<id>^" for its parent). */
    suspend fun textAt(rev: String, f: File): String? {
        val r = repo ?: return null
        val key = f.absoluteFile to rev
        revCache[key]?.let { return it.text }
        val text = runCatching { withContext(io) { r.textAt(rev, f) } }.getOrNull()
        revCache[key] = Optional(text)
        return text
    }

    /**
     * Gutter markers of [text] against [f]'s committed version; empty when git doesn't track it
     * yet. The diff runs off the UI thread.
     */
    suspend fun lineChanges(f: File, text: String): LineDiff.Index {
        val type = changeOf(f)?.type
        if (status.head == null || type == ChangeType.UNVERSIONED || type == ChangeType.ADDED) return LineDiff.Index.Empty
        val base = headText(f) ?: return LineDiff.Index.Empty
        return withContext(cpu) { LineDiff.Index(LineDiff.compute(base, text), base) }
    }

    /** Whole-file diff rows for the text diff window, computed off the UI thread. */
    suspend fun diffRows(base: String?, current: String?): List<LineDiff.Row> =
        withContext(cpu) { LineDiff.rows(base.orEmpty(), current.orEmpty()) }

    suspend fun author(): GitAuthor? = repo?.let { r -> runCatching { withContext(io) { r.author() } }.getOrNull() }

    suspend fun history(f: File): List<CommitInfo> =
        repo?.let { r -> runCatching { withContext(io) { r.history(f) } }.getOrDefault(emptyList()) } ?: emptyList()

    /** `git init` in the project folder, optionally with a starter .gitignore. */
    suspend fun init(gitignore: Boolean = true) {
        val dir = root ?: return
        runCatching { withContext(io) { service.init(dir, gitignore) } }
            .onSuccess { r -> stateLock.withLock { generation++; repo = r }; error = null; refresh() }
            .onFailure { error = errorOf(it) }
    }

    /** Commits [files]; returns the new short id, or null with [error] set. */
    suspend fun commit(files: List<File>, message: String, amend: Boolean = false): String? =
        op { it.commit(files, message, amend) }

    suspend fun rollback(f: File): Boolean = op { it.rollback(f) } != null

    suspend fun checkout(name: String): Boolean {
        val ok = op { it.checkout(name) } != null
        blockedCheckout = if (ok) null else name.takeIf { error is GitError.CheckoutConflict }
        return ok
    }

    /** "Stash and switch": put local changes aside, then check out [name]. */
    suspend fun stashAndCheckout(name: String): Boolean {
        if (op { it.stash("Before switching to $name") } == null) return false
        return checkout(name)
    }

    /** Undo Last Commit; returns the undone commit so its message can be reused. */
    suspend fun undoLastCommit(): CommitInfo? = op { it.undoLastCommit() }

    suspend fun stash(message: String?): Boolean {
        val stashed = op { it.stash(message) } ?: return false
        if (!stashed) error = GitError.NothingToStash
        return stashed
    }

    suspend fun popStash(index: Int) = op { it.popStash(index) } != null
    suspend fun dropStash(index: Int) = op { it.dropStash(index) } != null
    suspend fun createBranch(name: String) = op { it.createBranch(name) } != null
    suspend fun deleteBranch(name: String) = op { it.deleteBranch(name) } != null

    /** Runs a repository change; on failure sets [error] and returns null. Always re-reads the status. */
    private suspend fun <T : Any> op(block: (GitRepository) -> T): T? {
        val r = repo ?: return null
        return try {
            runCatching { withContext(io) { block(r) } }
                .onSuccess { error = null }
                .onFailure { error = errorOf(it) }
                .getOrNull()
        } finally {
            withContext(NonCancellable) { refresh() }
        }
    }

    private fun errorOf(t: Throwable): GitError = (t as? GitException)?.error ?: GitError.Failed(t.message ?: t.javaClass.simpleName)

    override fun close() {
        val r = repo
        repo = null
        super.close()
        // Release the repository's files even though our scope is gone.
        r?.let { kotlin.concurrent.thread(name = "git-close", isDaemon = true) { it.close() } }
    }

    private data class RepoState(val status: GitStatus, val branches: List<String>, val last: CommitInfo?, val stashes: List<StashInfo>)
}
