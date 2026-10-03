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
        runCatching { withContext(io) { r.status(root) } }
            .onSuccess { status = it }
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

    /** `git init` in the project folder. */
    suspend fun init() {
        val dir = root ?: return
        runCatching { withContext(io) { GitRepo.init(dir) } }
            .onSuccess { repo = it; error = null; refresh() }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
    }

    /** Commits [files]; returns the new short id, or null with [error] set. */
    suspend fun commit(files: List<File>, message: String): String? {
        val r = repo ?: return null
        return runCatching { withContext(io) { r.commit(files, message) } }
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

    fun close() {
        repo?.close()
        repo = null
    }

    private companion object {
        @OptIn(ExperimentalCoroutinesApi::class)
        val io = Dispatchers.IO.limitedParallelism(1)
    }
}
