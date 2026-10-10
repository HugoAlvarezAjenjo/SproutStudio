package es.hugoalvarezajenjo.sproutstudio.domain.text

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SourceFile
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.TextLines

/**
 * Find in Files: the editor's [FindQuery] (case, whole words, regex) run over many files.
 * Pure: the caller hands in the texts, so it reads the open tabs' unsaved text, not the disk.
 */
object ProjectSearch {
    /** Stop past this many matches; the list is for reading, not for 50 000 rows. */
    const val MAX_HITS = 2000
    /** Longest preview line; a longer one is cut around the match. */
    const val PREVIEW_MAX = 120
    /** Characters kept before the match when a long line is cut. */
    private const val PREVIEW_LEAD = 40
    private const val ELLIPSIS = "…"

    fun search(files: Sequence<SourceFile>, q: FindQuery, maxHits: Int = MAX_HITS): ProjectSearchResult {
        if (q.text.isEmpty()) return ProjectSearchResult.Empty
        val out = ArrayList<FileHits>()
        var left = maxHits
        for (f in files) {
            val found = FindReplace.find(f.text, q)
            found.error?.let { return ProjectSearchResult(emptyList(), error = it) }
            if (found.matches.isEmpty()) continue
            val starts = TextLines.starts(f.text)
            val hits = found.matches.asSequence().take(left).map { hit(f.text, starts, it) }.toList()
            out += FileHits(f.path, hits)
            left -= hits.size
            if (left <= 0) return ProjectSearchResult(out, truncated = true)
        }
        return ProjectSearchResult(out)
    }

    private fun hit(text: String, starts: IntArray, range: IntRange): SearchHit {
        val line = TextLines.of(starts, range.first)
        var from = starts[line]
        val end = TextLines.end(text, starts, line)
        while (from < range.first && text[from].isWhitespace()) from++ // indentation isn't worth the room
        val matchEnd = minOf(range.last + 1, end) // a regex match can run past the line
        var prefix = ""
        var to = end
        if (to - from > PREVIEW_MAX) {
            if (range.first - from > PREVIEW_LEAD) { from = range.first - PREVIEW_LEAD; prefix = ELLIPSIS }
            to = minOf(end, maxOf(from + PREVIEW_MAX, matchEnd))
        }
        val suffix = if (to < end) ELLIPSIS else ""
        val preview = prefix + text.substring(from, to) + suffix
        val s = prefix.length + range.first - from
        return SearchHit(range, line + 1, preview, s until s + (matchEnd - range.first))
    }
}
