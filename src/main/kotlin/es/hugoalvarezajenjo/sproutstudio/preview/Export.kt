package es.hugoalvarezajenjo.sproutstudio.preview

import es.hugoalvarezajenjo.sproutstudio.features.preview.label
import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import javax.imageio.ImageIO

/** The native dialog and clipboard parts of export (the drawing is the render service's). */
object Export {

    /** Native save dialog for an export. Returns the chosen file, or null if cancelled. */
    fun chooseTarget(baseDir: File?, format: ExportFormat, baseName: String): File? {
        val dialog = FileDialog(null as Frame?, "Export diagram as ${format.label()}", FileDialog.SAVE).apply {
            directory = (baseDir ?: File(System.getProperty("user.home"))).absolutePath
            file = "$baseName.${format.extension}"
        }
        dialog.isVisible = true
        val name = dialog.file ?: return null
        return File(dialog.directory, if (name.endsWith(".${format.extension}")) name else "$name.${format.extension}")
    }

    fun copyPngToClipboard(png: ByteArray) {
        val image = ImageIO.read(png.inputStream()) ?: error("Could not build the image")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(image), null)
    }

    private class ImageSelection(private val image: Image) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any =
            if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
    }
}
