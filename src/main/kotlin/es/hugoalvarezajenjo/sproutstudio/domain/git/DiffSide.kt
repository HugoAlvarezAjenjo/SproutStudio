package es.hugoalvarezajenjo.sproutstudio.domain.git

/** One side of a comparison. */
sealed interface DiffSide {
    /** The last commit, whichever it is right now. */
    data object Head : DiffSide
    /** The editor's text (even unsaved), or the file on disk. */
    data object WorkingCopy : DiffSide
    /** The file as of commit [id]. */
    data class At(val id: String) : DiffSide
    /** The file just before commit [id] (its first parent). */
    data class Before(val id: String) : DiffSide
}
