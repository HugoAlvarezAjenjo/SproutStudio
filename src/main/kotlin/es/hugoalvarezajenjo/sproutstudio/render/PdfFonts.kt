package es.hugoalvarezajenjo.sproutstudio.render

import org.openpdf.text.pdf.BaseFont

/**
 * Works around a PlantUML PDF bug: its OpenPDF backend picks Times for any font family whose
 * name contains "serif" -- which includes PlantUML's own default, "SansSerif". So every PDF came
 * out in Times while the preview, PNG and SVG are sans-serif.
 *
 * PlantUML caches resolved fonts in a static map keyed by `family.lowercase() + "-b"? + "-i"?`,
 * and looks there first. Seeding the sans-serif names with Helvetica fixes it for every diagram,
 * including fonts set in `!include`d files, while a family the diagram names explicitly
 * (Courier, Times...) still resolves normally. If a PlantUML update renames the cache this is
 * a no-op, and PdfExportTest catches the Times fallback.
 */
internal object PdfFonts {
    // PlantUML keeps the quotes of `skinparam defaultFontName "SansSerif"` in the family name.
    private val sansNames = listOf("sansserif", "sans-serif", "sans serif", "dialog")
        .flatMap { listOf(it, "\"$it\"") }

    private val installed: Boolean by lazy {
        runCatching {
            val field = Class.forName("net.sourceforge.plantuml.openpdf.StringBounderOpenPdf")
                .getDeclaredField("fontCache").apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            val cache = field.get(null) as MutableMap<String, BaseFont>
            val faces = mapOf(
                "" to "Helvetica",
                "-b" to "Helvetica-Bold",
                "-i" to "Helvetica-Oblique",
                "-b-i" to "Helvetica-BoldOblique",
            )
            for (name in sansNames) for ((suffix, face) in faces) {
                cache[name + suffix] = BaseFont.createFont(face, BaseFont.CP1252, BaseFont.NOT_EMBEDDED)
            }
        }.isSuccess
    }

    /** Idempotent; call before every PDF export. */
    fun install(): Boolean = installed
}
