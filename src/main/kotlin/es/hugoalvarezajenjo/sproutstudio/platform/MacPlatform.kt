package es.hugoalvarezajenjo.sproutstudio.platform

import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Path
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

    /**
     * Only from the packaged app: the script calls the .app bundle this process runs from, in
     * /usr/local/bin, which is on the PATH by default.
     */
    override val commandInstaller: CommandInstaller? by lazy {
        appBundle(ProcessHandle.current().info().command().orElse(null))?.let { app ->
            CommandInstaller(
                target = Path.of("/usr/local/bin", ShellCommand.NAME),
                script = ShellCommand.macScript(app),
                asAdmin = ::installAsAdmin,
                unavailable = CommandInstaller.Result.AppNotInstalled.takeIf { isTranslocated(app) },
            )
        }
    }

    override fun reveal(f: File) {
        runCatching { ProcessBuilder("open", "-R", f.absolutePath).start() }
    }

    /** Terminal.app, opened on the folder (it starts a shell there). */
    override fun openTerminal(dir: File) {
        runCatching { ProcessBuilder("open", "-a", "Terminal", dir.absolutePath).start() }
    }

    /**
     * In full screen, macOS keeps the menu bar's strip at the top of the screen to slide the
     * menu bar in, and clicks there often don't reach the window. On a screen with a notch
     * macOS already starts the window below that strip; on other screens (most external
     * monitors) the window starts at the very top, so the app leaves the strip empty.
     */
    override fun fullScreenTopInset(windowTop: Int, screenTop: Int): Int =
        if (windowTop <= screenTop) MENU_BAR_HEIGHT else 0

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

    internal companion object {
        /** Height of the macOS menu bar on a screen without a notch, in points. */
        const val MENU_BAR_HEIGHT = 24

        /** `X.app/Contents/MacOS/SproutStudio` -> `X.app`; null when not run from a bundle. */
        fun appBundle(launcher: String?): Path? {
            val bundle = launcher?.let { Path.of(it).parent?.parent?.parent } ?: return null
            return bundle.takeIf { it.fileName?.toString()?.endsWith(".app") == true }
        }

        /**
         * An app opened from where it was downloaded runs from a random read-only copy (App
         * Translocation) that is gone after it quits: a command pointing there would break.
         */
        fun isTranslocated(app: Path): Boolean = app.any { it.toString() == "AppTranslocation" }

        /**
         * `install` run through `osascript … with administrator privileges` (the system password
         * dialog). The paths go in as arguments and AppleScript quotes them, so nothing from a path
         * is read as a command.
         */
        fun installAsAdmin(script: Path, target: Path): CommandInstaller.AdminResult {
            val p = ProcessBuilder(
                "osascript",
                "-e", "on run argv",
                "-e", "do shell script \"mkdir -p \" & quoted form of (item 1 of argv) & \" && install -m 755 \" & " +
                    "quoted form of (item 2 of argv) & \" \" & quoted form of (item 3 of argv) with administrator privileges",
                "-e", "end run",
                target.parent.toString(), script.toString(), target.toString(),
            ).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            return when {
                p.waitFor() == 0 -> CommandInstaller.AdminResult.DONE
                "-128" in out -> CommandInstaller.AdminResult.CANCELLED // "User canceled."
                else -> CommandInstaller.AdminResult.FAILED
            }
        }
    }
}
