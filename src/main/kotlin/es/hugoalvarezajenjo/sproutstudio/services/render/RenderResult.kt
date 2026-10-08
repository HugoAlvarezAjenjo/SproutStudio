package es.hugoalvarezajenjo.sproutstudio.services.render

data class RenderResult(
    /** Image bytes (SVG or PNG, per request); present even for errors (PlantUML draws an error image). */
    val bytes: ByteArray?,
    /** Number of @startxxx/@endxxx blocks found in the file (0 when none). */
    val diagramCount: Int,
    /** Index actually rendered (clamped into range). */
    val index: Int,
    val error: RenderError?,
    /** Pixel scale the image was rendered at (PNG only; 1.0 for SVG). */
    val scale: Double = 1.0,
) {
    val svg: ByteArray? get() = bytes
}
