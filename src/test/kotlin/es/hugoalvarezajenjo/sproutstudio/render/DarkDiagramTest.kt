package es.hugoalvarezajenjo.sproutstudio.render

import es.hugoalvarezajenjo.sproutstudio.preview.DiagramImage
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue

/** The dark diagram option recolours the preview only; exports keep the original colours. */
class DarkDiagramTest {
    private val src = "@startuml\nAlice -> Bob: hi\n@enduml\n"

    private fun luminance(dark: Boolean): Float = runBlocking {
        val r = PlantUmlRenderer.renderPreview(src, null, 0, 1.0, darkDiagram = dark)
        val bg = DiagramImage.fromPng(r.bytes!!, r.scale)!!.background
        0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue
    }

    @Test
    fun lightPreviewKeepsWhitePaper() = assertTrue(luminance(false) > 0.9f, "light preview should be white")

    @Test
    fun darkPreviewHasDarkPaper() = assertTrue(luminance(true) < 0.25f, "dark preview should be dark, was ${luminance(true)}")

    @Test
    fun pngExportStaysLightWhatever() = runBlocking {
        val png = PlantUmlRenderer.renderPng(src, null, 0)
        val bg = DiagramImage.fromPng(png, 1.0)!!.background
        assertTrue(bg.red > 0.9f && bg.green > 0.9f && bg.blue > 0.9f, "export must keep original colours")
    }
}
