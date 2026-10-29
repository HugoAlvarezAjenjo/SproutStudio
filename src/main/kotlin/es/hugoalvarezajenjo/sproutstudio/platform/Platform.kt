package es.hugoalvarezajenjo.sproutstudio.platform

import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import java.io.File
import java.nio.file.Path

/**
 * Everything that depends on the desktop the app runs on: native dialogs, the Trash, the
 * file manager, the clipboard, and the hooks the OS calls from outside the app's windows.
 *
 * One class per OS ([MacPlatform], [WindowsPlatform], [LinuxPlatform]), each on top of
 * [AwtPlatform], which does what plain AWT/Swing already does the same everywhere. An OS class
 * overrides only what is different on that OS, so no code outside this package asks which OS
 * it is on for behaviour (only for keyboard style and texts, see [Os]). Tests use a fake.
 *
 * Dialogs block until the user answers, like the native ones they wrap.
 */
interface Platform {

    /** "Open a diagram": a file with one of [extensions], or null if cancelled. */
    fun chooseFile(title: String, extensions: Set<String>): File?

    fun chooseFolder(title: String): File?

    /** Native save dialog, opening in [dir] with [suggested] filled in; null if cancelled. */
    fun chooseSaveTarget(title: String, suggested: String, dir: File?): File?

    /** Moves [f] to the system Trash; false if that's not possible (never deletes for good). */
    fun moveToTrash(f: File): Boolean

    /** Shows [f] in the file manager, selected where the OS allows it. */
    fun reveal(f: File)

    /** Opens the system terminal in [dir]. */
    fun openTerminal(dir: File)

    fun copyImageToClipboard(png: ByteArray)

    fun copyTextToClipboard(text: String)

    /**
     * Connects [hooks] to the OS before any window exists (macOS: Finder "open file" events, ⌘Q,
     * the app menu). Most desktops deliver files as program arguments instead, so by default
     * there is nothing to connect.
     */
    fun integrate(hooks: AppHooks) {}

    /**
     * Private per-user folder for [SingleInstance], or null when the OS already keeps the app
     * to one instance (macOS: a double-click reaches the running app as an "open file" event).
     */
    fun instanceDir(): Path? = null

    /** Brings the app's frontmost window forward, when another launch handed us its files. */
    fun bringToFront() {}

    /**
     * How many points at the top of a full-screen window the OS keeps for itself, so the app
     * should leave them empty. [windowTop] and [screenTop] are the top edges of the window and
     * of its screen, in AWT coordinates. By default the OS keeps nothing.
     */
    fun fullScreenTopInset(windowTop: Int, screenTop: Int): Int = 0

    companion object {
        /** The [Platform] of the OS this process runs on. */
        fun current(): Platform = forOs(Os.family)

        fun forOs(family: Os.Family): Platform = when (family) {
            Os.Family.MAC -> MacPlatform()
            Os.Family.WINDOWS -> WindowsPlatform()
            Os.Family.LINUX -> LinuxPlatform()
        }
    }
}
