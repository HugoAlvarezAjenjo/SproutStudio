package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.TestGraph

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.input.TextFieldValue
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class AutoSaverTest {
    private lateinit var dir: File
    private var savedPref = true

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("sprout-autosave").toFile()
        savedPref = TestGraph.graph.autoSave.enabled.value // don't clobber the user's real setting
        TestGraph.graph.autoSave.enabled.value = true
    }

    @AfterTest
    fun tearDown() {
        TestGraph.graph.autoSave.enabled.value = savedPref
        dir.setWritable(true)
        dir.listFiles()?.forEach { it.setWritable(true) } // Windows can't delete a read-only file
        dir.deleteRecursively()
    }

    private fun fileDoc(text: String = "@startuml\nA -> B\n@enduml\n"): Document {
        val f = File(dir, "d.puml").apply { writeText(text) }
        return TestGraph.openDocument(f)
    }

    private fun Document.type(text: String) {
        value = TextFieldValue(text)
        Snapshot.sendApplyNotifications() // what the Compose frame clock does in the app
    }

    /** Runs [AutoSave.watch] while [body] executes, with an idle time well above the typing pauses below. */
    private fun watching(docs: List<Document>, body: suspend () -> Unit) = runBlocking {
        val job = launch { TestGraph.graph.autoSaver.watch({ docs }, idleMs = 300) }
        delay(50)
        body()
        job.cancel()
    }

    /**
     * Waits for [done] instead of a fixed time: a slow CI machine may take longer than the
     * idle time plus the write, and stopping the watcher mid-save leaves the edits marked dirty.
     */
    private suspend fun eventually(what: String, done: () -> Boolean) {
        val until = System.nanoTime() + 5_000_000_000
        while (!done()) {
            check(System.nanoTime() < until) { "timed out waiting for: $what" }
            delay(20)
        }
    }

    @Test
    fun `saves after you stop typing`() = runBlocking<Unit> {
        val doc = fileDoc()
        watching(listOf(doc)) {
            doc.type("@startuml\nA -> C\n@enduml\n")
            eventually("the save") { !doc.dirty }
        }
        assertEquals("@startuml\nA -> C\n@enduml\n", doc.file!!.readText())
        assertFalse(doc.dirty)
    }

    @Test
    fun `does not write while you keep typing`() = runBlocking<Unit> {
        val original = "@startuml\n@enduml\n"
        val doc = fileDoc(original)
        watching(listOf(doc)) {
            repeat(8) { i -> doc.type("@startuml\nA -> B$i\n@enduml\n"); delay(40) }
            assertEquals(original, doc.file!!.readText(), "saved mid-typing")
            eventually("the save") { !doc.dirty }
        }
        assertEquals("@startuml\nA -> B7\n@enduml\n", doc.file!!.readText())
    }

    @Test
    fun `untitled diagrams are never autosaved`() = runBlocking<Unit> {
        val doc = TestGraph.document(null, "@startuml\n@enduml\n")
        watching(listOf(doc)) {
            doc.type("@startuml\nX -> Y\n@enduml\n")
            delay(700) // well past the idle time: a save would have happened
        }
        assertTrue(doc.dirty)
        TestGraph.graph.autoSaver.flush(listOf(doc))
        assertTrue(doc.dirty)
        assertTrue(dir.list()!!.isEmpty(), "nothing written anywhere")
    }

    @Test
    fun `turned off means nothing is written`() = runBlocking<Unit> {
        TestGraph.graph.autoSave.enabled.value = false
        val original = "@startuml\n@enduml\n"
        val doc = fileDoc(original)
        watching(listOf(doc)) {
            doc.type("@startuml\nchanged\n@enduml\n")
            delay(700) // well past the idle time: a save would have happened
        }
        TestGraph.graph.autoSaver.flush(listOf(doc))
        assertEquals(original, doc.file!!.readText())
        assertTrue(doc.dirty)
    }

    @Test
    fun `flush saves immediately (tab switch, focus loss, quit)`() = runBlocking<Unit> {
        val doc = fileDoc()
        doc.value = TextFieldValue("@startuml\nnow\n@enduml\n")
        TestGraph.graph.autoSaver.flush(listOf(doc))
        assertEquals("@startuml\nnow\n@enduml\n", doc.file!!.readText())
        assertFalse(doc.dirty)
    }

    @Test
    fun `atomic save leaves no temp file behind`() = runBlocking<Unit> {
        val doc = fileDoc()
        doc.value = TextFieldValue("@startuml\nB\n@enduml\n")
        assertTrue(doc.save())
        assertEquals(listOf("d.puml"), dir.list()!!.sorted())
    }

    @Test
    fun `a failed save keeps the edits and reports why`() = runBlocking<Unit> {
        val doc = fileDoc()
        doc.value = TextFieldValue("@startuml\nkeep me\n@enduml\n")
        // A read-only folder blocks the temp file on macOS/Linux; Windows ignores that on
        // folders but refuses to replace a read-only file.
        dir.setWritable(false); doc.file!!.setWritable(false)
        assertFalse(doc.save())
        assertTrue(doc.dirty)
        assertNotNull(doc.saveError)
        dir.setWritable(true); doc.file!!.setWritable(true)
        assertTrue(doc.save())
        assertNull(doc.saveError)
        assertEquals("@startuml\nkeep me\n@enduml\n", doc.file!!.readText())
    }
}
