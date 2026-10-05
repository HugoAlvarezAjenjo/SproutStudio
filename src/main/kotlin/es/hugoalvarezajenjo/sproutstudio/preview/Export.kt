package es.hugoalvarezajenjo.sproutstudio.preview

import es.hugoalvarezajenjo.sproutstudio.app.AppGraph
import es.hugoalvarezajenjo.sproutstudio.features.preview.label
import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import java.io.File

/** Legacy: the dialog and clipboard parts of export, through the platform. */
object Export {
    private val platform get() = AppGraph.default.platform

    /** Native save dialog for an export. Returns the chosen file, or null if cancelled. */
    fun chooseTarget(baseDir: File?, format: ExportFormat, baseName: String): File? {
        val dir = baseDir ?: File(System.getProperty("user.home"))
        val f = platform.chooseSaveTarget("Export diagram as ${format.label()}", "$baseName.${format.extension}", dir) ?: return null
        return if (f.name.endsWith(".${format.extension}")) f else File(f.parentFile, "${f.name}.${format.extension}")
    }

    fun copyPngToClipboard(png: ByteArray) = platform.copyImageToClipboard(png)
}
