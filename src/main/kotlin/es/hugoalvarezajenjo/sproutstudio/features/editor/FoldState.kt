package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Which regions are folded, kept on the Document. Folds are remembered by their [hideStart] and
 * shifted as the text is edited ([onEdit]); a fold whose start is edited away is dropped.
 */
@Stable
class FoldState {
    var folded by mutableStateOf<Set<Int>>(emptySet())
        private set

    private var cacheText: String? = null
    private var cache: List<FoldRegion> = emptyList()

    fun regions(text: String): List<FoldRegion> {
        if (text !== cacheText) { cache = Folding.regions(text); cacheText = text }
        return cache
    }

    /** Folded regions that still exist in [text]. */
    fun active(text: String): List<FoldRegion> =
        if (folded.isEmpty()) emptyList() else regions(text).filter { it.hideStart in folded }

    fun isFolded(r: FoldRegion) = r.hideStart in folded
    fun fold(r: FoldRegion) { folded = folded + r.hideStart }
    fun unfold(r: FoldRegion) { folded = folded - r.hideStart }
    fun toggle(r: FoldRegion) = if (isFolded(r)) unfold(r) else fold(r)
    fun set(rs: Collection<FoldRegion>) { folded = rs.mapTo(HashSet()) { it.hideStart } }
    fun clear() { folded = emptySet() }

    /** Keep folds attached to their text when [old] becomes [new]. */
    fun onEdit(old: String, new: String) {
        if (folded.isEmpty() || old == new) return
        val max = minOf(old.length, new.length)
        var p = 0
        while (p < max && old[p] == new[p]) p++
        var s = 0
        while (s < max - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
        val editEnd = old.length - s
        val delta = new.length - old.length
        folded = folded.mapNotNullTo(HashSet()) { k ->
            when {
                k < p -> k
                k >= editEnd -> k + delta
                else -> null // the opening line's end was edited: forget this fold
            }
        }
    }

    /**
     * Called for every new editor value. Arrow keys hop over a fold (caret from its start to its
     * end and back); a caret or selection end landing INSIDE a fold (click on `…`, find, error
     * jump) expands it, so nothing is ever edited unseen.
     */
    fun adjust(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        if (folded.isEmpty()) return new
        val active = active(new.text)
        if (active.isEmpty()) return new
        val sel = new.selection
        if (sel.collapsed && old.text == new.text && old.selection.collapsed) {
            val c = sel.start
            val hit = Folding.topLevel(active).firstOrNull { c in it }
            if (hit != null) {
                when (old.selection.start) {
                    hit.hideStart -> return new.copy(selection = TextRange(hit.hideEnd))
                    hit.hideEnd -> return new.copy(selection = TextRange(hit.hideStart))
                }
            }
        }
        val inside = active.filter { sel.start in it || sel.end in it }
        if (inside.isNotEmpty()) folded = folded - inside.map { it.hideStart }.toSet()
        return new
    }
}
