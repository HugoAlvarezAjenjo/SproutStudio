package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.platform.CommandInstaller
import es.hugoalvarezajenjo.sproutstudio.platform.CommandInstaller.AdminResult
import es.hugoalvarezajenjo.sproutstudio.platform.CommandInstaller.Result
import es.hugoalvarezajenjo.sproutstudio.platform.ShellCommand
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

/** The Help menu's install: what the dialog shows, and that the menu item comes back. */
class InstallCommandTest {

    private val dir: File = Files.createTempDirectory("install").toFile()
    private val target: Path = dir.resolve("bin/sproutstudio").toPath()

    @AfterTest
    fun cleanUp() {
        target.parent.toFile().setWritable(true)
        dir.deleteRecursively()
    }

    /** Install through [admin], with the folder made read-only so the admin step runs. */
    private fun TestScope.install(admin: ((Path, Path) -> AdminResult)? = null): InstallCommand {
        // The command is a shell script for macOS: it needs POSIX permissions, which Windows lacks.
        assumeTrue("posix" in dir.toPath().fileSystem.supportedFileAttributeViews(), "POSIX file permissions")
        if (admin != null) {
            Files.createDirectories(target.parent)
            target.parent.toFile().setWritable(false)
        }
        val installer = CommandInstaller(
            target, ShellCommand.macScript(Path.of("/Applications/SproutStudio.app")), admin ?: { _, _ -> AdminResult.DONE },
        )
        return InstallCommand(installer, this, StandardTestDispatcher(testScheduler)).also { it.install() }
    }

    @Test
    fun `an install shows its result and frees the menu item`() = runTest {
        val s = install()
        testScheduler.advanceUntilIdle()
        assertEquals(Result.Installed(target), s.result)
        assertFalse(s.running)
        s.dismiss()
        assertNull(s.result)
    }

    @Test
    fun `cancelling the password dialog shows nothing`() = runTest {
        val s = install { _, _ -> AdminResult.CANCELLED }
        testScheduler.advanceUntilIdle()
        assertNull(s.result)
        assertFalse(s.running)
    }

    @Test
    fun `an unexpected error is shown, and the menu item still comes back`() = runTest {
        val s = install { _, _ -> throw IllegalStateException("boom") }
        testScheduler.advanceUntilIdle()
        assertIs<Result.Failed>(s.result)
        assertFalse(s.running)
    }
}
