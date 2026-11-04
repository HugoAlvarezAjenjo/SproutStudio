package es.hugoalvarezajenjo.sproutstudio.platform

import es.hugoalvarezajenjo.sproutstudio.platform.CommandInstaller.AdminResult
import es.hugoalvarezajenjo.sproutstudio.platform.CommandInstaller.Result
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Installing the command into a temporary "bin" folder; the admin step is a fake. */
class CommandInstallerTest {

    private val dir: File = Files.createTempDirectory("install").toFile()
    private val bin: Path = dir.resolve("bin").toPath()
    private val target: Path = bin.resolve(ShellCommand.NAME)
    private val app = Path.of("/Applications/SproutStudio.app")

    /** What the fake admin step was asked to do, and what it answers. */
    private val adminCalls = ArrayList<String>()
    private var adminAnswer = AdminResult.DONE

    @AfterTest
    fun cleanUp() {
        bin.toFile().setWritable(true)
        dir.deleteRecursively()
    }

    private fun installer(app: Path = this.app, unavailable: Result? = null) =
        CommandInstaller(target, ShellCommand.macScript(app), asAdmin = { script, to ->
            adminCalls += Files.readString(script)
            assertEquals(target, to)
            adminAnswer
        }, unavailable = unavailable)

    private fun assumePosix() = assumeTrue("posix" in dir.toPath().fileSystem.supportedFileAttributeViews(), "POSIX file permissions")

    @Test
    fun `a writable folder gets the script directly, executable, with no password`() {
        assumePosix()
        assertEquals(Result.Installed(target), installer().install())
        assertEquals(ShellCommand.macScript(app), Files.readString(target))
        assertEquals("rwxr-xr-x", PosixFilePermissions.toString(Files.getPosixFilePermissions(target)))
        assertTrue(adminCalls.isEmpty())
        assertEquals(listOf(ShellCommand.NAME), bin.toFile().list()!!.toList()) // no temporary file left
    }

    @Test
    fun `our own earlier command is replaced, for an app that moved`() {
        installer(Path.of("/Users/me/Old/SproutStudio.app")).install()
        assertEquals(Result.Installed(target), installer().install())
        assertEquals(ShellCommand.macScript(app), Files.readString(target))
    }

    @Test
    fun `a sproutstudio someone else wrote is never touched`() {
        Files.createDirectories(bin)
        Files.writeString(target, "#!/bin/sh\necho mine\n")
        assertEquals(Result.NotOurs(target), installer().install())
        assertEquals("#!/bin/sh\necho mine\n", Files.readString(target))
        assertTrue(adminCalls.isEmpty())
    }

    @Test
    fun `a folder the user can't write goes through the admin step with the same script`() {
        assumePosix()
        Files.createDirectories(bin)
        bin.toFile().setWritable(false)
        assertEquals(Result.Installed(target), installer().install())
        assertEquals(listOf(ShellCommand.macScript(app)), adminCalls)

        adminAnswer = AdminResult.CANCELLED
        assertEquals(Result.Cancelled, installer().install())
        adminAnswer = AdminResult.FAILED
        assertIs<Result.Failed>(installer().install())
    }

    @Test
    fun `an admin step that can't even start is a failure, not an exception`() {
        assumePosix()
        Files.createDirectories(bin)
        bin.toFile().setWritable(false)
        val noOsascript = CommandInstaller(target, "script", { _, _ -> throw IOException("Cannot run program \"osascript\"") })
        assertEquals(Result.Failed("Cannot run program \"osascript\""), noOsascript.install())
    }

    @Test
    fun `a reason known in advance is the answer, and nothing is written`() {
        assertEquals(Result.AppNotInstalled, installer(unavailable = Result.AppNotInstalled).install())
        assertTrue(!Files.exists(target) && adminCalls.isEmpty())
    }
}
