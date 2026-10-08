package es.hugoalvarezajenjo.sproutstudio.features.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
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
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import kotlin.math.exp

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DiagramView(
    image: DiagramImage,
    zoom: ZoomState,
    dimmed: Boolean = false,
    modifier: Modifier = Modifier,
    /** Size "fit to window" frames; the side-by-side compare fits both pictures alike. */
    fitWidth: Float = image.width,
    fitHeight: Float = image.height,
) {
    // Keep the diagram fitted while the user hasn't taken control of the view.
    LaunchedEffect(image, zoom.viewport, fitWidth, fitHeight) {
        if (zoom.autoFit) zoom.fit(fitWidth, fitHeight)
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
                            else -> {
                                last?.let { prev -> zoom.panBy(ch.position - prev); ch.consume() }
                                last = ch.position
                            }
                        }
                    }
                }
            }
            .pointerInput(image, fitWidth, fitHeight) { detectTapGestures(onDoubleTap = { zoom.fit(fitWidth, fitHeight) }) },
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
