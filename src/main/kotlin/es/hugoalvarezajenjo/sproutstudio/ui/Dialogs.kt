package es.hugoalvarezajenjo.sproutstudio.ui

import es.hugoalvarezajenjo.sproutstudio.model.DiagramExtensions
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

object Dialogs {
    val isMac = System.getProperty("os.name").lowercase().contains("mac")

    fun openDiagram(): File? {
        val d = FileDialog(null as Frame?, "Open a diagram", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in DiagramExtensions }
        }
        d.isVisible = true
        return d.file?.let { File(d.directory, it) }
    }

    /** Native folder picker (macOS needs the apple.awt.fileDialogForDirectories flag). */
    fun openFolder(): File? {
        if (isMac) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true")
            try {
                val d = FileDialog(null as Frame?, "Open a project folder", FileDialog.LOAD)
                d.isVisible = true
                return d.file?.let { File(d.directory, it) }
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
            }
        }
        val chooser = javax.swing.JFileChooser().apply { fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY }
        return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    fun saveAs(suggested: String, dir: File?): File? {
        val d = FileDialog(null as Frame?, "Save diagram", FileDialog.SAVE).apply {
            dir?.let { directory = it.absolutePath }
            file = suggested
        }
        d.isVisible = true
        val name = d.file ?: return null
        val withExt = if (name.substringAfterLast('.', "").lowercase() in DiagramExtensions) name else "$name.puml"
        return File(d.directory, withExt)
    }
}
