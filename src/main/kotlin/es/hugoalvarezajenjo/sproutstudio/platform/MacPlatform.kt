package es.hugoalvarezajenjo.sproutstudio.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

/** macOS: native folder picker, Finder selection, and the app-level events of AppKit. */
class MacPlatform : AwtPlatform() {

    /** The apple.awt.fileDialogForDirectories flag turns FileDialog into the native folder picker. */
    override fun chooseFolder(title: String): File? {
        System.setProperty("apple.awt.fileDialogForDirectories", "true")
        try {
            val d = FileDialog(null as Frame?, title, FileDialog.LOAD)
            d.isVisible = true
            return d.file?.let { File(d.directory, it) }
        } finally {
            System.setProperty("apple.awt.fileDialogForDirectories", "false")
        }
    }

    override fun reveal(f: File) {
        runCatching { ProcessBuilder("open", "-R", f.absolutePath).start() }
    }

    override fun integrate(hooks: AppHooks) {
        System.setProperty("apple.awt.application.name", "SproutStudio")
        // Native title bar follows the app theme (read once at startup; AWT can't switch it live).
        System.setProperty(
            "apple.awt.application.appearance",
            if (hooks.dark) "NSAppearanceNameDarkAqua" else "NSAppearanceNameAqua",
        )
        if (!Desktop.isDesktopSupported()) return
        val desktop = Desktop.getDesktop()

        // Finder double-clicks arrive as an "open file" event, not as argv. Main registers this
        // before the UI starts, so a cold-start double-click is not lost.
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler { e -> SwingUtilities.invokeLater { hooks.openFiles(e.files) } }
        }
        // ⌘Q quits without closing windows one by one, so the app saves first.
        // This runs on AppKit's thread, not the UI thread, so a blocking save can't deadlock.
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler { _, response ->
                hooks.beforeQuit()
                response.performQuit()
            }
        }
        // "SproutStudio → Settings…" (⌘,) in the app menu.
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler { SwingUtilities.invokeLater { hooks.openSettings() } }
        }
    }
}
