package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * One entry of the outline: a declared [symbol] and what it contains ([children]: a package's
 * classes, a node's components). [lastLine] is the 1-based line of the `}` that closes it, or
 * null when it opens no block.
 */
data class OutlineNode(val symbol: Symbol, val lastLine: Int? = null, val children: List<OutlineNode> = emptyList())

/**
 * One diagram's entries. [block] is its index as the preview counts diagrams, [kind] what
 * follows `@start` (`uml`, `mindmap`) and [title] its `title` line, if any; [line] is the
 * 1-based `@start` line. All three are null (and [line] 1) for the names outside any diagram:
 * procedures, macros, `$variables`, and what an include file declares without `@startuml`.
 */
data class OutlineSection(val block: Int?, val kind: String?, val title: String?, val line: Int, val nodes: List<OutlineNode>)

/**
 * What a PlantUML text declares, as a tree for the Outline panel: one section per diagram, the
 * symbols in text order, nested where a declaration opens a `{ }` block. Built on [SymbolIndex]
 * (the symbols) and [Folding] (the blocks), so it names the same symbols navigation does.
 */
class Outline private constructor(private val text: String, val sections: List<OutlineSection>) {

    val isEmpty: Boolean get() = sections.all { it.nodes.isEmpty() }

    /**
     * The entry to highlight for a caret at [offset]: the symbol under it (at any of its uses),
     * else the innermost entry whose block the caret is in, else null.
     */
    fun selectedAt(offset: Int): OutlineNode? {
        val index = SymbolIndex.of(text)
        index.occurrenceAt(offset)?.let { o ->
            val d = index.declarationOf(o)
            find { it.symbol.range == d.range }?.let { return it }
        }
        val line = TextLines.of(TextLines.starts(text), offset) + 1
        var best: OutlineNode? = null
        fun visit(nodes: List<OutlineNode>) {
            for (n in nodes) {
                val last = n.lastLine ?: continue
                if (line in n.symbol.line..last) { best = n; visit(n.children) }
            }
        }
        sections.forEach { visit(it.nodes) }
        return best
    }

    private fun find(pred: (OutlineNode) -> Boolean): OutlineNode? {
        fun search(nodes: List<OutlineNode>): OutlineNode? {
            for (n in nodes) { if (pred(n)) return n; search(n.children)?.let { return it } }
            return null
        }
        return sections.firstNotNullOfOrNull { search(it.nodes) }
    }

    companion object {
        private val startLine = Regex("""^@start(\w*)""")
        private val titleLine = Regex("""^\s*title\s+(.+?)\s*$""", RegexOption.IGNORE_CASE)

        fun of(text: String): Outline {
            val symbols = SymbolIndex.of(text).definitions
            // Where each `{` block ends, by the line that opens it (0-based, as Folding counts).
            val blockEnds = Folding.regions(text).filter { it.kind == FoldRegion.Kind.BRACES }.associate { it.startLine to it.endLine }
            val lines = text.split('\n')
            // SymbolIndex numbers its scopes by `@start` lines, 1 for the first; find each one's line.
            val scopeStarts = lines.indices.filter { startLine.containsMatchIn(lines[it].trim()) }
            val blocks = DiagramBlocks.of(text)

            val byScope = symbols.groupBy { if (it.scope <= 0) 0 else it.scope }
            val sections = byScope.toSortedMap().map { (scope, inScope) ->
                val nodes = nest(inScope, blockEnds)
                if (scope == 0) return@map OutlineSection(null, null, null, 1, nodes)
                val start = scopeStarts[scope - 1]
                val end = scopeStarts.getOrNull(scope) ?: lines.size
                OutlineSection(
                    block = blocks.indexOfFirst { it.startLine == start }.takeIf { it >= 0 },
                    kind = startLine.find(lines[start].trim())!!.groupValues[1],
                    title = (start + 1 until end).firstNotNullOfOrNull { titleLine.find(lines[it])?.groupValues?.get(1) },
                    line = start + 1,
                    nodes = nodes,
                )
            }
            return Outline(text, sections)
        }

        /** [symbols] in text order, each inside the closest declaration whose block holds its line. */
        private fun nest(symbols: List<Symbol>, blockEnds: Map<Int, Int>): List<OutlineNode> {
            class Open(val symbol: Symbol, val lastLine: Int?, val children: MutableList<OutlineNode> = ArrayList())
            val roots = ArrayList<OutlineNode>()
            val stack = ArrayList<Open>()

            fun close() {
                val o = stack.removeAt(stack.lastIndex)
                val node = OutlineNode(o.symbol, o.lastLine, o.children)
                (stack.lastOrNull()?.children ?: roots) += node
            }

            for (s in symbols) {
                while (stack.isNotEmpty() && s.line > stack.last().lastLine!!) close()
                val last = blockEnds[s.line - 1]?.let { it + 1 }
                if (last != null) stack += Open(s, last)
                else (stack.lastOrNull()?.children ?: roots) += OutlineNode(s)
            }
            while (stack.isNotEmpty()) close()
            return roots
        }
    }
}
