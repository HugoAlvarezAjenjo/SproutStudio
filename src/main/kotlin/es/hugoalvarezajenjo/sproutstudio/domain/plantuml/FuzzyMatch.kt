package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * How a completion label matches what the user typed, IntelliJ style.
 *
 * Tiers, best first:
 * - [PREFIX]: the label starts with the query (`skin` -> `skinparam`).
 * - [INITIALS]: each query chunk starts a word of the label. Words begin at an upper-case
 *   letter after a lower-case one, after a separator (space, `_`, `.`, `-`, `/`) or at a
 *   letter/digit boundary. A chunk may run on inside its word: `bc` and `bgCol` both reach
 *   `backgroundColor`, `lo` reaches `linetype ortho`, `c4c` reaches `C4/C4_Container`.
 * - [CONTAINS]: the query appears anywhere in the label (two characters or more).
 *
 * Matching ignores case. A shared leading sigil (`@`, `!`, `#`) is skipped, so `@sd` works
 * like `sd` against `@startditaa`-style labels.
 */
object FuzzyMatch {
    const val PREFIX = 0
    const val INITIALS = 1
    const val CONTAINS = 2

    /** A successful match: its [tier] and the label positions that matched, for highlighting. */
    data class Match(val tier: Int, val indices: List<Int>)

    /** The best match of [query] against [label], or null when it doesn't match at all. */
    fun match(label: String, query: String): Match? {
        if (query.isEmpty()) return Match(PREFIX, emptyList())
        val l = label.lowercase()
        val q = query.lowercase()
        if (l.startsWith(q)) return Match(PREFIX, q.indices.toList())
        // Initials and substrings need at least two characters, or one letter matches half the list.
        if (q.length < 2) return null

        val sigil = q[0].takeIf { it in SIGILS && l.startsWith(it) }
        val offset = if (sigil != null) 1 else 0
        initials(label.substring(offset), q.substring(offset))?.let { found ->
            val head = if (sigil != null) listOf(0) else emptyList()
            return Match(INITIALS, head + found.map { it + offset })
        }

        val at = l.indexOf(q)
        return if (at >= 0) Match(CONTAINS, (at until at + q.length).toList()) else null
    }

    private const val SIGILS = "@!#"

    /** Indices where words of [label] begin. */
    private fun wordStarts(label: String): List<Int> = label.indices.filter { i ->
        val c = label[i]
        if (!c.isLetterOrDigit()) return@filter false
        if (i == 0) return@filter true
        val p = label[i - 1]
        !p.isLetterOrDigit() || (c.isUpperCase() && !p.isUpperCase()) || (c.isDigit() != p.isDigit())
    }

    /**
     * Matches [q] (lower-case) as chunks, each one a prefix of a later word than the previous
     * chunk. Words may be skipped. Returns the matched label positions, or null.
     */
    private fun initials(label: String, q: String): List<Int>? {
        if (q.isEmpty()) return emptyList()
        val l = label.lowercase()
        val starts = wordStarts(label)
        val picked = ArrayList<Int>()

        fun go(qi: Int, fromWord: Int): Boolean {
            if (qi == q.length) return true
            for (w in fromWord until starts.size) {
                val s = starts[w]
                val end = if (w + 1 < starts.size) starts[w + 1] else label.length
                var n = 0
                while (qi + n < q.length && s + n < end && l[s + n] == q[qi + n]) n++
                // Longest chunk first, then shorter ones so the rest of the query can still fit.
                for (k in n downTo 1) {
                    val mark = picked.size
                    for (j in 0 until k) picked += s + j
                    if (go(qi + k, w + 1)) return true
                    while (picked.size > mark) picked.removeAt(picked.size - 1)
                }
            }
            return false
        }

        return if (go(0, 0)) picked.toList() else null
    }
}
