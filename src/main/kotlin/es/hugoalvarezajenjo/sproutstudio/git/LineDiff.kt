package es.hugoalvarezajenjo.sproutstudio.git

import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.HistogramDiff
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator

/**
 * A gutter marker, in lines of the CURRENT text (0-based). [ADDED]/[MODIFIED] cover
 * [start, end); [DELETED] lines were removed just above line [start] (so start == end).
 */
data class LineChange(val type: Type, val start: Int, val end: Int) {
    enum class Type { ADDED, MODIFIED, DELETED }
}

object LineDiff {
    /** What changed from [base] (the HEAD version) to [current], as IntelliJ's gutter shows it. */
    fun compute(base: String, current: String): List<LineChange> {
        if (base == current) return emptyList()
        val a = RawText(base.toByteArray(Charsets.UTF_8))
        val b = RawText(current.toByteArray(Charsets.UTF_8))
        return HistogramDiff().diff(RawTextComparator.DEFAULT, a, b).map { e ->
            when (e.type) {
                Edit.Type.INSERT -> LineChange(LineChange.Type.ADDED, e.beginB, e.endB)
                Edit.Type.DELETE -> LineChange(LineChange.Type.DELETED, e.beginB, e.beginB)
                else -> LineChange(LineChange.Type.MODIFIED, e.beginB, e.endB)
            }
        }
    }

    /** Marker for each line that has one, plus deletions keyed by the line below them. */
    class Index(changes: List<LineChange>) {
        private val byLine = HashMap<Int, LineChange.Type>()
        private val deletedAbove = HashSet<Int>()

        init {
            for (c in changes) {
                if (c.type == LineChange.Type.DELETED) deletedAbove += c.start
                else for (l in c.start until c.end) byLine[l] = c.type
            }
        }

        fun at(line: Int): LineChange.Type? = byLine[line]
        fun deletedAbove(line: Int) = line in deletedAbove
        /** First change in [from, to] (a folded line shows what's hidden inside it). */
        fun inRange(from: Int, to: Int): LineChange.Type? {
            var found: LineChange.Type? = null
            for (l in from..to) {
                val t = byLine[l] ?: continue
                if (t == LineChange.Type.MODIFIED) return t
                found = t
            }
            return found ?: if ((from + 1..to).any { it in deletedAbove }) LineChange.Type.MODIFIED else null
        }

        companion object { val Empty = Index(emptyList()) }
    }
}
