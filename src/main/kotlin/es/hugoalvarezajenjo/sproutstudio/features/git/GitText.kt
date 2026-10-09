package es.hugoalvarezajenjo.sproutstudio.features.git

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_branch_exists
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_checkout_conflict
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_delete_current
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_empty_message
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_first_commit
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_invalid_branch
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_no_commit_yet
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_no_files
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_not_merged
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_nothing_to_stash
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_nothing_to_undo
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_stash_clashes
import es.hugoalvarezajenjo.sproutstudio.resources.gerr_this_branch
import es.hugoalvarezajenjo.sproutstudio.resources.git_files
import es.hugoalvarezajenjo.sproutstudio.resources.gnote_amended
import es.hugoalvarezajenjo.sproutstudio.resources.gnote_committed
import es.hugoalvarezajenjo.sproutstudio.resources.gnote_reworded
import es.hugoalvarezajenjo.sproutstudio.resources.gnote_undid
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text

import es.hugoalvarezajenjo.sproutstudio.services.git.GitError

/*
 * The words for git results. The git service and ViewModel only say WHAT happened; all
 * user-facing git text lives here, translated through strings.xml.
 */

fun GitError.describe(): String = when (this) {
    GitError.EmptyMessage -> text(Res.string.gerr_empty_message)
    GitError.NoFilesSelected -> text(Res.string.gerr_no_files)
    GitError.NoCommitYet -> text(Res.string.gerr_no_commit_yet)
    GitError.NothingToUndo -> text(Res.string.gerr_nothing_to_undo)
    GitError.FirstCommitCantBeUndone -> text(Res.string.gerr_first_commit)
    GitError.NothingToStash -> text(Res.string.gerr_nothing_to_stash)
    GitError.StashClashes -> text(Res.string.gerr_stash_clashes)
    is GitError.InvalidBranchName -> text(Res.string.gerr_invalid_branch, name)
    is GitError.BranchExists -> text(Res.string.gerr_branch_exists, name)
    GitError.DeleteCurrentBranch -> text(Res.string.gerr_delete_current)
    is GitError.BranchNotMerged -> text(Res.string.gerr_not_merged, name, into ?: text(Res.string.gerr_this_branch))
    is GitError.CheckoutConflict -> text(Res.string.gerr_checkout_conflict, paths.joinToString())
    is GitError.Failed -> detail
}

private fun files(n: Int) = text(Res.plurals.git_files, n, n)

fun GitNotice.describe(): String = when (this) {
    is GitNotice.Committed -> text(Res.string.gnote_committed, files(files), id)
    is GitNotice.Amended -> text(Res.string.gnote_amended, files(files), id)
    is GitNotice.Reworded -> text(Res.string.gnote_reworded, id)
    is GitNotice.Undid -> text(Res.string.gnote_undid, short)
}
