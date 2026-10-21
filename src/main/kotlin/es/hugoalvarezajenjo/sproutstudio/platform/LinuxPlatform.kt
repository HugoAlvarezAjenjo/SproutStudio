package es.hugoalvarezajenjo.sproutstudio.platform

import java.io.File
import kotlin.concurrent.thread

/**
 * Linux (GNOME, KDE, Xfce, Cinnamon...): the JDK has no Trash and can't select a file in the
 * file manager here, so both go through the freedesktop tools every desktop ships.
 *
 * [run] executes a command and returns its exit code; [background] runs work off the UI thread.
 * Both are parameters so tests can check the commands without starting any.
 */
class LinuxPlatform(
    private val run: (List<String>) -> Int = { AwtPlatform.runCommand(it) },
    private val background: (() -> Unit) -> Unit = { thread(isDaemon = true, name = "reveal") { it() } },
) : AwtPlatform() {

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

    /** A GVariant string literal: single quotes, with backslashes and quotes escaped. */
    private fun gvariantString(s: String) = "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
}
