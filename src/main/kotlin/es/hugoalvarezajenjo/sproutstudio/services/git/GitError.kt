package es.hugoalvarezajenjo.sproutstudio.services.git

/**
 * Why a git operation was refused or failed. The service says WHAT went wrong; the UI picks
 * the words (and can tell a checkout conflict from anything else without parsing a message).
 */
sealed interface GitError {
    data object EmptyMessage : GitError
    data object NoFilesSelected : GitError
    /** Amend, stash, branches... need a first commit. */
    data object NoCommitYet : GitError
    data object NothingToUndo : GitError
    data object FirstCommitCantBeUndone : GitError
    data object NothingToStash : GitError
    data object StashClashes : GitError
    data class InvalidBranchName(val name: String) : GitError
    data class BranchExists(val name: String) : GitError
    data object DeleteCurrentBranch : GitError
    data class BranchNotMerged(val name: String, val into: String?) : GitError
    /** Switching branch would overwrite uncommitted changes to [paths]. */
    data class CheckoutConflict(val paths: List<String>) : GitError
    /** Anything else; [detail] is git's own message. */
    data class Failed(val detail: String) : GitError
}
