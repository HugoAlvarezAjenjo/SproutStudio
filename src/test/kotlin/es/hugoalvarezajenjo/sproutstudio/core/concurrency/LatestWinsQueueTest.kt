package es.hugoalvarezajenjo.sproutstudio.core.concurrency

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LatestWinsQueueTest {

    private val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val queue = LatestWinsQueue(worker)
    private val ran = Collections.synchronizedList(ArrayList<String>())
    private val gate = CountDownLatch(1)

    /** Keeps the worker busy until [gate] opens, so the next submissions all wait in line. */
    private suspend fun kotlinx.coroutines.CoroutineScope.blockWorker() = async {
        queue.submit(LatestWinsQueue.Priority.URGENT) { ran += "blocker"; gate.await() }
    }.also { while (ran.isEmpty()) yield() }

    @Test
    fun typingFastRunsOnlyTheLatestRenderPerKey() = runBlocking {
        withTimeout(5000) {
            val blocker = blockWorker()
            val calls = (1..5).map { i ->
                async(start = CoroutineStart.UNDISPATCHED) { runCatching { queue.submit(LatestWinsQueue.Priority.NORMAL, key = "doc") { ran += "render $i"; i } } }
            }
            gate.countDown()
            blocker.await()
            val results = calls.map { it.await() }
            assertEquals(listOf("blocker", "render 5"), ran.toList(), "renders 1-4 must never run")
            assertEquals(5, results.last().getOrThrow())
            results.dropLast(1).forEach { assertIs<LatestWinsQueue.Superseded>(it.exceptionOrNull()) }
        }
    }

    @Test
    fun differentKeysAllRun() = runBlocking {
        withTimeout(5000) {
            val blocker = blockWorker()
            val a = async(start = CoroutineStart.UNDISPATCHED) { queue.submit(LatestWinsQueue.Priority.NORMAL, key = "a") { ran += "a" } }
            val b = async(start = CoroutineStart.UNDISPATCHED) { queue.submit(LatestWinsQueue.Priority.NORMAL, key = "b") { ran += "b" } }
            gate.countDown()
            blocker.await(); a.await(); b.await()
            assertEquals(listOf("blocker", "a", "b"), ran.toList())
        }
    }

    @Test
    fun urgentWorkOvertakesBackgroundWork() = runBlocking {
        withTimeout(5000) {
            val blocker = blockWorker()
            // Like opening the template gallery (many thumbnails) and then typing in the editor.
            val thumbs = (1..3).map { i -> async(start = CoroutineStart.UNDISPATCHED) { queue.submit(LatestWinsQueue.Priority.BACKGROUND) { ran += "thumb $i" } } }
            val preview = async(start = CoroutineStart.UNDISPATCHED) { queue.submit(LatestWinsQueue.Priority.NORMAL) { ran += "preview" } }
            val problems = async(start = CoroutineStart.UNDISPATCHED) { queue.submit(LatestWinsQueue.Priority.URGENT) { ran += "problems" } }
            gate.countDown()
            blocker.await(); preview.await(); problems.await(); thumbs.forEach { it.await() }
            assertEquals(listOf("blocker", "problems", "preview", "thumb 1", "thumb 2", "thumb 3"), ran.toList())
        }
    }

    @Test
    fun aCancelledCallerIsNeverRun() = runBlocking {
        withTimeout(5000) {
            val blocker = blockWorker()
            val gone = async(start = CoroutineStart.UNDISPATCHED) { queue.submit(LatestWinsQueue.Priority.NORMAL) { ran += "gone" } }
            gone.cancel()
            while (queue.waitingCount > 0) yield()
            gate.countDown()
            blocker.await()
            queue.submit(LatestWinsQueue.Priority.NORMAL) { ran += "after" }
            assertEquals(listOf("blocker", "after"), ran.toList())
        }
    }

    @Test
    fun errorsReachTheCaller() = runBlocking {
        assertFailsWith<IllegalStateException> { queue.submit(LatestWinsQueue.Priority.NORMAL) { error("boom") } }
        assertEquals("ok", queue.submit(LatestWinsQueue.Priority.NORMAL) { "ok" }, "the queue keeps working")
    }
}
