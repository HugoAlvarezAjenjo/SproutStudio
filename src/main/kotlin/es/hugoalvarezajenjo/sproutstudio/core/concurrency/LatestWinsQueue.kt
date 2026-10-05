package es.hugoalvarezajenjo.sproutstudio.core.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs blocking jobs one at a time on [worker], most urgent first.
 *
 * Built for PlantUML, which can't run two things at once and can't be interrupted once a
 * render has started. So instead of cancelling, the queue avoids doing useless work:
 *  - Lower [Priority.ordinal] runs first; equal priorities run in submission order.
 *  - A job with a key REPLACES a waiting job with the same key (typing fast queues one render
 *    per document, not one per keystroke). The replaced caller gets [Superseded].
 *  - A job whose caller was cancelled while waiting is dropped without running.
 */
class LatestWinsQueue(worker: CoroutineDispatcher) {

    enum class Priority { URGENT, NORMAL, BACKGROUND }

    /** The job was replaced by a newer one with the same key before it ran. */
    class Superseded : CancellationException("Replaced by a newer request")

    private class Job<T>(val priority: Priority, val key: Any?, val seq: Long, val block: () -> T) {
        val result = CompletableDeferred<T>()
        fun run() {
            if (result.isCompleted) return // caller gave up while it waited
            runCatching(block).fold({ result.complete(it) }, { result.completeExceptionally(it) })
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + worker)
    private val lock = Any()
    private val waiting = ArrayList<Job<*>>()
    private var draining = false
    private var nextSeq = 0L

    /** Jobs waiting to run (not counting the one running). For tests. */
    val waitingCount: Int get() = synchronized(lock) { waiting.size }

    suspend fun <T> submit(priority: Priority, key: Any? = null, block: () -> T): T {
        val job = synchronized(lock) {
            val j = Job(priority, key, nextSeq++, block)
            if (key != null) {
                val replaced = waiting.filter { it.key == key }
                waiting.removeAll(replaced)
                replaced.forEach { it.result.completeExceptionally(Superseded()) }
            }
            waiting += j
            if (!draining) { draining = true; scope.launch { drain() } }
            j
        }
        try {
            return job.result.await()
        } catch (e: CancellationException) {
            // Our caller was cancelled (or we were superseded): don't run it for nobody.
            synchronized(lock) { waiting.remove(job) }
            job.result.cancel()
            throw e
        }
    }

    private fun drain() {
        while (true) {
            val next = synchronized(lock) {
                val j = waiting.minWithOrNull(compareBy<Job<*>>({ it.priority.ordinal }, { it.seq }))
                if (j == null) draining = false else waiting.remove(j)
                j
            } ?: return
            next.run()
        }
    }
}
