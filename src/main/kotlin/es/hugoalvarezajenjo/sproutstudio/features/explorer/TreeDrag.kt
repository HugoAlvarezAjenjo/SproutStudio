package es.hugoalvarezajenjo.sproutstudio.features.explorer

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import es.hugoalvarezajenjo.sproutstudio.features.explorer.canMove
import java.io.File

/**
 * Drag & drop inside the project tree. Rows report their window bounds; while a row is dragged,
 * the folder under the pointer (a folder row, the folder of a file row, or the project folder for
 * its own row and the empty space) is the drop target, if the move would be valid.
 */
@Stable
internal class TreeDrag {
    var source by mutableStateOf<File?>(null)
        private set
    /** Pointer position in window coordinates, while dragging. */
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var target by mutableStateOf<File?>(null)
        private set

    /** Drop zones: window bounds -> the folder a drop there goes into. */
    private val zones = HashMap<Any, Pair<Rect, File>>()

    fun zone(key: Any, bounds: Rect, folder: File) { zones[key] = bounds to folder }
    fun forget(key: Any) { zones.remove(key) }

    fun start(file: File, at: Offset) { source = file; pointer = at; update() }
    fun moveTo(at: Offset) { pointer = at; update() }

    /** Ends the drag; returns (what, where) when it should be moved. */
    fun drop(): Pair<File, File>? {
        val r = source?.let { s -> target?.let { s to it } }
        cancel()
        return r
    }

    fun cancel() { source = null; target = null }

    private fun update() {
        val s = source ?: return
        // The smallest zone under the pointer wins: a row sits on top of the empty-space zone.
        val hit = zones.values.filter { it.first.contains(pointer) }.minByOrNull { it.first.width * it.first.height }?.second
        target = hit?.takeIf { canMove(s, it) }
    }
}

/**
 * Makes a tree row draggable and a drop zone. [folder] is where a drop on this row goes;
 * [file] is what dragging this row moves (null: the row can't be dragged, e.g. the project folder).
 */
internal fun Modifier.treeDrag(drag: TreeDrag, key: Any, folder: File, file: File?, onDrop: (File, File) -> Unit): Modifier {
    var coords: LayoutCoordinates? = null
    var m = this.onGloballyPositioned { c -> coords = c; drag.zone(key, c.boundsInWindow(), folder) }
    if (file != null) m = m.pointerInput(file) {
        detectDragGestures(
            onDragStart = { at -> coords?.let { drag.start(file, it.localToWindow(at)) } },
            onDrag = { change, _ -> change.consume(); coords?.let { drag.moveTo(it.localToWindow(change.position)) } },
            onDragEnd = { drag.drop()?.let { (what, where) -> onDrop(what, where) } },
            onDragCancel = { drag.cancel() },
        )
    }
    return m
}
