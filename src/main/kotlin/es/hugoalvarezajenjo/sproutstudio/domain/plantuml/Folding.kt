package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * A foldable block. Folding hides [hideStart, hideEnd): from the end of the opening line up to
 * the closing keyword, so `class Pizza {…}` and `alt ok … end` stay on one readable line.
 */
data class FoldRegion(
    val kind: Kind,
    /** 0-based lines of the opener and the closer. */
    val startLine: Int,
    val endLine: Int,
    val hideStart: Int,
    val hideEnd: Int,
) {
    enum class Kind { DIAGRAM, BRACES, BLOCK, NOTE, COMMENT }
    operator fun contains(offset: Int) = offset in (hideStart + 1) until hideEnd
}

object Folding {
    const val PLACEHOLDER = " … "

    private enum class Open { DIAGRAM, BRACE, END, BOX, NOTE, REF, LEGEND, HEADER, FOOTER, IF, WHILE, REPEAT, FORK, SPLIT, SWITCH, COMMENT }

    private val endBlocks = setOf("alt", "opt", "loop", "par", "par2", "break", "critical", "group")

    /** Every foldable region, ordered by start; regions with nothing inside are skipped. */
    fun regions(text: String): List<FoldRegion> {
        val starts = lineStarts(text)
        val out = ArrayList<FoldRegion>()
        val stack = ArrayList<Pair<Open, Int>>() // (kind, start line)

        fun close(kind: Open, endLine: Int) {
            val i = stack.indexOfLast { it.first == kind }
            if (i < 0) return // stray closer: ignore rather than unbalance everything
            val start = stack[i].second
            while (stack.size > i) stack.removeAt(stack.lastIndex)
            if (endLine - start < 2) return // nothing between opener and closer
            val hideStart = lineEnd(text, starts, start)
            val endLineStart = starts[endLine]
            var hideEnd = endLineStart
            while (hideEnd < text.length && (text[hideEnd] == ' ' || text[hideEnd] == '\t')) hideEnd++
            val k = when (kind) {
                Open.DIAGRAM -> FoldRegion.Kind.DIAGRAM
                Open.BRACE -> FoldRegion.Kind.BRACES
                Open.NOTE -> FoldRegion.Kind.NOTE
                Open.COMMENT -> FoldRegion.Kind.COMMENT
                else -> FoldRegion.Kind.BLOCK
            }
            out += FoldRegion(k, start, endLine, hideStart, hideEnd)
        }

        var inComment = false
        for (ln in starts.indices) {
            val raw = text.substring(starts[ln], lineEnd(text, starts, ln))
            val t = raw.trim()
            val lower = t.lowercase()
            // ── block comments /' … '/ ──
            if (inComment) {
                if (t.endsWith("'/")) { inComment = false; close(Open.COMMENT, ln) }
                continue
            }
            if (t.startsWith("/'")) {
                if (!t.endsWith("'/") || t.length < 4) { inComment = true; stack += Open.COMMENT to ln }
                continue
            }
            if (t.isEmpty() || t.startsWith("'")) continue
            val word = lower.takeWhile { !it.isWhitespace() && it != '(' && it != '{' && it != ':' }

            when {
                lower.startsWith("@start") -> stack += Open.DIAGRAM to ln
                lower.startsWith("@end") -> close(Open.DIAGRAM, ln)
                t.startsWith("}") -> close(Open.BRACE, ln)
                lower == "end" || (word == "end" && lower.removePrefix("end").isBlank()) -> close(Open.END, ln)
                lower.startsWith("end box") || lower == "endbox" -> close(Open.BOX, ln)
                Regex("^end\\s*[hr]?note").containsMatchIn(lower) -> close(Open.NOTE, ln)
                lower.startsWith("end ref") || lower == "endref" -> close(Open.REF, ln)
                lower.startsWith("endlegend") || lower.startsWith("end legend") -> close(Open.LEGEND, ln)
                lower.startsWith("endheader") || lower.startsWith("end header") -> close(Open.HEADER, ln)
                lower.startsWith("endfooter") || lower.startsWith("end footer") -> close(Open.FOOTER, ln)
                lower.startsWith("endif") || lower.startsWith("end if") -> close(Open.IF, ln)
                lower.startsWith("endwhile") || lower.startsWith("end while") -> close(Open.WHILE, ln)
                lower.startsWith("repeat while") || lower.startsWith("repeatwhile") -> close(Open.REPEAT, ln)
                lower.startsWith("end fork") || lower.startsWith("endfork") || lower.startsWith("end merge") -> close(Open.FORK, ln)
                lower.startsWith("end split") || lower.startsWith("endsplit") -> close(Open.SPLIT, ln)
                lower.startsWith("endswitch") || lower.startsWith("end switch") -> close(Open.SWITCH, ln)
                // ── openers ──
                t.endsWith("{") -> stack += Open.BRACE to ln
                word in endBlocks -> stack += Open.END to ln
                word == "box" -> stack += Open.BOX to ln
                (word == "note" || word == "hnote" || word == "rnote") && ':' !in t && '"' !in t -> stack += Open.NOTE to ln
                word == "ref" && ':' !in t -> stack += Open.REF to ln
                lower == "legend" || (word == "legend" && lower.split(Regex("\\s+")).size <= 2) -> stack += Open.LEGEND to ln
                lower == "header" -> stack += Open.HEADER to ln
                lower == "footer" -> stack += Open.FOOTER to ln
                word == "if" -> stack += Open.IF to ln
                word == "while" -> stack += Open.WHILE to ln
                word == "repeat" -> stack += Open.REPEAT to ln
                word == "fork" && !lower.startsWith("fork again") -> stack += Open.FORK to ln
                word == "split" && !lower.startsWith("split again") -> stack += Open.SPLIT to ln
                word == "switch" -> stack += Open.SWITCH to ln
            }
        }
        return out.sortedWith(compareBy({ it.hideStart }, { -it.hideEnd }))
    }

    /** Of the folded regions, the ones actually visible as a placeholder (not inside another). */
    fun topLevel(folded: List<FoldRegion>): List<FoldRegion> {
        val out = ArrayList<FoldRegion>()
        var end = -1
        for (r in folded.sortedBy { it.hideStart }) {
            if (r.hideStart < end) continue
            out += r
            end = r.hideEnd
        }
        return out
    }

    fun lineStarts(text: String): IntArray {
        val list = ArrayList<Int>()
        list += 0
        for (i in text.indices) if (text[i] == '\n') list += i + 1
        return list.toIntArray()
    }

    private fun lineEnd(text: String, starts: IntArray, line: Int) =
        if (line + 1 < starts.size) starts[line + 1] - 1 else text.length

    /** 0-based line of [offset]. */
    fun lineOf(starts: IntArray, offset: Int): Int {
        var i = java.util.Arrays.binarySearch(starts, offset)
        if (i < 0) i = -i - 2
        return i.coerceIn(0, starts.size - 1)
    }
}
