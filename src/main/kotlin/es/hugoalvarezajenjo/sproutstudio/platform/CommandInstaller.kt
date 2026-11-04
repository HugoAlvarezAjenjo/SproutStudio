package es.hugoalvarezajenjo.sproutstudio.platform

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * Installs the `sproutstudio` command ([ShellCommand]) at [target], calling the app at [app].
 *
 * Writes it directly when the user may (Homebrew makes /usr/local/bin theirs); otherwise
 * [asAdmin] copies it there with administrator rights (the system password dialog). A file at
 * [target] that we didn't write is never replaced.
 */
class CommandInstaller(
    private val app: Path,
    val target: Path,
    private val asAdmin: (script: Path, target: Path) -> AdminResult,
) {
    enum class AdminResult { DONE, CANCELLED, FAILED }

    sealed interface Result {
        data class Installed(val target: Path) : Result
        /** [target] exists and isn't ours: the user removes it, we don't. */
        data class NotOurs(val target: Path) : Result
        /** The app runs from a temporary place (macOS App Translocation): the script would break. */
        data object AppNotInstalled : Result
        data object Cancelled : Result
        data class Failed(val message: String) : Result
    }

    fun install(): Result {
        if (TRANSLOCATED in app.toString()) return Result.AppNotInstalled
        if (Files.exists(target) && !ShellCommand.isOurs(runCatching { Files.readString(target) }.getOrDefault(""))) {
            return Result.NotOurs(target)
        }
        val script = ShellCommand.macScript(app)
        return try {
            writeExecutable(target, script)
            Result.Installed(target)
        } catch (_: AccessDeniedException) {
            // Also a missing /usr/local/bin that the user can't create.
            withAdmin(script)
        } catch (e: IOException) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun withAdmin(script: String): Result {
        val tmp = Files.createTempFile(ShellCommand.NAME, ".sh")
        return try {
            Files.writeString(tmp, script)
            when (asAdmin(tmp, target)) {
                AdminResult.DONE -> Result.Installed(target)
                AdminResult.CANCELLED -> Result.Cancelled
                AdminResult.FAILED -> Result.Failed("could not write $target")
            }
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    /** Next to [target] first, then moved over it: a half-written command never runs. */
    private fun writeExecutable(target: Path, text: String) {
        Files.createDirectories(target.parent)
        val tmp = Files.createTempFile(target.parent, ".${ShellCommand.NAME}", ".tmp")
        try {
            Files.writeString(tmp, text)
            Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rwxr-xr-x"))
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }

    companion object {
        private const val TRANSLOCATED = "/AppTranslocation/"

        /**
         * macOS: `install` run through `osascript … with administrator privileges`. The paths go in
         * as arguments and AppleScript quotes them, so nothing from a path is read as a command.
         */
        fun macAdmin(script: Path, target: Path): AdminResult {
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
                p.waitFor() == 0 -> AdminResult.DONE
                "-128" in out -> AdminResult.CANCELLED // "User canceled."
                else -> AdminResult.FAILED
            }
        }
    }
}
