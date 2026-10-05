package es.hugoalvarezajenjo.sproutstudio.services.render

import net.sourceforge.plantuml.FileFormat

/** What the user can export to. PDF is vector, drawn by PlantUML's own OpenPDF backend. */
enum class ExportFormat(val extension: String, internal val plantUml: FileFormat) {
    SVG("svg", FileFormat.SVG),
    PNG("png", FileFormat.PNG),
    PDF("pdf", FileFormat.PDF),
}
