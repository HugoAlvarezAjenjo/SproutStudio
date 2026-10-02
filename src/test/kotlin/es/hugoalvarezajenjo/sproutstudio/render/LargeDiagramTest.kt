package es.hugoalvarezajenjo.sproutstudio.render

import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Big diagrams must come out whole: PlantUML crops PNGs to 4096 px unless told otherwise. */
class LargeDiagramTest {
    private val wide = buildString {
        appendLine("@startuml")
        for (i in 1..30) appendLine("participant \"Service number $i\" as S$i")
        for (i in 1..29) appendLine("S$i -> S${i + 1}: call $i")
        appendLine("@enduml")
    }

    /** Natural (1x) size, read from the SVG's viewBox-free width/height attributes. */
    private fun svgSize(svg: String): Pair<Int, Int> {
        val w = Regex("width:(\\d+)px").find(svg)!!.groupValues[1].toInt()
        val h = Regex("height:(\\d+)px").find(svg)!!.groupValues[1].toInt()
        return w to h
    }

    @Test
    fun previewOfAWideDiagramIsNotCropped() = runBlocking {
        val (w, _) = svgSize(String(PlantUmlRenderer.render(wide, null).bytes!!))
        assertTrue(w * 3 > 4096, "test diagram must exceed the default limit at 3x (w=$w)")
        val r = PlantUmlRenderer.renderPreview(wide, null, 0, 3.0)
        val img = Image.makeFromEncoded(r.bytes!!)
        assertEquals(w * 3.0, img.width.toDouble(), w * 0.05 * 3, "preview PNG width ${img.width} vs ${w * 3}")
    }

    @Test
    fun hugeDiagramPreviewDropsScaleInsteadOfCropping() = runBlocking {
        val huge = buildString {
            appendLine("@startuml")
            for (i in 1..70) appendLine("participant \"Service number $i\" as S$i")
            for (i in 1..69) appendLine("S$i -> S${i + 1}: call $i")
            appendLine("@enduml")
        }
        val (w, h) = svgSize(String(PlantUmlRenderer.render(huge, null).bytes!!))
        val r = PlantUmlRenderer.renderPreview(huge, null, 0, 3.0)
        val img = Image.makeFromEncoded(r.bytes!!)
        assertTrue(r.scale < 3.0 && img.width <= 16384, "scale=${r.scale} width=${img.width}")
        // Whole diagram: same aspect ratio as the natural size, and the size matches the scale.
        assertEquals(w.toDouble() / h, img.width.toDouble() / img.height, 0.05)
        assertEquals(w * r.scale, img.width.toDouble(), w * r.scale * 0.05)
    }

    @Test
    fun exportedPngOfAWideDiagramIsNotCropped() = runBlocking {
        val (w, _) = svgSize(String(PlantUmlRenderer.render(wide, null).bytes!!))
        val png = PlantUmlRenderer.renderPng(wide, null, 0)
        val img = Image.makeFromEncoded(png)
        assertEquals(w.toDouble(), img.width.toDouble(), w * 0.05)
    }
}
