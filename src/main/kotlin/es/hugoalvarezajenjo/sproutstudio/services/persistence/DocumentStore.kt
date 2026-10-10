package es.hugoalvarezajenjo.sproutstudio.services.persistence

import es.hugoalvarezajenjo.sproutstudio.core.text.LineEnding
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
 *
 * Text comes out with `\n` line endings whatever the file uses ([LineEnding]); a write keeps
 * the ending the file already had on disk, so saving a Windows file doesn't rewrite every line.
 */
class DocumentStore(private val io: CoroutineDispatcher) {

    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockOf(f: File): Mutex =
        locks.computeIfAbsent(runCatching { f.canonicalPath }.getOrDefault(f.absolutePath)) { Mutex() }

    /** Writes [text] to [f] atomically, off the UI thread. */
    suspend fun write(f: File, text: String) = withContext(io) { lockOf(f).withLock { writeAtomically(f, text) } }

    /** Same as [write], blocking the caller (the quit handler, menu Save). */
    fun writeNow(f: File, text: String) = runBlocking(io) { lockOf(f).withLock { writeAtomically(f, text) } }

    suspend fun read(f: File): String = withContext(io) { lockOf(f).withLock { readText(f) } }

    /** [read], or null when [f] is missing or unreadable. */
    suspend fun readOrNull(f: File): String? = withContext(io) { lockOf(f).withLock { runCatching { readText(f) }.getOrNull() } }

    /**
     * [f]'s text now, then again every time it changes on disk (polled every [pollMs]); null
     * while the file doesn't exist. Reads run on [io]. For windows that follow a file edited
     * elsewhere (quick preview, diff against the working copy).
     */
    fun watch(f: File, pollMs: Long = 700): Flow<String?> = flow {
        var stamp: Long? = null
        while (true) {
            val now = if (f.exists()) f.lastModified() else -1L
            if (now != stamp) {
                stamp = now
                emit(if (now < 0) null else runCatching { readText(f) }.getOrNull())
            }
            delay(pollMs)
        }
    }.flowOn(io)

    /** [read], blocking the caller (opening a tab, which must have its text at once). */
    fun readNow(f: File): String = runBlocking(io) { lockOf(f).withLock { readText(f) } }

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
        /** [f]'s text with `\n` line endings. */
        fun readText(f: File): String = LineEnding.normalize(f.readText())

        /**
         * Write via a temp file in the same folder + rename, so a crash or a full disk mid-write
         * (likely enough when saving every second) never leaves a half-written diagram.
         * [content] uses `\n`; it is written with the line ending [f] has now (new files get `\n`).
         */
        fun writeAtomically(f: File, content: String) {
            val dir = f.absoluteFile.parentFile ?: throw IOException("No folder for ${f.name}")
            val tmp = File(dir, ".${f.name}.sprout-tmp")
            try {
                tmp.writeText(endingOf(f).apply(content))
                try {
                    Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            } finally {
                tmp.delete()
            }
        }

        /** The line ending [f] uses, from its first line break (only the start is read). */
        private fun endingOf(f: File): LineEnding {
            if (!f.isFile) return LineEnding.LF
            val head = runCatching { f.inputStream().use { String(it.readNBytes(HEAD_BYTES), Charsets.UTF_8) } }.getOrNull()
            return head?.let(LineEnding::of) ?: LineEnding.LF
        }

        private const val HEAD_BYTES = 8192
    }
}
