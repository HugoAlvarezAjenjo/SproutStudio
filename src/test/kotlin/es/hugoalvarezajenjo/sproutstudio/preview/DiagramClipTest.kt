package es.hugoalvarezajenjo.sproutstudio.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.render.PlantUmlRenderer
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** A zoomed / panned diagram must stay inside its canvas and never paint over neighbours. */
class DiagramClipTest {

    init { System.setProperty("java.awt.headless", "true") }

    @Test
    fun zoomedDiagramDoesNotPaintOutsideItsCanvas() {
        val file = File("samples/pizza.puml")
        val r = runBlocking { PlantUmlRenderer.renderPreview(file.readText(), file.parentFile, 0, 3.0) }
        val img = DiagramImage.fromPng(r.bytes!!, r.scale)!!
        val neighbour = Color(0xFFFF00FF) // magenta "toolbar" above and "editor" left of the canvas

        // Zoomed 6x and panned up-left, like a user who zoomed in and dragged around.
        val zoom = ZoomState().apply { scale = 6f; offset = Offset(-400f, -400f); autoFit = false }

        val scene = ImageComposeScene(600, 500, Density(1f)) {
            Column(Modifier.fillMaxSize().background(neighbour)) {
                Box(Modifier.fillMaxWidth().height(100.dp))            // neighbour strip
                Box(Modifier.fillMaxSize()) { DiagramView(img, zoom) } // canvas: y >= 100
            }
        }
        val shot = scene.render(0)
        scene.close()
        val bmp = Bitmap.makeFromImage(shot)
        // Every pixel of the strip above the canvas must still be the neighbour's colour.
        for (y in 0 until 100 step 5) for (x in 0 until 600 step 10) {
            assertEquals(neighbour.toArgb(), bmp.getColor(x, y), "diagram leaked onto ($x,$y)")
        }
    }
}
