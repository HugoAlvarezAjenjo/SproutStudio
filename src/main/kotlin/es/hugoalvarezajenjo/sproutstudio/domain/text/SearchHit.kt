package es.hugoalvarezajenjo.sproutstudio.domain.text

/**
 * One match of a project search. [range] is in the file's text; [line] is 1-based. [preview] is
 * the match's line as the results list shows it (trimmed, shortened around the match when long)
 * and [previewMatch] the match inside it, for highlighting.
 */
data class SearchHit(val range: IntRange, val line: Int, val preview: String, val previewMatch: IntRange)
