package es.hugoalvarezajenjo.sproutstudio.platform

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * Installs the `sproutstudio` command: writes [script] at [target], executable.
 *
 * Writes it directly when the user may; otherwise [asAdmin] copies it there with administrator
 * rights. A file at [target] that we didn't write ([ShellCommand.isOurs]) is never replaced. What
 * the script says and how admin rights are asked for are the OS class's business, not this one's.
 */
class CommandInstaller(
    val target: Path,
    private val script: String,
    private val asAdmin: (script: Path, target: Path) -> AdminResult,
    /** Known before trying: why installing from here can't work (macOS: [Result.AppNotInstalled]). */
    private val unavailable: Result? = null,
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
        unavailable?.let { return it }
        if (Files.exists(target) && !ShellCommand.isOurs(runCatching { Files.readString(target) }.getOrDefault(""))) {
            return Result.NotOurs(target)
        }
        return try {
            writeExecutable()
            Result.Installed(target)
        } catch (_: AccessDeniedException) {
            // Also a missing /usr/local/bin that the user can't create.
            try {
                withAdmin()
            } catch (e: IOException) {
                failed(e) // no temporary file, or no admin tool to run
            }
        } catch (e: IOException) {
            failed(e)
        }
    }

    private fun failed(e: IOException) = Result.Failed(e.message ?: e.javaClass.simpleName)

    private fun withAdmin(): Result {
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
    private fun writeExecutable() {
        Files.createDirectories(target.parent)
        val tmp = Files.createTempFile(target.parent, ".${ShellCommand.NAME}", ".tmp")
        try {
            Files.writeString(tmp, script)
            Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rwxr-xr-x"))
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(tmp)
        }
    }
}
