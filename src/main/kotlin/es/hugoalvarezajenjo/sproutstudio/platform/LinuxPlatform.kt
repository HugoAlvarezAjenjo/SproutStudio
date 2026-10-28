package es.hugoalvarezajenjo.sproutstudio.platform

import java.awt.Toolkit
import java.io.File
import kotlin.concurrent.thread

/**
 * Linux (GNOME, KDE, Xfce, Cinnamon...): the JDK has no Trash and can't select a file in the
 * file manager here, so both go through the freedesktop tools every desktop ships.
 *
 * [run] executes a command and returns its exit code; [start] launches one in a folder without
 * waiting (false if it isn't installed); [background] runs work off the UI thread; [setWindowClass]
 * names the windows for the desktop. All are parameters so tests can check them without starting
 * anything.
 */
class LinuxPlatform(
    private val run: (List<String>) -> Int = { AwtPlatform.runCommand(it) },
    private val start: (List<String>, File) -> Boolean = { cmd, dir -> AwtPlatform.startCommand(cmd, dir) },
    private val background: (() -> Unit) -> Unit = { thread(isDaemon = true, name = "reveal") { it() } },
    private val desktopId: String? = System.getProperty(DESKTOP_ID_PROPERTY),
    private val setWindowClass: (String) -> Unit = ::setAwtAppClassName,
) : AwtPlatform() {

    /**
     * Gives every window the WM_CLASS of the menu entry the installer adds, so GNOME and KDE show
     * our icon in the dock and group the windows under the launcher. Without it, AWT names them
     * after the main class and the dock shows a generic icon. Only installed and `./gradlew run`
     * builds set the id (build.gradle.kts); without it the AWT default stays.
     */
    override fun integrate(hooks: AppHooks) {
        desktopId?.takeIf { it.isNotBlank() }?.let(setWindowClass)
    }

    /** `gio trash` (GLib, present on every mainstream desktop) uses the freedesktop Trash. */
    override fun moveToTrash(f: File): Boolean = run(listOf("gio", "trash", "--", f.absolutePath)) == 0

    /**
     * Asks the file manager over D-Bus to show [f] selected (the freedesktop FileManager1
     * interface: Nautilus, Dolphin, Nemo, Thunar...). Without one, opens the folder instead.
     * Off the UI thread: the D-Bus call waits for the file manager to answer.
     */
    override fun reveal(f: File) = background {
        val shown = run(
            listOf(
                "gdbus", "call", "--session",
                "--dest", "org.freedesktop.FileManager1",
                "--object-path", "/org/freedesktop/FileManager1",
                "--method", "org.freedesktop.FileManager1.ShowItems",
                "[${gvariantString(f.absoluteFile.toPath().toUri().toASCIIString())}]", "",
            ),
        ) == 0
        if (!shown) run(listOf("xdg-open", folderOf(f).absolutePath))
    }

    /**
     * There's no one terminal on Linux: Debian and Ubuntu name the user's choice
     * `x-terminal-emulator`; elsewhere, the first desktop terminal that is installed.
     */
    override fun openTerminal(dir: File) {
        TERMINALS.firstOrNull { start(listOf(it), dir) }
    }

    /** A GVariant string literal: single quotes, with backslashes and quotes escaped. */
    private fun gvariantString(s: String) = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"

    companion object {
        /** Set by build.gradle.kts to the installer's menu entry id, e.g. "sproutstudio-SproutStudio". */
        const val DESKTOP_ID_PROPERTY = "sproutstudio.desktopId"

        /** Tried in order by [openTerminal]. */
        val TERMINALS = listOf("x-terminal-emulator", "gnome-terminal", "konsole", "xfce4-terminal", "mate-terminal", "xterm")

        /**
         * There is no public API for WM_CLASS: X11's toolkit keeps it in a static field, read when
         * each window is created, so this must run before the first one. Needs
         * `--add-opens java.desktop/sun.awt.X11` (build.gradle.kts). Under another toolkit (a
         * native Wayland one) the field doesn't exist and nothing changes.
         */
        private fun setAwtAppClassName(name: String) {
            runCatching {
                val toolkit = Toolkit.getDefaultToolkit() // creates XToolkit, which sets its default first
                toolkit.javaClass.getDeclaredField("awtAppClassName").apply { isAccessible = true }.set(null, name)
            }
        }
    }
}
