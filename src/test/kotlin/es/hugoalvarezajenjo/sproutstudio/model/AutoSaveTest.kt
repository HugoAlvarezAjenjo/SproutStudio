package es.hugoalvarezajenjo.sproutstudio.model

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

class AutoSaveTest {
    private lateinit var dir: File
    private var savedPref = true

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("sprout-autosave").toFile()
        savedPref = AutoSavePrefs.enabled // don't clobber the user's real setting
        AutoSavePrefs.enabled = true
    }

    @AfterTest
    fun tearDown() {
        AutoSavePrefs.enabled = savedPref
        dir.setWritable(true)
        dir.deleteRecursively()
    }

    private fun fileDoc(text: String = "@startuml\nA -> B\n@enduml\n"): Document {
        val f = File(dir, "d.puml").apply { writeText(text) }
        return Document.open(f)
    }

    private fun Document.type(text: String) {
        value = TextFieldValue(text)
        Snapshot.sendApplyNotifications() // what the Compose frame clock does in the app
    }

    /** Runs [AutoSave.watch] with a short idle time while [body] executes. */
    private fun watching(docs: List<Document>, body: suspend () -> Unit) = runBlocking {
        val job = launch { AutoSave.watch({ docs }, idleMs = 100) }
        delay(50)
        body()
        job.cancel()
    }

    @Test
    fun `saves after you stop typing`() {
        val doc = fileDoc()
        watching(listOf(doc)) {
            doc.type("@startuml\nA -> C\n@enduml\n")
            delay(400)
        }
        assertEquals("@startuml\nA -> C\n@enduml\n", doc.file!!.readText())
        assertFalse(doc.dirty)
    }

    @Test
    fun `does not write while you keep typing`() {
        val original = "@startuml\n@enduml\n"
        val doc = fileDoc(original)
        watching(listOf(doc)) {
            repeat(8) { i -> doc.type("@startuml\nA -> B$i\n@enduml\n"); delay(40) }
            assertEquals(original, doc.file!!.readText(), "saved mid-typing")
            delay(400)
        }
        assertEquals("@startuml\nA -> B7\n@enduml\n", doc.file!!.readText())
    }

    @Test
    fun `untitled diagrams are never autosaved`() {
        val doc = Document(null, "@startuml\n@enduml\n")
        watching(listOf(doc)) {
            doc.type("@startuml\nX -> Y\n@enduml\n")
            delay(400)
        }
        assertTrue(doc.dirty)
        AutoSave.flush(listOf(doc))
        assertTrue(doc.dirty)
        assertTrue(dir.list()!!.isEmpty(), "nothing written anywhere")
    }

    @Test
    fun `turned off means nothing is written`() {
        AutoSavePrefs.enabled = false
        val original = "@startuml\n@enduml\n"
        val doc = fileDoc(original)
        watching(listOf(doc)) {
            doc.type("@startuml\nchanged\n@enduml\n")
            delay(400)
        }
        AutoSave.flush(listOf(doc))
        assertEquals(original, doc.file!!.readText())
        assertTrue(doc.dirty)
    }

    @Test
    fun `flush saves immediately (tab switch, focus loss, quit)`() {
        val doc = fileDoc()
        doc.value = TextFieldValue("@startuml\nnow\n@enduml\n")
        AutoSave.flush(listOf(doc))
        assertEquals("@startuml\nnow\n@enduml\n", doc.file!!.readText())
        assertFalse(doc.dirty)
    }

    @Test
    fun `atomic save leaves no temp file behind`() {
        val doc = fileDoc()
        doc.value = TextFieldValue("@startuml\nB\n@enduml\n")
        assertTrue(doc.save())
        assertEquals(listOf("d.puml"), dir.list()!!.sorted())
    }

    @Test
    fun `a failed save keeps the edits and reports why`() {
        val doc = fileDoc()
        doc.value = TextFieldValue("@startuml\nkeep me\n@enduml\n")
        dir.setWritable(false)
        assertFalse(doc.save())
        assertTrue(doc.dirty)
        assertNotNull(doc.saveError)
        dir.setWritable(true)
        assertTrue(doc.save())
        assertNull(doc.saveError)
        assertEquals("@startuml\nkeep me\n@enduml\n", doc.file!!.readText())
    }
}
