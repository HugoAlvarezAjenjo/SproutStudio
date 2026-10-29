package es.hugoalvarezajenjo.sproutstudio.platform

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import kotlin.concurrent.thread

/**
 * One running app per user: a second launch hands its files to the first one and ends.
 *
 * The first launch takes an exclusive lock on `instance.lock` in [dir] and keeps it while it runs,
 * so the OS frees it even if the app crashes. It listens on a Unix domain socket, `instance.sock`,
 * next to it. A launch that can't take the lock is a second one: it sends its paths over the
 * socket, one per line (none means "just come to the front"), and the caller ends it.
 *
 * Unix domain sockets are files, not network ports: nothing listens on the network, and only
 * someone who can open [dir] (the user, see [Platform.instanceDir]) can connect.
 * They exist on Linux and on Windows 10 1803 and later. Anything that fails here makes the app
 * start as a normal, separate instance instead of not starting.
 */
class SingleInstance(private val dir: Path) {

    private val lockFile = dir.resolve("instance.lock")
    private val socketFile = dir.resolve("instance.sock")

    /** What [start] decided for this launch. */
    sealed interface Role {
        /** First launch: later launches reach it until it is closed (on quit). */
        class Primary internal constructor(
            private val lockChannel: FileChannel,
            private val lock: FileLock,
            private val server: ServerSocketChannel,
            private val socketFile: Path,
        ) : Role, Closeable {
            /** Stops listening first, so no launch connects to an instance that is going away. */
            override fun close() {
                runCatching { server.close() }
                runCatching { Files.deleteIfExists(socketFile) }
                runCatching { lock.release() }
                runCatching { lockChannel.close() }
            }
        }

        /** Another launch is running and got the files: this one should end. */
        data object Forwarded : Role

        /** Couldn't tell (no socket support, unwritable folder...): run on our own. */
        data object Standalone : Role
    }

    /**
     * Becomes the running instance, or forwards [files] to the one already running.
     * [onFiles] runs on a background thread, once per later launch.
     */
    fun start(files: List<File>, onFiles: (List<File>) -> Unit): Role = try {
        createPrivateDir()
        val channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock = tryLock(channel)
        if (lock != null) {
            listen(channel, lock, onFiles)
        } else {
            channel.close()
            if (forward(files)) Role.Forwarded else Role.Standalone
        }
    } catch (e: Exception) {
        // By design: whatever goes wrong, the app still starts (on its own).
        Role.Standalone
    }

    /** Null when another instance holds the lock (another process, or this one in tests). */
    private fun tryLock(channel: FileChannel): FileLock? = try {
        channel.tryLock()
    } catch (e: OverlappingFileLockException) {
        null
    }

    private fun listen(channel: FileChannel, lock: FileLock, onFiles: (List<File>) -> Unit): Role {
        // We hold the lock, so a socket file left here is from an instance that died.
        Files.deleteIfExists(socketFile)
        val server = try {
            ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(UnixDomainSocketAddress.of(socketFile))
        } catch (e: Exception) {
            lock.release(); channel.close()
            return Role.Standalone
        }
        thread(isDaemon = true, name = "single-instance") {
            while (server.isOpen) {
                val client = try { server.accept() } catch (e: IOException) { break }
                // Each launch on its own thread: one that connects and never finishes can't
                // keep the ones after it waiting.
                thread(isDaemon = true, name = "single-instance-client") {
                    client.use { runCatching { onFiles(read(it)) } }
                }
            }
        }
        return Role.Primary(channel, lock, server, socketFile)
    }

    /**
     * Sends [files] to the running instance. It may hold the lock but not be listening yet (it is
     * still starting), so this retries for a short while.
     */
    private fun forward(files: List<File>): Boolean {
        val message = files.joinToString("") { it.absolutePath + "\n" }.toByteArray(Charsets.UTF_8)
        for (attempt in 1..CONNECT_ATTEMPTS) {
            try {
                SocketChannel.open(UnixDomainSocketAddress.of(socketFile)).use { s ->
                    val buf = ByteBuffer.wrap(message)
                    while (buf.hasRemaining()) s.write(buf)
                }
                return true
            } catch (e: IOException) {
                if (attempt < CONNECT_ATTEMPTS) Thread.sleep(CONNECT_RETRY_MS)
            }
        }
        return false
    }

    /** Reads one launch's paths, capped so a broken client can't fill memory. */
    private fun read(client: SocketChannel): List<File> {
        val bytes = Channels.newInputStream(client).readNBytes(MAX_MESSAGE_BYTES)
        return bytes.toString(Charsets.UTF_8).lines().filter { it.isNotBlank() }.map(::File)
    }

    /**
     * [dir] readable only by the user where the file system has Unix permissions (Linux), also
     * when it was already there; Windows' per-user AppData already is.
     */
    private fun createPrivateDir() {
        val posix = "posix" in dir.fileSystem.supportedFileAttributeViews()
        if (!posix) { Files.createDirectories(dir); return }
        val ownerOnly = PosixFilePermissions.fromString("rwx------")
        if (Files.isDirectory(dir)) Files.setPosixFilePermissions(dir, ownerOnly)
        else Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(ownerOnly))
    }

    private companion object {
        const val CONNECT_ATTEMPTS = 20
        const val CONNECT_RETRY_MS = 100L
        const val MAX_MESSAGE_BYTES = 256 * 1024
    }
}
