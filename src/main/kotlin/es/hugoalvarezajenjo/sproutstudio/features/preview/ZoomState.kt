package es.hugoalvarezajenjo.sproutstudio.features.preview

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

@Stable
class ZoomState {
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var viewport by mutableStateOf(IntSize.Zero)
    /** True until the user zooms/pans manually; while true we keep the diagram fitted. */
    var autoFit by mutableStateOf(true)

    fun fit(w: Float, h: Float, padding: Float = 16f) {
        if (viewport.width == 0 || w <= 0f || h <= 0f) return
        val s = min((viewport.width - 2 * padding) / w, (viewport.height - 2 * padding) / h).coerceIn(0.05f, 1.5f)
        scale = s
        offset = Offset((viewport.width - w * s) / 2f, (viewport.height - h * s) / 2f)
        autoFit = true
    }

    fun actualSize(w: Float, h: Float) {
        scale = 1f
        offset = Offset(max(16f, (viewport.width - w) / 2f), max(16f, (viewport.height - h) / 2f))
        autoFit = false
    }

    fun zoomBy(factor: Float, pivot: Offset = Offset(viewport.width / 2f, viewport.height / 2f)) {
        val newScale = (scale * factor).coerceIn(0.05f, 16f)
        val f = newScale / scale
        offset = pivot - (pivot - offset) * f
        scale = newScale
        autoFit = false
    }

    fun panBy(delta: Offset) {
        offset += delta
        autoFit = false
    }
}

@Composable
fun rememberZoomState() = remember { ZoomState() }
