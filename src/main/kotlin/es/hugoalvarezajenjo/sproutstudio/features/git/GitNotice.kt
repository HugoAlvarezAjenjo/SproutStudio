package es.hugoalvarezajenjo.sproutstudio.features.git

/** What the last Commit-window action did, worded by the UI. */
sealed interface GitNotice {
    data class Committed(val files: Int, val id: String) : GitNotice
    data class Amended(val files: Int, val id: String) : GitNotice
    data class Reworded(val id: String) : GitNotice
    /** "Undo Last Commit": [short] is gone from the branch, its changes are back in the list. */
    data class Undid(val short: String) : GitNotice
}
