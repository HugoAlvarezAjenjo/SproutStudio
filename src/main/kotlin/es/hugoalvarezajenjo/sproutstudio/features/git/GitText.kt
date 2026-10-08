package es.hugoalvarezajenjo.sproutstudio.features.git

import es.hugoalvarezajenjo.sproutstudio.services.git.GitError

/*
 * The words for git results. The git service and ViewModel only say WHAT happened; all
 * user-facing git text lives here, ready to move to string resources.
 */

fun GitError.describe(): String = when (this) {
    GitError.EmptyMessage -> "Empty commit message"
    GitError.NoFilesSelected -> "No files selected"
    GitError.NoCommitYet -> "Make the first commit first"
    GitError.NothingToUndo -> "There is no commit to undo"
    GitError.FirstCommitCantBeUndone -> "The first commit can't be undone"
    GitError.NothingToStash -> "No local changes to stash"
    GitError.StashClashes -> "The stash clashes with your current changes; commit or roll them back first"
    is GitError.InvalidBranchName -> "\"$name\" isn't a valid branch name"
    is GitError.BranchExists -> "Branch \"$name\" already exists"
    GitError.DeleteCurrentBranch -> "Can't delete the branch you are on"
    is GitError.BranchNotMerged -> "\"$name\" has commits that aren't merged into ${into ?: "this branch"}, so it was kept"
    is GitError.CheckoutConflict -> "Your uncommitted changes would be overwritten: " + paths.joinToString()
    is GitError.Failed -> detail
}

private fun files(n: Int) = "$n file${if (n == 1) "" else "s"}"

fun GitNotice.describe(): String = when (this) {
    is GitNotice.Committed -> "Committed ${files(files)} ($id)"
    is GitNotice.Amended -> "Amended the last commit with ${files(files)} ($id)"
    is GitNotice.Reworded -> "Reworded the last commit ($id)"
    is GitNotice.Undid -> "Undid $short: its changes are back in the list"
}
