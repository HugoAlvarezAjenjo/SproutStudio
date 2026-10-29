package es.hugoalvarezajenjo.sproutstudio.platform

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A second launch hands its files to the first, over a real socket in a temporary folder. */
class SingleInstanceTest {

    private val dir: Path = Files.createTempDirectory("si").resolve("app")
    private val received = LinkedBlockingQueue<List<File>>()
    private val opened = mutableListOf<SingleInstance.Role.Primary>()

    /** A launch sends absolute paths, so these are absolute too (on Windows, C:\tmp\a.puml). */
    private val a = File("/tmp/a.puml").absoluteFile
    private val b = File("/tmp/b.puml").absoluteFile

    private fun launch(vararg files: File): SingleInstance.Role =
        SingleInstance(dir).start(files.toList()) { received += it }.also { if (it is SingleInstance.Role.Primary) opened += it }

    private fun nextReceived(): List<File> = assertNotNull(received.poll(5, TimeUnit.SECONDS), "nothing arrived")

    /** Unix permissions only exist where the file system has them (not on Windows). */
    private fun assumePosix() = assumeTrue("posix" in dir.fileSystem.supportedFileAttributeViews())

    @AfterTest
    fun closeAll() = opened.forEach { it.close() }

    @Test
    fun `the first launch becomes the running instance`() {
        assertIs<SingleInstance.Role.Primary>(launch())
    }

    @Test
    fun `a second launch forwards its files as absolute paths`() {
        launch()
        val spaced = File("My Diagrams/it's here.puml")
        assertEquals(SingleInstance.Role.Forwarded, launch(spaced, b))
        assertEquals(listOf(spaced.absoluteFile, b), nextReceived())
    }

    @Test
    fun `a second launch with no files still reaches the running one, to bring it forward`() {
        launch()
        assertEquals(SingleInstance.Role.Forwarded, launch())
        assertEquals(emptyList(), nextReceived())
    }

    @Test
    fun `each later launch arrives on its own`() {
        launch()
        launch(a)
        launch(b)
        // Each launch is read on its own thread, so they may arrive in either order.
        assertEquals(setOf(listOf(a), listOf(b)), setOf(nextReceived(), nextReceived()))
    }

    @Test
    fun `once the running instance quits, the next launch takes over`() {
        (launch() as SingleInstance.Role.Primary).close()
        assertIs<SingleInstance.Role.Primary>(launch())
    }

    @Test
    fun `a socket file left by a crashed instance doesn't stop the next one`() {
        Files.createDirectories(dir)
        Files.writeString(dir.resolve("instance.sock"), "stale")
        assertIs<SingleInstance.Role.Primary>(launch())
        assertEquals(SingleInstance.Role.Forwarded, launch(a))
        assertEquals(listOf(a), nextReceived())
    }

    @Test
    fun `the folder is created readable only by the user`() {
        assumePosix()
        launch()
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)))
        assertTrue(Files.exists(dir.resolve("instance.sock")))
    }

    @Test
    fun `a launch that connects and never finishes doesn't hold up the next ones`() {
        launch()
        SocketChannel.open(UnixDomainSocketAddress.of(dir.resolve("instance.sock"))).use {
            assertEquals(SingleInstance.Role.Forwarded, launch(a)) // while the first one hangs
            assertEquals(listOf(a), nextReceived())
        }
    }

    @Test
    fun `an existing folder is made readable only by the user`() {
        assumePosix()
        Files.createDirectories(dir)
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwxr-xr-x"))
        launch()
        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)))
    }

    @Test
    fun `a folder that can't be created means running on our own`() {
        val blocked = Files.createTempFile("si", "file") // a file where the folder should go
        assertEquals(SingleInstance.Role.Standalone, SingleInstance(blocked.resolve("app")).start(emptyList()) {})
    }
}
