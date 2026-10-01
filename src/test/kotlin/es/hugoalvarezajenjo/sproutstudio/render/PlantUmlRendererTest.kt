package es.hugoalvarezajenjo.sproutstudio.render

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlantUmlRendererTest {

    init {
        System.setProperty("java.awt.headless", "true")
    }

    @Test
    fun `renders a sequence diagram to SVG without errors`() {
        val r = PlantUmlRenderer.renderBlocking("@startuml\nAlice -> Bob: hi\n@enduml\n", null)
        assertNull(r.error)
        assertEquals(1, r.diagramCount)
        assertTrue(String(r.svg!!).contains("<svg"))
        assertTrue(String(r.svg!!).contains("Alice"))
    }

    @Test
    fun `renders a class diagram with smetana, no graphviz needed`() {
        val src = "@startuml\nclass Car\nclass Wheel\nCar *-- Wheel\n@enduml\n"
        val r = PlantUmlRenderer.renderBlocking(src, null)
        assertNull(r.error, "unexpected error: ${r.error}")
        val svg = String(r.svg!!)
        assertTrue(svg.contains("Car") && svg.contains("Wheel"))
        assertTrue(!svg.contains("Dot executable"), "fell back to graphviz")
    }

    @Test
    fun `reports the editor line of a syntax error`() {
        val src = "@startuml\nAlice -> Bob: ok\nthis is not plantuml ((\n@enduml\n"
        val r = PlantUmlRenderer.renderBlocking(src, null)
        val err = assertNotNull(r.error)
        assertEquals(3, err.line)
    }

    @Test
    fun `reports correct line for an error in the second diagram`() {
        val src = "@startuml\nA -> B\n@enduml\n\n@startuml\nA -> B\nbroken ((\n@enduml\n"
        val r = PlantUmlRenderer.renderBlocking(src, null, index = 1)
        assertEquals(2, r.diagramCount)
        assertEquals(7, assertNotNull(r.error).line)
    }

    @Test
    fun `counts and selects multiple diagrams`() {
        val src = "@startuml\nA -> B\n@enduml\n@startuml\nC -> D\n@enduml\n"
        val r = PlantUmlRenderer.renderBlocking(src, null, index = 1)
        assertEquals(2, r.diagramCount)
        assertEquals(1, r.index)
        assertTrue(String(r.svg!!).contains(">C<"))
    }

    @Test
    fun `refuses remote includes before rendering`() {
        val src = "@startuml\n!include https://example.com/x.puml\nA -> B\n@enduml\n"
        val r = PlantUmlRenderer.renderBlocking(src, null)
        assertEquals(2, assertNotNull(r.error).line)
        assertNull(r.svg)
    }

    @Test
    fun `resolves local includes relative to the file folder`() {
        val dir = Files.createTempDirectory("puml").toFile()
        File(dir, "common.iuml").writeText("participant Shared\n")
        val src = "@startuml\n!include common.iuml\nShared -> Shared: hello\n@enduml\n"
        val r = PlantUmlRenderer.renderBlocking(src, dir)
        assertNull(r.error, "unexpected error: ${r.error}")
        assertTrue(String(r.svg!!).contains("Shared"))
        dir.deleteRecursively()
    }

    @Test
    fun `offline guard catches all remote include forms`() {
        assertNotNull(OfflineGuard.check("!includeurl foo"))
        assertNotNull(OfflineGuard.check("  !include <https://x/y>"))
        assertNotNull(OfflineGuard.check("!includesub http://x/y!PART"))
        assertNull(OfflineGuard.check("!include local/file.puml"))
        assertNull(OfflineGuard.check("' !include https://commented"))
    }
}
