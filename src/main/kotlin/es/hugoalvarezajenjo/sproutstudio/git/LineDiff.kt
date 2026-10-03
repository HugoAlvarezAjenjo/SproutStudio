package es.hugoalvarezajenjo.sproutstudio.git

import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.HistogramDiff
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator

/**
 * A gutter marker, in lines of the CURRENT text (0-based). [ADDED]/[MODIFIED] cover
 * [start, end); [DELETED] lines were removed just above line [start] (so start == end).
 * [baseStart, baseEnd) are the lines this block replaced in the committed version.
 */
data class LineChange(val type: Type, val start: Int, val end: Int, val baseStart: Int = 0, val baseEnd: Int = 0) {
    enum class Type { ADDED, MODIFIED, DELETED }
}

object LineDiff {
    /** One row of a unified text diff. Line numbers are 1-based; null on the side that lacks it. */
    data class Row(val kind: Kind, val oldLine: Int?, val newLine: Int?, val text: String) {
        enum class Kind { SAME, ADDED, DELETED }
    }

    /**
     * Whole-file unified diff of [base] -> [current]: unchanged lines, then for each change the
     * removed lines followed by the added ones (like IntelliJ's unified viewer).
     */
    fun rows(base: String, current: String): List<Row> {
        val a = RawText(base.toByteArray(Charsets.UTF_8))
        val b = RawText(current.toByteArray(Charsets.UTF_8))
        val edits = HistogramDiff().diff(RawTextComparator.DEFAULT, a, b)
        val out = ArrayList<Row>()
        var ia = 0
        var ib = 0
        fun same(untilB: Int) {
            while (ib < untilB) { out += Row(Row.Kind.SAME, ia + 1, ib + 1, b.getString(ib)); ia++; ib++ }
        }
        for (e in edits) {
            same(e.beginB)
            for (i in e.beginA until e.endA) out += Row(Row.Kind.DELETED, i + 1, null, a.getString(i))
            for (i in e.beginB until e.endB) out += Row(Row.Kind.ADDED, null, i + 1, b.getString(i))
            ia = e.endA; ib = e.endB
        }
        same(b.size())
        return out
    }

    /** What changed from [base] (the HEAD version) to [current], as IntelliJ's gutter shows it. */
    fun compute(base: String, current: String): List<LineChange> {
        if (base == current) return emptyList()
        val a = RawText(base.toByteArray(Charsets.UTF_8))
        val b = RawText(current.toByteArray(Charsets.UTF_8))
        return HistogramDiff().diff(RawTextComparator.DEFAULT, a, b).map { e ->
            when (e.type) {
                Edit.Type.INSERT -> LineChange(LineChange.Type.ADDED, e.beginB, e.endB, e.beginA, e.endA)
                Edit.Type.DELETE -> LineChange(LineChange.Type.DELETED, e.beginB, e.beginB, e.beginA, e.endA)
                else -> LineChange(LineChange.Type.MODIFIED, e.beginB, e.endB, e.beginA, e.endA)
            }
        }
    }

    /** Char offset where 0-based [line] starts; past the last line = end of text. */
    private fun lineOffset(text: String, line: Int): Int {
        var l = 0
        var i = 0
        while (l < line) {
            val nl = text.indexOf('\n', i)
            if (nl < 0) return text.length
            i = nl + 1; l++
        }
        return i
    }

    /** The committed lines [change] replaced, as text (empty for pure additions). */
    fun baseText(base: String, change: LineChange): String =
        base.substring(lineOffset(base, change.baseStart), lineOffset(base, change.baseEnd))

    /**
     * One line of a single block's diff, for the gutter popup. [changed] is the part of the line
     * that differs from its counterpart on the other side (null: no finer highlight).
     */
    data class HunkRow(val kind: Row.Kind, val text: String, val changed: IntRange? = null)

    /**
     * The diff of [change] alone: its committed lines (DELETED rows) then its current ones (ADDED
     * rows). Modified lines are paired in order and get the differing middle part highlighted,
     * like IntelliJ's word-level marks.
     */
    fun hunkRows(base: String, current: String, change: LineChange): List<HunkRow> {
        fun lines(text: String, from: Int, to: Int): List<String> =
            if (to <= from) emptyList()
            else text.substring(lineOffset(text, from), lineOffset(text, to)).removeSuffix("\n").split('\n')
        val old = lines(base, change.baseStart, change.baseEnd)
        val new = lines(current, change.start, change.end)
        val oldHi = arrayOfNulls<IntRange>(old.size)
        val newHi = arrayOfNulls<IntRange>(new.size)
        for (i in 0 until minOf(old.size, new.size)) {
            val a = old[i]; val b = new[i]
            var p = 0
            while (p < a.length && p < b.length && a[p] == b[p]) p++
            var s = 0
            while (s < a.length - p && s < b.length - p && a[a.length - 1 - s] == b[b.length - 1 - s]) s++
            if (p == 0 && s == 0) continue // nothing in common: the whole line is the change
            if (a.length - s > p) oldHi[i] = p until a.length - s
            if (b.length - s > p) newHi[i] = p until b.length - s
        }
        return old.mapIndexed { i, t -> HunkRow(Row.Kind.DELETED, t, oldHi[i]) } +
            new.mapIndexed { i, t -> HunkRow(Row.Kind.ADDED, t, newHi[i]) }
    }

    /** Result of rolling one block back: the new text and where to put the caret. */
    data class Reverted(val text: String, val caret: Int)

    /**
     * Puts the committed version of one block back into [current] (IntelliJ's "Rollback Lines"):
     * added lines go away, deleted ones return, modified ones get their old text.
     */
    fun revert(base: String, current: String, change: LineChange): Reverted {
        val from = lineOffset(current, change.start)
        val to = lineOffset(current, change.end)
        val aFrom = lineOffset(base, change.baseStart)
        val aTo = lineOffset(base, change.baseEnd)
        var prefix = current.substring(0, from)
        val restored = base.substring(aFrom, aTo)
        val suffix = current.substring(to)
        // End of file: keep the committed version's "no newline at the end" as it was.
        if (suffix.isEmpty() && aTo == base.length) {
            if (restored.isEmpty() && !base.endsWith('\n') && prefix.endsWith('\n')) prefix = prefix.dropLast(1)
        } else if (suffix.isNotEmpty() && restored.isNotEmpty() && !restored.endsWith('\n')) {
            // A last base line without newline lands before more text: separate them.
            return Reverted(prefix + restored + "\n" + suffix, prefix.length)
        } else if (suffix.isEmpty() && restored.isNotEmpty() && !restored.endsWith('\n') && current.endsWith('\n')) {
            return Reverted(prefix + restored + "\n", prefix.length)
        }
        return Reverted(prefix + restored + suffix, prefix.length)
    }

    /** Marker for each line that has one, plus deletions keyed by the line below them. */
    class Index(val changes: List<LineChange>, /** The committed text the markers compare against. */ val base: String? = null) {
        private val byLine = HashMap<Int, LineChange.Type>()
        private val deletedAbove = HashSet<Int>()

        init {
            for (c in changes) {
                if (c.type == LineChange.Type.DELETED) deletedAbove += c.start
                else for (l in c.start until c.end) byLine[l] = c.type
            }
        }

        fun at(line: Int): LineChange.Type? = byLine[line]

        /**
         * The block a click on [line] means: one covering it, or a deletion marked at its top edge
         * (or, with [orBelow], at the bottom edge, i.e. above the next line).
         */
        fun changeAt(line: Int, orBelow: Boolean = false): LineChange? =
            changes.firstOrNull { it.type != LineChange.Type.DELETED && line in it.start until it.end }
                ?: changes.firstOrNull { it.type == LineChange.Type.DELETED && (it.start == line || (orBelow && it.start == line + 1)) }

        /** Block for the caret on [line]: like [changeAt], also catching a deletion just below. */
        fun changeForCaret(line: Int): LineChange? = changeAt(line) ?: changeAt(line, orBelow = true)
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
