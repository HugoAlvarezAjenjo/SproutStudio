package es.hugoalvarezajenjo.sproutstudio.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.swing.JFileChooser

/**
 * [Platform] on plain AWT/Swing: what works the same on every desktop. The OS classes extend it
 * and override only what their OS does differently.
 */
abstract class AwtPlatform : Platform {

    /** AWT's FileDialog is the native one on macOS and Windows, and GTK's on Linux. */
    override fun chooseFile(title: String, extensions: Set<String>): File? {
        val d = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in extensions }
        }
        d.isVisible = true
        return d.file?.let { File(d.directory, it) }
    }

    /** FileDialog can't pick folders outside macOS, so Swing's chooser. */
    override fun chooseFolder(title: String): File? {
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

    /** Opens the folder holding [f] (a folder opens itself), without selecting anything. */
    override fun reveal(f: File) {
        runCatching { Desktop.getDesktop().open(folderOf(f)) }
    }

    override fun copyImageToClipboard(png: ByteArray) {
        val image = ImageIO.read(png.inputStream()) ?: error("Could not build the image")
        Toolkit.getDefaultToolkit().systemClipboard.setContents(ImageSelection(image), null)
    }

    override fun copyTextToClipboard(text: String) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    }

    /**
     * Where [bringToFront] looks. The OS classes that need it (Windows, Linux) install it in
     * `integrate`; macOS never calls [bringToFront] and touches the toolkit only when it must.
     */
    protected val activeWindows = ActiveWindowTracker()

    /**
     * The window the user was last in, to the front (restored if minimised). The window manager
     * may only flash it in the taskbar instead when another app has the focus (it limits
     * stealing the focus); that still points the user at it.
     */
    override fun bringToFront() {
        val w = activeWindows.frontmost() ?: return
        if (w is Frame && w.extendedState and Frame.ICONIFIED != 0) w.extendedState = w.extendedState and Frame.ICONIFIED.inv()
        w.toFront()
        w.requestFocus()
    }

    protected fun folderOf(f: File): File = if (f.isDirectory) f else f.absoluteFile.parentFile

    private class ImageSelection(private val image: Image) : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.imageFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.imageFlavor
        override fun getTransferData(flavor: DataFlavor): Any =
            if (flavor == DataFlavor.imageFlavor) image else throw UnsupportedFlavorException(flavor)
    }

    companion object {
        /**
         * Runs [cmd] (no shell, so nothing in a path is interpreted) and returns its exit code,
         * or -1 if it can't start or takes longer than [timeoutSeconds].
         */
        fun runCommand(cmd: List<String>, timeoutSeconds: Long = 10): Int = runCatching {
            val p = ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
            if (p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) p.exitValue() else { p.destroy(); -1 }
        }.getOrDefault(-1)

        /** Starts [cmd] in [dir] and returns at once; false if it can't start (not installed). */
        fun startCommand(cmd: List<String>, dir: File): Boolean = runCatching {
            ProcessBuilder(cmd).directory(dir).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        }.isSuccess
    }
}
