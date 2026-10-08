package es.hugoalvarezajenjo.sproutstudio.services.git

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.NotMergedException
import org.eclipse.jgit.api.errors.StashApplyFailureException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.UserConfig
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk
import java.io.File

/**
 * A git repository, read and written in-process with JGit. Only local operations. Not
 * thread-safe on its own: callers serialize access (see [GitRepository]).
 */
class JGitRepository internal constructor(private val repo: Repository) : GitRepository {
    private val git = Git(repo)
    override val workTree: File = repo.workTree.absoluteFile
    private val canonicalWorkTree: File = runCatching { workTree.canonicalFile }.getOrDefault(workTree)

    /** Canonical paths, so a symlinked folder (macOS /var -> /private/var) still matches. */
    override fun relPath(f: File): String? {
        val cf = runCatching { f.canonicalFile }.getOrDefault(f.absoluteFile.normalize())
        val rel = cf.relativeToOrNull(canonicalWorkTree) ?: return null
        val p = rel.invariantSeparatorsPath
        return if (p.startsWith("..")) null else p
    }

    override fun branch(): String? = runCatching {
        val full = repo.fullBranch ?: return null
        if (full.startsWith(Constants.R_HEADS)) Repository.shortenRefName(full)
        else repo.resolve(Constants.HEAD)?.abbreviate(7)?.name() // detached HEAD: show the commit
    }.getOrNull()

    override fun headId(): String? = repo.resolve(Constants.HEAD)?.name

    override fun status(scope: File?): GitStatus = jgit {
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
        GitStatus(branch(), headId(), changes, ignored)
    }

    override fun headText(f: File): String? = textAt(Constants.HEAD, f)

    override fun textAt(rev: String, f: File): String? {
        val path = relPath(f) ?: return null
        val tree = runCatching { repo.resolve("$rev^{tree}") }.getOrNull() ?: return null
        TreeWalk.forPath(repo, path, tree)?.use { tw ->
            return String(repo.open(tw.getObjectId(0)).bytes, Charsets.UTF_8)
        }
        return null
    }

    override fun author(): GitAuthor {
        val u = repo.config.get(UserConfig.KEY)
        return GitAuthor(u.authorName, u.authorEmail, implicit = u.isAuthorNameImplicit || u.isAuthorEmailImplicit)
    }

    /**
     * Commits exactly [files] (like IntelliJ's checkboxes, or `git commit --only`): anything else
     * already staged stays staged and out of this commit. New files are added, deleted ones removed.
     */
    override fun commit(files: List<File>, message: String, amend: Boolean): String = jgit {
        refuseIf(message.isBlank(), GitError.EmptyMessage)
        val paths = files.mapNotNull(::relPath).distinct()
        if (amend) {
            refuseIf(headId() == null, GitError.NoCommitYet)
            if (paths.isEmpty()) {
                // Amend with no files: just reword the last commit.
                return@jgit git.commit().setAmend(true).setMessage(message.trim() + "\n").call().id.abbreviate(7).name()
            }
        }
        refuseIf(paths.isEmpty(), GitError.NoFilesSelected)
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
        commit.call().id.abbreviate(7).name()
    }

    override fun rollback(f: File) = jgit {
        val path = relPath(f) ?: return@jgit
        val inHead = headText(f) != null
        if (inHead) {
            git.checkout().setStartPoint(Constants.HEAD).addPath(path).call()
        } else if (headId() != null) {
            git.reset().addPath(path).call()
        } else {
            git.rm().setCached(true).addFilepattern(path).call()
        }
        Unit
    }

    override fun lastCommit(): CommitInfo? {
        val id = repo.resolve(Constants.HEAD) ?: return null
        return RevWalk(repo).use { it.parseCommit(id).info() }
    }

    /** The commit disappears from the branch but its changes stay; nothing on disk changes. */
    override fun undoLastCommit(): CommitInfo = jgit {
        val last = lastCommit() ?: throw GitException(GitError.NothingToUndo)
        refuseIf(last.parents == 0, GitError.FirstCommitCantBeUndone)
        git.reset().setMode(ResetCommand.ResetType.SOFT).setRef("HEAD~1").call()
        last
    }

    /** Doesn't follow renames. */
    override fun history(f: File, max: Int): List<CommitInfo> = jgit {
        val path = relPath(f) ?: return@jgit emptyList()
        if (headId() == null) return@jgit emptyList()
        git.log().addPath(path).setMaxCount(max).call().map { it.info() }
    }

    // ── stash ──

    /** Puts the uncommitted changes to tracked files aside. */
    override fun stash(message: String?): Boolean = jgit {
        refuseIf(headId() == null, GitError.NoCommitYet)
        val cmd = git.stashCreate()
        if (!message.isNullOrBlank()) cmd.setWorkingDirectoryMessage(message.trim())
        cmd.call() != null
    }

    override fun stashes(): List<StashInfo> = jgit {
        git.stashList().call().mapIndexed { i, c -> StashInfo(i, c.shortMessage, c.authorIdent.whenAsInstant.toEpochMilli()) }
    }

    /** `git stash pop`: re-applies stash [index] and drops it (kept if applying fails). */
    override fun popStash(index: Int) = jgit {
        try {
            git.stashApply().setStashRef("stash@{$index}").call()
        } catch (e: StashApplyFailureException) {
            throw GitException(GitError.StashClashes)
        }
        git.stashDrop().setStashRef(index).call()
        Unit
    }

    override fun dropStash(index: Int) = jgit { git.stashDrop().setStashRef(index).call(); Unit }

    /** Local branches, sorted, by short name. */
    override fun branches(): List<String> = jgit {
        git.branchList().call().map { Repository.shortenRefName(it.name) }.sortedBy { it.lowercase() }
    }

    /**
     * Like `git checkout`, uncommitted edits to files that don't differ between the two branches
     * come along; if one would be overwritten, it's refused with [GitError.CheckoutConflict].
     */
    override fun checkout(name: String) = jgit { git.checkout().setName(name).call(); Unit }

    /** New branch [name] from the current commit, and switch to it (`git switch -c`). */
    override fun createBranch(name: String) = jgit {
        refuseIf(headId() == null, GitError.NoCommitYet)
        refuseIf(!Repository.isValidRefName(Constants.R_HEADS + name), GitError.InvalidBranchName(name))
        refuseIf(name in branches(), GitError.BranchExists(name))
        git.checkout().setCreateBranch(true).setName(name).call()
        Unit
    }

    /** Refuses the current branch and (like `git branch -d`) one not merged yet. */
    override fun deleteBranch(name: String) = jgit {
        refuseIf(name == branch(), GitError.DeleteCurrentBranch)
        try {
            git.branchDelete().setBranchNames(Constants.R_HEADS + name).setForce(false).call()
        } catch (e: NotMergedException) {
            throw GitException(GitError.BranchNotMerged(name, branch()))
        }
        Unit
    }

    override fun close() {
        git.close()
        repo.close()
    }

    private fun RevCommit.info() = CommitInfo(
        name, shortMessage, fullMessage.trimEnd(), authorIdent.name, authorIdent.whenAsInstant.toEpochMilli(), parentCount,
    )
}

private fun refuseIf(condition: Boolean, error: GitError) {
    if (condition) throw GitException(error)
}

/** Runs a JGit call, turning its exceptions into [GitException]s the app can tell apart. */
internal inline fun <T> jgit(block: () -> T): T = try {
    block()
} catch (e: GitException) {
    throw e
} catch (e: CheckoutConflictException) {
    throw GitException(GitError.CheckoutConflict(e.conflictingPaths))
} catch (e: Exception) {
    throw GitException(GitError.Failed(e.message ?: e.javaClass.simpleName))
}
