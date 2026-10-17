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

    /** Which way the tab last moved: +1 towards the end, -1 towards the start, 0 not yet. */
    private var heading by mutableStateOf(0f)

    /** Each tab reports its width as it's laid out. */
    fun measured(tab: T, widthPx: Int) { widths[tab] = widthPx }

    fun forget(tab: T) { widths.remove(tab) }

    fun start(tab: T) { dragged = tab; offset = 0f; heading = 0f }

    fun dragBy(dx: Float) {
        val tab = dragged ?: return
        offset += dx
        if (dx != 0f) heading = sign(dx)
        while (offset != 0f) {
            val i = tabs().indexOf(tab)
            val next = if (offset > 0) i + 1 else i - 1
            val width = tabs().getOrNull(next)?.let { widths[it] } ?: return
            if (abs(offset) < width / 2f) return
            move(i, next)
            offset -= sign(offset) * width
        }
    }

    fun end() { dragged = null; offset = 0f; heading = 0f }

    /**
     * How far to scroll the strip this frame, in px (negative = back), when the strip is wider
     * than its window: the dragged tab is drawn [scroll] px into the strip's content, and the
     * window shows [viewport] px of it. Only the edge the tab is heading for scrolls, starting
     * [edge] px before it and speeding up to [maxStep] at the edge itself.
     *
     * The caller scrolls and then passes the scrolled amount to [dragBy]: the content moved under
     * a pointer that stayed put, so the tab moves along the strip by the same amount.
     */
    fun edgeScroll(scroll: Int, viewport: Int, edge: Float, maxStep: Float): Float {
        val tab = dragged ?: return 0f
        val all = tabs()
        val slot = all.subList(0, all.indexOf(tab).coerceAtLeast(0)).sumOf { widths[it] ?: 0 }
        val left = slot + offset - scroll
        val right = left + (widths[tab] ?: 0)
        val depth = when {
            heading > 0 -> right - (viewport - edge)
            heading < 0 -> edge - left
            else -> 0f
        }
        if (depth <= 0f) return 0f
        return heading * maxStep * (depth / edge).coerceAtMost(1f)
    }
}
