package es.hugoalvarezajenjo.sproutstudio.platform

import java.io.File

/**
 * Everything that depends on the desktop the app runs on: native dialogs, the Trash, the
 * file manager, the clipboard. Today [DesktopPlatform] (macOS first, with generic fallbacks);
 * a Windows or Linux version only has to change this layer. Tests use a fake.
 *
 * Dialogs block until the user answers, like the native ones they wrap.
 */
interface Platform {
    val isMac: Boolean

    /** "Open a diagram": a file with one of [extensions], or null if cancelled. */
    fun chooseFile(title: String, extensions: Set<String>): File?

    fun chooseFolder(title: String): File?

    /** Native save dialog, opening in [dir] with [suggested] filled in; null if cancelled. */
    fun chooseSaveTarget(title: String, suggested: String, dir: File?): File?

    /** Moves [f] to the system Trash; false if that's not possible (never deletes for good). */
    fun moveToTrash(f: File): Boolean

    /** Shows [f] in Finder (or its folder in the file manager). */
    fun reveal(f: File)

    fun copyImageToClipboard(png: ByteArray)
}
