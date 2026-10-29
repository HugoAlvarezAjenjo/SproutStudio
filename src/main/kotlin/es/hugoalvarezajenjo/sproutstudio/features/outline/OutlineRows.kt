package es.hugoalvarezajenjo.sproutstudio.features.outline

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Outline
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.OutlineNode
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.OutlineSection

/** One line of the Outline panel: a diagram's header, or an entry indented by [depth]. */
internal sealed interface OutlineRow {
    data class Header(val section: OutlineSection) : OutlineRow
    data class Entry(val node: OutlineNode, val depth: Int, val collapsed: Boolean) : OutlineRow
}

/**
 * Stays the same while the user types above it, unlike the symbol's offset, so a closed
 * package stays closed. Name + scope is what makes a symbol unique (see `SymbolIndex`).
 */
internal val OutlineNode.key: String get() = "${symbol.scope}:${symbol.name}"

/**
 * The rows to show: headers only when the file has more than one section, and nothing under a
 * [collapsed] entry.
 */
internal fun outlineRows(outline: Outline, collapsed: Set<String>): List<OutlineRow> = buildList {
    val headers = outline.sections.size > 1
    fun add(nodes: List<OutlineNode>, depth: Int) {
        for (n in nodes) {
            val closed = n.key in collapsed
            add(OutlineRow.Entry(n, depth, closed))
            if (!closed) add(n.children, depth + 1)
        }
    }
    for (s in outline.sections) {
        if (headers) add(OutlineRow.Header(s))
        add(s.nodes, 0)
    }
}

/** Every entry that has something under it: what Collapse All closes. */
internal fun Outline.containerKeys(): Set<String> = buildSet {
    fun visit(nodes: List<OutlineNode>) {
        for (n in nodes) if (n.children.isNotEmpty()) { add(n.key); visit(n.children) }
    }
    sections.forEach { visit(it.nodes) }
}
