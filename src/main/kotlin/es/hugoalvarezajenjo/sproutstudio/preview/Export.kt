package es.hugoalvarezajenjo.sproutstudio.preview

import es.hugoalvarezajenjo.sproutstudio.render.ExportFormat
import es.hugoalvarezajenjo.sproutstudio.render.PlantUmlRenderer
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import javax.imageio.ImageIO

object Export {

    /** Shows a native save dialog and exports. Returns the written file, or null if cancelled. */
    suspend fun exportWithDialog(
        source: String,
        baseDir: File?,
        index: Int,
        format: ExportFormat,
        baseName: String,
    ): File? {
        val dialog = FileDialog(null as Frame?, "Export diagram as ${format.label}", FileDialog.SAVE).apply {
            directory = (baseDir ?: File(System.getProperty("user.home"))).absolutePath
            file = "$baseName.${format.extension}"
        }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val target = File(dialog.directory, if (name.endsWith(".${format.extension}")) name else "$name.${format.extension}")
        PlantUmlRenderer.export(source, baseDir, index, format, target)
        return target
    }

    suspend fun copyPngToClipboard(source: String, baseDir: File?, index: Int) {
        val bytes = PlantUmlRenderer.renderPng(source, baseDir, index)
        val image = ImageIO.read(bytes.inputStream()) ?: error("Could not build the image")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(image), null)
    }

    private class ImageSelection(private val image: Image) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any =
            if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
    }
}
