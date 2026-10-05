package es.hugoalvarezajenjo.sproutstudio.services.persistence

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * The only writer of diagram files. One lock per file: autosave, Save, git rollback, rename and
 * move of the same file run one after the other, never interleaved (an autosave can't write
 * the old text back over a file git just restored, or land on a path being renamed).
 *
 * The lock is only ever held on [io], never while waiting for the UI thread, so the blocking
 * [writeNow] (used where the caller can't suspend, like ⌘Q) can't deadlock with it.
 */
class DocumentStore(private val io: CoroutineDispatcher) {

    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockOf(f: File): Mutex =
        locks.computeIfAbsent(runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)) { Mutex() }

    /** Writes [text] to [f] atomically, off the UI thread. */
    suspend fun write(f: File, text: String) = withContext(io) { lockOf(f).withLock { writeAtomically(f, text) } }

    /** Same as [write], blocking the caller (the quit handler, menu Save). */
    fun writeNow(f: File, text: String) = runBlocking(io) { lockOf(f).withLock { writeAtomically(f, text) } }

    suspend fun read(f: File): String = withContext(io) { lockOf(f).withLock { f.readText() } }

    /** [read], or null when [f] is missing or unreadable. */
    suspend fun readOrNull(f: File): String? = withContext(io) { lockOf(f).withLock { runCatching { f.readText() }.getOrNull() } }

    /** [read], blocking the caller (opening a tab, which must have its text at once). */
    fun readNow(f: File): String = runBlocking(io) { lockOf(f).withLock { f.readText() } }

    /**
     * Runs [block] while nobody else writes [files] (git rollback, rename, move). [block] may
     * suspend; it must not wait for the UI thread while it holds the locks.
     */
    suspend fun <T> exclusive(vararg files: File, block: suspend () -> T): T {
        // Always lock in the same order, so two operations on the same pair can't deadlock.
        val ordered = files.map(::lockOf).distinct().sortedBy { System.identityHashCode(it) }
        suspend fun go(i: Int): T = if (i == ordered.size) block() else ordered[i].withLock { go(i + 1) }
        return go(0)
    }

    companion object {
        /**
         * Write via a temp file in the same folder + rename, so a crash or a full disk mid-write
         * (likely enough when saving every second) never leaves a half-written diagram.
         */
        fun writeAtomically(f: File, content: String) {
            val dir = f.absoluteFile.parentFile ?: throw IOException("No folder for ${f.name}")
            val tmp = File(dir, ".${f.name}.sprout-tmp")
            try {
                tmp.writeText(content)
                try {
                    Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                tmp.delete()
            }
        }
    }
}
