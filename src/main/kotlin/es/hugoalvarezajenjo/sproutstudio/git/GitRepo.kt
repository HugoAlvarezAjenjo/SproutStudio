package es.hugoalvarezajenjo.sproutstudio.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.UserConfig
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.Closeable
import java.io.File

/** How a file differs from the last commit (HEAD), the way IntelliJ's Commit window shows it. */
enum class ChangeType { MODIFIED, ADDED, DELETED, UNVERSIONED, CONFLICT }

/** One changed file. [path] is relative to the repository's work tree, with '/' separators. */
data class FileChange(val path: String, val type: ChangeType, val file: File)

/** A status snapshot: the changes under the project folder plus what .gitignore hides there. */
@JvmInline value class IgnoredPath(val path: String)

/** One commit, for history lists and "last commit" labels. */
data class CommitInfo(
    val id: String,
    val message: String,
    val fullMessage: String,
    val author: String,
    val timeMillis: Long,
    val parents: Int,
) {
    val short: String get() = id.take(7)

    companion object {
        fun of(c: org.eclipse.jgit.revwalk.RevCommit) = CommitInfo(
            c.name, c.shortMessage, c.fullMessage.trimEnd(), c.authorIdent.name,
            c.authorIdent.whenAsInstant.toEpochMilli(), c.parentCount,
        )
    }
}

data class StashInfo(val index: Int, val message: String, val timeMillis: Long)

data class GitStatus(
    val branch: String?,
    /** Commit id of HEAD, or null before the first commit. */
    val head: String?,
    val changes: List<FileChange>,
    /** Ignored files and folders (folders as a whole when everything in them is ignored). */
    val ignored: Set<IgnoredPath>,
) {
    val byPath: Map<String, FileChange> by lazy { changes.associateBy { it.path } }
    /** Folders (relative paths) that contain a tracked change, for the tree colours. */
    val changedDirs: Set<String> by lazy {
        changes.filter { it.type != ChangeType.UNVERSIONED }
            .flatMap { c -> generateSequence(c.path.substringBeforeLast('/', "")) { p -> p.substringBeforeLast('/', "").takeIf { p.isNotEmpty() } }.filter { it.isNotEmpty() } }
            .toSet()
    }
    /** Ignored paths, relative ("out" covers everything under it). */
    val ignoredPaths: Set<String> by lazy { ignored.map { it.path }.toSet() }

    companion object {
        val Empty = GitStatus(null, null, emptyList(), emptySet())
    }
}

/**
 * A git repository, read and written in-process with JGit (no `git` binary needed). Only local
 * operations: status, file contents at HEAD, commit, rollback, init. Not thread-safe on its own:
 * callers serialize access (see [GitState]).
 */
class GitRepo private constructor(private val repo: Repository) : Closeable {
    private val git = Git(repo)
    val workTree: File = repo.workTree.absoluteFile
    private val canonicalWorkTree: File = runCatching { workTree.canonicalFile }.getOrDefault(workTree)

    /**
     * Path of [f] inside the work tree ("docs/a.puml"), or null if it lives outside. Canonical
     * paths, so a symlinked folder (macOS /var -> /private/var) still matches.
     */
    fun relPath(f: File): String? {
        val cf = runCatching { f.canonicalFile }.getOrDefault(f.absoluteFile.normalize())
        val rel = cf.relativeToOrNull(canonicalWorkTree) ?: return null
        val p = rel.invariantSeparatorsPath
        return if (p.startsWith("..")) null else p
    }

    fun branch(): String? = runCatching {
        val full = repo.fullBranch ?: return null
        if (full.startsWith(Constants.R_HEADS)) Repository.shortenRefName(full)
        else repo.resolve(Constants.HEAD)?.abbreviate(7)?.name() // detached HEAD: show the commit
    }.getOrNull()

    fun headId(): String? = repo.resolve(Constants.HEAD)?.name

    /** Changes under [scope] (the project folder; the whole work tree when null or the root). */
    fun status(scope: File? = null): GitStatus {
        val prefix = scope?.let(::relPath)?.takeIf { it.isNotEmpty() && it != "." }
        val cmd = git.status()
        if (prefix != null) cmd.addPath(prefix)
        val s = cmd.call()
        val out = LinkedHashMap<String, ChangeType>()
        // Order matters: later puts win, so the most specific state ends up in the map.
        s.modified.forEach { out[it] = ChangeType.MODIFIED }
        s.changed.forEach { out[it] = ChangeType.MODIFIED }
        s.added.forEach { out[it] = ChangeType.ADDED }
        // Added to the index, then edited again: still "new since HEAD".
        s.modified.filter { it in s.added }.forEach { out[it] = ChangeType.ADDED }
        s.missing.forEach { out[it] = if (it in s.added) ChangeType.ADDED else ChangeType.DELETED }
        s.removed.forEach { out[it] = ChangeType.DELETED }
        s.untracked.forEach { out[it] = ChangeType.UNVERSIONED }
        s.conflicting.forEach { out[it] = ChangeType.CONFLICT }
        // Added then deleted from disk: nothing left to show.
        s.missing.filter { it in s.added }.forEach { out.remove(it) }
        val changes = out.entries
            .map { (p, t) -> FileChange(p, t, File(workTree, p)) }
            .sortedBy { it.path.lowercase() }
        val ignored = s.ignoredNotInIndex.map { IgnoredPath(it.trimEnd('/')) }.toSet()
        return GitStatus(branch(), headId(), changes, ignored)
    }

    /** Text of [f] in the last commit, or null if it isn't committed (new file, no commits yet). */
    fun headText(f: File): String? = textAt(Constants.HEAD, f)

    /** Text of [f] at revision [rev] ("HEAD", a commit id, "abc123^"...), or null if absent there. */
    fun textAt(rev: String, f: File): String? {
        val path = relPath(f) ?: return null
        val head = runCatching { repo.resolve("$rev^{tree}") }.getOrNull() ?: return null
        TreeWalk.forPath(repo, path, head)?.use { tw ->
            val bytes = repo.open(tw.getObjectId(0)).bytes
            return String(bytes, Charsets.UTF_8)
        }
        return null
    }

    /** Who commits will be made as, from the repo's and the user's git config. */
    fun author(): Author {
        val u = repo.config.get(UserConfig.KEY)
        return Author(u.authorName, u.authorEmail, implicit = u.isAuthorNameImplicit || u.isAuthorEmailImplicit)
    }

    data class Author(val name: String, val email: String, val implicit: Boolean)

    /**
     * Commits exactly [files] (like IntelliJ's checkboxes, or `git commit --only`): anything else
     * already staged stays staged and out of this commit. New files are added, deleted ones removed.
     * Returns the short id of the new commit.
     */
    fun commit(files: List<File>, message: String, amend: Boolean = false): String {
        require(message.isNotBlank()) { "Empty commit message" }
        val paths = files.mapNotNull(::relPath).distinct()
        if (amend) {
            require(headId() != null) { "Nothing to amend yet" }
            if (paths.isEmpty()) {
                // Amend with no files: just reword the last commit.
                return git.commit().setAmend(true).setMessage(message.trim() + "\n").call().id.abbreviate(7).name()
            }
        }
        require(paths.isNotEmpty()) { "No files selected" }
        val s = git.status().apply { paths.forEach(::addPath) }.call()
        val untracked = paths.filter { it in s.untracked }
        if (untracked.isNotEmpty()) git.add().apply { untracked.forEach(::addFilepattern) }.call()
        val commit = git.commit().setMessage(message.trim() + "\n").setAmend(amend)
        if (headId() != null) paths.forEach { commit.setOnly(it) }
        else {
            // First commit: --only isn't supported without a HEAD, so stage exactly the selection.
            val gone = paths.filter { !File(workTree, it).exists() }
            val present = paths - gone.toSet()
            if (present.isNotEmpty()) git.add().apply { present.forEach(::addFilepattern) }.call()
            if (gone.isNotEmpty()) git.rm().setCached(true).apply { gone.forEach(::addFilepattern) }.call()
        }
        return commit.call().id.abbreviate(7).name()
    }

    /**
     * Throws away the local changes of [f]: back to its HEAD version (restoring it if deleted).
     * A file that is new since HEAD is only un-added (it stays on disk, unversioned): rollback
     * never deletes your work.
     */
    fun rollback(f: File) {
        val path = relPath(f) ?: return
        val inHead = headText(f) != null
        if (inHead) {
            git.checkout().setStartPoint(Constants.HEAD).addPath(path).call()
        } else if (headId() != null) {
            git.reset().addPath(path).call()
        } else {
            git.rm().setCached(true).addFilepattern(path).call()
        }
    }

    /** The commit HEAD points at, or null before the first commit. */
    fun lastCommit(): CommitInfo? {
        val id = repo.resolve(Constants.HEAD) ?: return null
        return RevWalk(repo).use { CommitInfo.of(it.parseCommit(id)) }
    }

    /**
     * "Undo Last Commit": `git reset --soft HEAD~1`. The commit disappears from the branch but its
     * changes stay, ready to commit again; nothing on disk changes. Returns the undone commit.
     */
    fun undoLastCommit(): CommitInfo {
        val last = lastCommit() ?: throw IllegalStateException("There is no commit to undo")
        require(last.parents > 0) { "The first commit can't be undone" }
        git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.SOFT).setRef("HEAD~1").call()
        return last
    }

    /** Commits that touched [f], newest first (doesn't follow renames). */
    fun history(f: File, max: Int = 300): List<CommitInfo> {
        val path = relPath(f) ?: return emptyList()
        if (headId() == null) return emptyList()
        return git.log().addPath(path).setMaxCount(max).call().map(CommitInfo::of)
    }

    // ── stash ──

    /** Puts the uncommitted changes to tracked files aside (`git stash`); false if there were none. */
    fun stash(message: String?): Boolean {
        require(headId() != null) { "Make the first commit before stashing" }
        val cmd = git.stashCreate()
        if (!message.isNullOrBlank()) cmd.setWorkingDirectoryMessage(message.trim())
        return cmd.call() != null
    }

    fun stashes(): List<StashInfo> = git.stashList().call().mapIndexed { i, c ->
        StashInfo(i, c.shortMessage, c.authorIdent.whenAsInstant.toEpochMilli())
    }

    /** `git stash pop`: re-applies stash [index] and drops it (kept if applying fails). */
    fun popStash(index: Int) {
        try {
            git.stashApply().setStashRef("stash@{$index}").call()
        } catch (e: org.eclipse.jgit.api.errors.StashApplyFailureException) {
            throw IllegalStateException("The stash clashes with your current changes; commit or roll them back first")
        }
        git.stashDrop().setStashRef(index).call()
    }

    fun dropStash(index: Int) {
        git.stashDrop().setStashRef(index).call()
    }

    /** Local branches, sorted, by short name. */
    fun branches(): List<String> =
        git.branchList().call().map { Repository.shortenRefName(it.name) }.sortedBy { it.lowercase() }

    /**
     * Switch to branch [name]. Like `git checkout`, uncommitted edits to files that don't differ
     * between the two branches come along; if one would be overwritten, JGit refuses and says which.
     */
    fun checkout(name: String) {
        git.checkout().setName(name).call()
    }

    /** New branch [name] from the current commit, and switch to it (`git switch -c`). */
    fun createBranch(name: String) {
        require(headId() != null) { "Make the first commit before creating branches" }
        require(Repository.isValidRefName(Constants.R_HEADS + name)) { "\"$name\" isn't a valid branch name" }
        require(name !in branches()) { "Branch \"$name\" already exists" }
        git.checkout().setCreateBranch(true).setName(name).call()
    }

    /** Deletes [name]; refuses the current branch and (like `git branch -d`) one not merged yet. */
    fun deleteBranch(name: String) {
        require(name != branch()) { "Can't delete the branch you are on" }
        try {
            git.branchDelete().setBranchNames(Constants.R_HEADS + name).setForce(false).call()
        } catch (e: org.eclipse.jgit.api.errors.NotMergedException) {
            throw IllegalStateException("\"$name\" has commits that aren't merged into ${branch()}, so it was kept")
        }
    }

    override fun close() {
        git.close()
        repo.close()
    }

    companion object {
        /** The repository [dir] belongs to (itself or a parent), or null if it isn't in one. */
        fun find(dir: File): GitRepo? = runCatching {
            val b = FileRepositoryBuilder().readEnvironment().findGitDir(dir.absoluteFile)
            if (b.gitDir == null) return null
            val repo = b.setMustExist(true).build()
            if (repo.isBare) { repo.close(); null } else GitRepo(repo)
        }.getOrNull()

        /** `git init` in [dir], on branch main; with [gitignore], a starter .gitignore if there's none. */
        fun init(dir: File, gitignore: Boolean = false): GitRepo {
            val g = Git.init().setDirectory(dir.absoluteFile).setInitialBranch("main").call()
            val ig = File(dir, ".gitignore")
            if (gitignore && !ig.exists()) ig.writeText(DEFAULT_GITIGNORE)
            return GitRepo(g.repository)
        }

        /** Litter that never belongs in a diagrams repo; exports are left for you to decide. */
        const val DEFAULT_GITIGNORE =
            "# macOS\n.DS_Store\n\n# SproutStudio's temp file while saving\n.*.sprout-tmp\n\n# IDEs\n.idea/\n.vscode/\n"

    }
}
