package es.hugoalvarezajenjo.sproutstudio.features.preview

import androidx.compose.runtime.getValue

import androidx.compose.ui.graphics.Color
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image

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
