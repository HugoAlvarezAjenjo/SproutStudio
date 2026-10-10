package es.hugoalvarezajenjo.sproutstudio.domain.text

/** The matches of a project search in one file, [path] being absolute. */
data class FileHits(val path: String, val hits: List<SearchHit>)
