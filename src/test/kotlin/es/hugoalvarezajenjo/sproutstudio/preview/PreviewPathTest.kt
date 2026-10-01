package es.hugoalvarezajenjo.sproutstudio.preview

import es.hugoalvarezajenjo.sproutstudio.render.PlantUmlRenderer
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Bitmap
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the exact preview path: PlantUML high-DPI PNG -> Skia Image. */
class PreviewPathTest {

    init { System.setProperty("java.awt.headless", "true") }

    private val samples = File("samples")
    private val out = File("build/preview-snapshots").apply { mkdirs() }

    private fun snapshot(file: File, index: Int) = runBlocking {
        val r = PlantUmlRenderer.renderPreview(file.readText(), file.parentFile, index, 3.0)
        assertNull(r.error, "${file.name}[$index]: ${r.error}")
        val bytes = r.bytes!!
        val img = assertNotNull(DiagramImage.fromPng(bytes, r.scale), "PNG did not decode")
        assertTrue(img.width > 10 && img.height > 10, "size ${img.width}x${img.height}")
        // Logical size is the 1x size: image pixels = 3 x logical.
        assertEquals(img.image.width.toFloat(), img.width * 3f, 3f)
        File(out, "${file.nameWithoutExtension}-$index.png").writeBytes(bytes)

        val bmp = Bitmap.makeFromImage(img.image)
        var nonWhite = 0
        for (y in 0 until bmp.height step 4) for (x in 0 until bmp.width step 4) {
            val c = bmp.getColor(x, y)
            if (c != -1 && (c ushr 24) != 0) nonWhite++
        }
        assertTrue(nonWhite > 50, "rendered image looks blank")
    }

    @Test fun `pizza sequence renders`() = snapshot(File(samples, "pizza.puml"), 0)

    @Test
    fun `preview png is really rendered at 3x pixels`() = runBlocking {
        val src = "@startuml\nAlice -> Bob: hi\n@enduml\n"
        val one = PlantUmlRenderer.renderPreview(src, null, 0, 1.0)
        val three = PlantUmlRenderer.renderPreview(src, null, 0, 3.0)
        val w1 = org.jetbrains.skia.Image.makeFromEncoded(one.bytes!!).width
        val w3 = org.jetbrains.skia.Image.makeFromEncoded(three.bytes!!).width
        assertEquals(w1 * 3f, w3.toFloat(), w1 * 0.1f)
    }
    @Test fun `pizza class renders`() = snapshot(File(samples, "pizza.puml"), 1)
    @Test fun `morning activity renders`() = snapshot(File(samples, "morning.puml"), 0)
}
