package es.hugoalvarezajenjo.sproutstudio.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * A rendered diagram. [image] is drawn by PlantUML at a high pixel scale so it stays crisp on
 * Retina and when zooming in; [width]/[height] are the diagram's natural (1x) size.
 * [background] is the diagram's own paper colour, used to paint the whole preview canvas.
 *
 * Why not SVG? Skia's SVG renderer has no font manager on desktop and silently drops all text.
 */
class DiagramImage(val image: Image, val width: Float, val height: Float, val background: Color) {
    companion object {
        fun fromPng(bytes: ByteArray, scale: Double): DiagramImage? = runCatching {
            val img = Image.makeFromEncoded(bytes)
            DiagramImage(img, (img.width / scale).toFloat(), (img.height / scale).toFloat(), sampleBackground(img))
        }.getOrNull()

        /** PlantUML's `backgroundColor`, read from a corner pixel. Transparent paper reads as white. */
        private fun sampleBackground(img: Image): Color = runCatching {
            val bmp = Bitmap.makeFromImage(img)
            val c = Color(bmp.getColor(1, 1))
            bmp.close()
            if (c.alpha < 0.5f) Color.White else c.copy(alpha = 1f)
        }.getOrDefault(Color.White)
    }
}

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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DiagramView(image: DiagramImage, zoom: ZoomState, dimmed: Boolean = false, modifier: Modifier = Modifier) {
    // Keep the diagram fitted while the user hasn't taken control of the view.
    LaunchedEffect(image, zoom.viewport) {
        if (zoom.autoFit) zoom.fit(image.width, image.height)
    }

    Canvas(
        modifier
            .fillMaxSize()
            // Compose doesn't clip drawing to a Canvas' bounds: without this a zoomed or panned
            // diagram paints over the toolbar, the editor and the status bar.
            .clipToBounds()
            .onSizeChanged { zoom.viewport = it }
            // Wheel = zoom at the pointer (like image viewers). A sideways scroll (trackpad, tilt
            // wheel) or ⇧+wheel pans instead, so a trackpad can still move around.
            .onPointerEvent(PointerEventType.Scroll) { ev ->
                val change = ev.changes.first()
                val d = change.scrollDelta
                val mods = ev.keyboardModifiers
                when {
                    mods.isShiftPressed -> zoom.panBy(Offset(-(d.x + d.y) * 24f, 0f))
                    d.x != 0f && !mods.isMetaPressed && !mods.isCtrlPressed -> zoom.panBy(Offset(-d.x * 24f, -d.y * 24f))
                    else -> zoom.zoomBy(exp(-d.y * 0.12f), change.position)
                }
                change.consume()
            }
            // Drag with the left OR the middle button (wheel pressed) pans.
            .pointerInput(image) {
                awaitPointerEventScope {
                    var last: Offset? = null
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.first()
                        val panning = ev.buttons.isPrimaryPressed || ev.buttons.isTertiaryPressed
                        when {
                            !panning -> last = null
                            last == null -> last = ch.position
                            else -> {
                                zoom.panBy(ch.position - last!!)
                                last = ch.position
                                ch.consume()
                            }
                        }
                    }
                }
            }
            .pointerInput(image) { detectTapGestures(onDoubleTap = { zoom.fit(image.width, image.height) }) },
    ) {
        // The canvas IS the diagram's paper: no frame, no shadow, no wasted margin around a card.
        drawRect(image.background)
        val w = image.width * zoom.scale
        val h = image.height * zoom.scale
        translate(zoom.offset.x, zoom.offset.y) {
            drawIntoCanvas { c ->
                c.skiaCanvas.drawImageRect(
                    image.image,
                    Rect.makeWH(image.image.width.toFloat(), image.image.height.toFloat()),
                    Rect.makeWH(w, h),
                    SamplingMode.MITCHELL,
                    null,
                    true,
                )
            }
        }
        // Stale picture while the text has an error: fade it into the paper.
        if (dimmed) drawRect(image.background.copy(alpha = 0.6f))
    }
}

@Composable
fun rememberZoomState() = remember { ZoomState() }
