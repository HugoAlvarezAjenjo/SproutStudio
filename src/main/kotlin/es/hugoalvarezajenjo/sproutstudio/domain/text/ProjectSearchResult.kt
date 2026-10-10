package es.hugoalvarezajenjo.sproutstudio.domain.text

/**
 * What a project search found: matching files in the order searched, or why the pattern is
 * invalid. [truncated] when it stopped at [ProjectSearch.MAX_HITS], so the count is a minimum.
 */
data class ProjectSearchResult(
    val files: List<FileHits>,
    val truncated: Boolean = false,
    val error: String? = null,
) {
    val hitCount: Int get() = files.sumOf { it.hits.size }

    companion object { val Empty = ProjectSearchResult(emptyList()) }
}
