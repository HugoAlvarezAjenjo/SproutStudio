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

    /** Every foldable region, ordered by start; regions with nothing inside are skipped. */
    fun regions(text: String): List<FoldRegion> {
        val starts = TextLines.starts(text)
        val out = ArrayList<FoldRegion>()
        val stack = ArrayList<Pair<BlockKind, Int>>() // (kind, start line)

        fun close(kind: BlockKind, endLine: Int) {
            val i = stack.indexOfLast { it.first == kind }
            if (i < 0) return // stray closer: ignore rather than unbalance everything
            val start = stack[i].second
            while (stack.size > i) stack.removeAt(stack.lastIndex)
            if (endLine - start < 2) return // nothing between opener and closer
            val hideStart = TextLines.end(text, starts, start)
            var hideEnd = starts[endLine]
            while (hideEnd < text.length && (text[hideEnd] == ' ' || text[hideEnd] == '\t')) hideEnd++
            val k = when (kind) {
                BlockKind.DIAGRAM -> FoldRegion.Kind.DIAGRAM
                BlockKind.BRACE -> FoldRegion.Kind.BRACES
                BlockKind.NOTE -> FoldRegion.Kind.NOTE
                BlockKind.COMMENT -> FoldRegion.Kind.COMMENT
                else -> FoldRegion.Kind.BLOCK
            }
            out += FoldRegion(k, start, endLine, hideStart, hideEnd)
        }

        for (line in BlockSyntax.scan(text)) {
            when (val r = line.role) {
                is LineRole.Open -> stack += r.kind to line.index
                is LineRole.Close -> close(r.kind, line.index)
                else -> {}
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
}
