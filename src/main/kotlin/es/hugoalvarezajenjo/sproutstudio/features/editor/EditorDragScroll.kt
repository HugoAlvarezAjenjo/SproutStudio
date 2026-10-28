package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * While a selection is dragged past the editor's edge, scroll towards the pointer every frame and
 * [extend] the selection to the text now under it. The field can't do it itself: it is as tall as
 * its text and sits inside the editor's scroll, so to the field the pointer is still over text.
 *
 * Positions are in the field's content coordinates (the modifier goes after its padding);
 * [contentOrigin] is where that content starts inside the scrolled area.
 */
internal fun Modifier.scrollWhileSelecting(
    vScroll: ScrollState,
    hScroll: ScrollState,
    contentOrigin: () -> Offset,
    viewportHeight: () -> Int,
    extend: (Offset) -> Unit,
): Modifier = pointerInput(vScroll, hScroll) {
    coroutineScope {
        val frames = this
        awaitPointerEventScope {
            while (true) {
                val press = awaitPointerEvent(PointerEventPass.Initial)
                // A press another handler took (a ⌘-click on a link) isn't a selection.
                if (press.type != PointerEventType.Press || !press.buttons.isPrimaryPressed) continue
                if (press.changes.any { it.isConsumed }) continue
                var pos = press.changes.first().position
                val ticker = frames.launch {
                    while (isActive) {
                        withFrameNanos { }
                        val origin = contentOrigin()
                        val dy = DragScroll.velocity(pos.y + origin.y - vScroll.value, viewportHeight().toFloat())
                        val dx = DragScroll.velocity(pos.x + origin.x - hScroll.value, hScroll.viewportSize.toFloat())
                        if (dx == 0f && dy == 0f) continue
                        // The text moves under a pointer that stays put: follow it in content coordinates.
                        val moved = Offset(hScroll.scrollBy(dx), vScroll.scrollBy(dy))
                        if (moved == Offset.Zero) continue
                        pos += moved
                        extend(pos)
                    }
                }
                do {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    e.changes.firstOrNull()?.let { pos = it.position }
                } while (e.changes.any { it.pressed })
                ticker.cancel()
            }
        }
    }
}

/** How fast a drag past an edge scrolls. */
internal object DragScroll {
    /** Speed grows with the distance past the edge, up to this many pixels a frame. */
    const val MAX_PX_PER_FRAME = 60f
    private const val PER_PX_PAST_EDGE = 0.5f

    /**
     * Pixels to scroll this frame for a pointer at [at] in a viewport of [size] pixels: negative
     * before its start, positive past its end, 0 inside (or when there is no viewport).
     */
    fun velocity(at: Float, size: Float): Float = when {
        size <= 0f -> 0f
        at < 0f -> -(-at * PER_PX_PAST_EDGE).coerceIn(1f, MAX_PX_PER_FRAME)
        at > size -> ((at - size) * PER_PX_PAST_EDGE).coerceIn(1f, MAX_PX_PER_FRAME)
        else -> 0f
    }
}
