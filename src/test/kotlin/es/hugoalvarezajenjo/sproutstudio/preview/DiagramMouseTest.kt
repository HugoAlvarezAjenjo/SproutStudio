package es.hugoalvarezajenjo.sproutstudio.preview

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.render.PlantUmlRenderer
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Mouse gestures on the preview: wheel = zoom at the pointer, left or middle drag = pan. */
@OptIn(ExperimentalTestApi::class)
class DiagramMouseTest {
    init { System.setProperty("java.awt.headless", "true") }

    private val img = File("samples/pizza.puml").let { f ->
        val r = runBlocking { PlantUmlRenderer.renderPreview(f.readText(), f.parentFile, 0, 3.0) }
        DiagramImage.fromPng(r.bytes!!, r.scale)!!
    }

    private fun gesture(block: androidx.compose.ui.test.MouseInjectionScope.() -> Unit): ZoomState {
        val zoom = ZoomState().apply { scale = 1f; offset = Offset(10f, 10f); autoFit = false }
        runComposeUiTest {
            setContent { DiagramView(img, zoom, modifier = Modifier.size(600.dp, 400.dp)) }
            waitForIdle()
            // Keep autoFit off: the first layout would otherwise re-fit the diagram.
            zoom.scale = 1f; zoom.offset = Offset(10f, 10f); zoom.autoFit = false
            waitForIdle()
            onRoot().performMouseInput(block)
            waitForIdle()
        }
        return zoom
    }

    @Test
    fun wheelUpZoomsInAndDownZoomsOut() {
        assertTrue(gesture { moveTo(Offset(300f, 200f)); scroll(-3f, ScrollWheel.Vertical) }.scale > 1.05f)
        assertTrue(gesture { moveTo(Offset(300f, 200f)); scroll(3f, ScrollWheel.Vertical) }.scale < 0.95f)
    }

    @Test
    fun wheelZoomKeepsThePointUnderThePointerFixed() {
        val p = Offset(300f, 200f)
        val z = gesture { moveTo(p); scroll(-3f, ScrollWheel.Vertical) }
        // Diagram point under the pointer before: (p - 10) / 1. After: (p - offset) / scale.
        val before = p - Offset(10f, 10f)
        val after = (p - z.offset) / z.scale
        assertEquals(before.x, after.x, 0.5f)
        assertEquals(before.y, after.y, 0.5f)
    }

    @Test
    fun middleButtonDragPans() {
        val z = gesture {
            moveTo(Offset(300f, 200f)); press(MouseButton.Tertiary)
            moveBy(Offset(40f, 25f)); moveBy(Offset(40f, 25f))
            release(MouseButton.Tertiary)
        }
        assertEquals(1f, z.scale)
        assertEquals(Offset(90f, 60f), z.offset)
    }

    @Test
    fun leftButtonDragStillPans() {
        val z = gesture {
            moveTo(Offset(300f, 200f)); press(MouseButton.Primary)
            moveBy(Offset(-30f, 20f))
            release(MouseButton.Primary)
        }
        assertEquals(-20f, z.offset.x, 0.5f)
        assertEquals(30f, z.offset.y, 0.5f)
    }
}

