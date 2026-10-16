package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.sign

/**
 * Dragging a tab along the tab bar. The tab follows the pointer by [offset]; once it's pulled
 * past half its neighbour's width the two swap places (through [move]) and [offset] drops by
 * that width, so the tab stays under the pointer.
 *
 * Generic over the tab type so the swap rule is tested without Compose.
 */
internal class TabDrag<T>(private val tabs: () -> List<T>, private val move: (from: Int, to: Int) -> Unit) {

    /** The tab being dragged; null when none is. */
    var dragged by mutableStateOf<T?>(null)
        private set

    /** How far, in px, the dragged tab is drawn from its slot. */
    var offset by mutableStateOf(0f)
        private set

    private val widths = mutableMapOf<T, Int>()

    /** Each tab reports its width as it's laid out. */
    fun measured(tab: T, widthPx: Int) { widths[tab] = widthPx }

    fun forget(tab: T) { widths.remove(tab) }

    fun start(tab: T) { dragged = tab; offset = 0f }

    fun dragBy(dx: Float) {
        val tab = dragged ?: return
        offset += dx
        while (offset != 0f) {
            val i = tabs().indexOf(tab)
            val next = if (offset > 0) i + 1 else i - 1
            val width = tabs().getOrNull(next)?.let { widths[it] } ?: return
            if (abs(offset) < width / 2f) return
            move(i, next)
            offset -= sign(offset) * width
        }
    }

    fun end() { dragged = null; offset = 0f }
}
