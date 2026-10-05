package es.hugoalvarezajenjo.sproutstudio.services.git

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
}
