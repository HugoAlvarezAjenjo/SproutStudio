package es.hugoalvarezajenjo.sproutstudio.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFileChooser

/** [Platform] on AWT/Swing, with the macOS native dialogs where they exist. */
class DesktopPlatform : Platform {

    override val isMac = System.getProperty("os.name").lowercase().contains("mac")

    override fun chooseFile(title: String, extensions: Set<String>): File? {
        val d = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
        }
        d.isVisible = true
        return d.file?.let { File(d.directory, it) }
    }

    /** macOS needs the apple.awt.fileDialogForDirectories flag for a native folder picker. */
    override fun chooseFolder(title: String): File? {
        if (isMac) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
                d.isVisible = true
                return d.file?.let { File(d.directory, it) }
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        }
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = title }
        return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    override fun chooseSaveTarget(title: String, suggested: String, dir: File?): File? {
        val d = FileDialog(null as Frame?, title, FileDialog.SAVE).apply {
            dir?.let { directory = it.absolutePath }
            file = suggested
        }
        d.isVisible = true
        val name = d.file ?: return null
        return File(d.directory, name)
    }

    override fun moveToTrash(f: File): Boolean = runCatching {
        val d = Desktop.getDesktop()
        d.isSupported(Desktop.Action.MOVE_TO_TRASH) && d.moveToTrash(f)
    }.getOrDefault(false)

    override fun reveal(f: File) {
        runCatching {
            if (isMac) ProcessBuilder("open", "-R", f.absolutePath).start()
            else Desktop.getDesktop().open(if (f.isDirectory) f else f.absoluteFile.parentFile)
        }
    }

    override fun copyImageToClipboard(png: ByteArray) {
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
