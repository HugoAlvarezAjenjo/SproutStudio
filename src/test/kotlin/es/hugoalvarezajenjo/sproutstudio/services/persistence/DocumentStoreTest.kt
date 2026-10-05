package es.hugoalvarezajenjo.sproutstudio.services.persistence

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentStoreTest {
    private val dir = Files.createTempDirectory("store").toFile()
    private val f = File(dir, "a.puml").apply { writeText("v1") }
    private val store = DocumentStore(Dispatchers.IO)

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    @Test
    fun noSaveLandsWhileAFileIsHeld() = runBlocking {
        withTimeout(5000) {
            // Like a git rollback: it restores the file and expects nobody to write it meanwhile.
            val rollback = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                store.exclusive(f) {
                    f.writeText("restored")
                    delay(300)
                    f.readText()
                }
            }
            delay(50)
            // An autosave fires in the middle.
            val save = launch { store.write(f, "autosaved") }
            assertEquals("restored", rollback.await(), "the autosave must wait for the rollback")
            save.join()
            assertEquals("autosaved", f.readText(), "and then run")
        }
    }

    @Test
    fun blockingAndSuspendingWritesShareTheLock() = runBlocking {
        withTimeout(5000) {
            val held = async(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                store.exclusive(f) { delay(300); f.readText() }
            }
            delay(50)
            val quit = launch(Dispatchers.Default) { store.writeNow(f, "on quit") }
            assertEquals("v1", held.await())
            quit.join()
            assertEquals("on quit", f.readText())
        }
    }

    @Test
    fun writesAreAtomicAndLeaveNoTempFile() = runBlocking {
        store.write(f, "x".repeat(10_000))
        assertEquals(10_000, f.readText().length)
        assertEquals(listOf("a.puml"), dir.list()!!.toList())
    }
}
