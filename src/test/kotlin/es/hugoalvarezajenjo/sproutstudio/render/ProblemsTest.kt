package es.hugoalvarezajenjo.sproutstudio.render

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProblemsTest {

    init {
        System.setProperty("java.awt.headless", "true")
    }

    @Test
    fun `a clean file has no problems`() {
        val p = PlantUmlRenderer.collectProblemsBlocking("@startuml\nAlice -> Bob: hi\n@enduml\n", null)
        assertTrue(p.isEmpty(), "expected no problems, got $p")
    }

    @Test
    fun `blank and no-diagram files report nothing`() {
        assertTrue(PlantUmlRenderer.collectProblemsBlocking("", null).isEmpty())
        assertTrue(PlantUmlRenderer.collectProblemsBlocking("just prose, no @startuml\n", null).isEmpty())
    }

    @Test
    fun `reports an error on its editor line`() {
        val src = "@startuml\nAlice -> Bob: ok\nthis is not plantuml ((\n@enduml\n"
        val p = PlantUmlRenderer.collectProblemsBlocking(src, null)
        assertTrue(p.isNotEmpty())
        assertEquals(3, p.first().line)
        assertEquals(0, p.first().diagramIndex)
    }

    @Test
    fun `collects errors from several diagram blocks in one pass`() {
        // First block clean, second broken on its line 3 (file line 7), third broken on file line 11.
        val src = buildString {
            append("@startuml\nA -> B\n@enduml\n\n")        // lines 1-3 (clean)
            append("@startuml\nA -> B\nbroken ((\n@enduml\n\n") // lines 5-9, error on file line 7
            append("@startuml\nmore nonsense ))\n@enduml\n")  // lines 11-13, error on file line 12
        }
        val p = PlantUmlRenderer.collectProblemsBlocking(src, null)
        // Both broken blocks are reported; the clean one is not.
        val blocks = p.map { it.diagramIndex }.toSet()
        assertTrue(1 in blocks, "second block error missing: $p")
        assertTrue(2 in blocks, "third block error missing: $p")
        assertTrue(0 !in blocks, "clean first block wrongly flagged: $p")
    }

    @Test
    fun `reports every bad line of one block, not only the first`() {
        // The pizza.puml case: PlantUML alone stops at "inte dasda" and never mentions "cla sad".
        val src = """
            @startuml
            A -> B
            @enduml

            @startuml
            class Pizza {
              +size: Size
            }
            inte dasda
            Pizza --> Size
            cla sad
            @enduml
        """.trimIndent() + "\n"
        val p = PlantUmlRenderer.collectProblemsBlocking(src, null)
        assertEquals(listOf(9, 11), p.map { it.line }, "got $p")
        assertTrue(p.all { it.diagramIndex == 1 })
    }

    @Test
    fun `a broken block opener does not loop or touch other diagrams`() {
        val src = "@startuml\nclass A {\n  x\n@enduml\n\n@startuml\nA -> B\n@enduml\n"
        val p = PlantUmlRenderer.collectProblemsBlocking(src, null)
        assertTrue(p.isNotEmpty() && p.size <= 20, "got $p")
        assertTrue(p.none { it.diagramIndex == 1 }, "clean second diagram flagged: $p")
    }

    @Test
    fun `a remote include is a problem`() {
        val src = "@startuml\n!include https://example.com/x.puml\nA -> B\n@enduml\n"
        val p = PlantUmlRenderer.collectProblemsBlocking(src, null)
        assertEquals(1, p.size)
        assertEquals(2, p.first().line)
    }
}
