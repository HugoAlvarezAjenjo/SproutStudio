package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.DiagramBlocks.Block
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiagramBlocksTest {

    @Test fun `finds each closed block with its kind and lines`() {
        val text = "@startuml\nA -> B\n@enduml\n\n  @startmindmap\n* a\n  @endmindmap\n"
        assertEquals(listOf(Block("uml", 0, 2), Block("mindmap", 4, 6)), DiagramBlocks.of(text))
    }

    @Test fun `a @start inside an open block is plain text`() {
        assertEquals(listOf(Block("uml", 0, 3)), DiagramBlocks.of("@startuml\nA -> B\n@startuml\n@enduml\n"))
    }

    @Test fun `any @end closes the open block`() {
        assertEquals(listOf(Block("uml", 0, 2)), DiagramBlocks.of("@startuml\nA -> B\n@endmindmap\n"))
    }

    @Test fun `upper case and a bare @start are not directives`() {
        assertEquals(emptyList(), DiagramBlocks.of("@StartUml\nA -> B\n@EndUml\n@start\n@end\n"))
    }

    @Test fun `an unclosed block is not counted but is reported as open`() {
        val text = "@startuml\nA -> B\n@enduml\n@startgantt\n[Task] lasts 2 days\n"
        assertEquals(listOf(Block("uml", 0, 2)), DiagramBlocks.of(text))
        assertEquals("gantt", DiagramBlocks.unclosed(text))
        assertNull(DiagramBlocks.unclosed("@startuml\n@enduml\n"))
    }

    @Test fun `an export is numbered only when the file has several diagrams`() {
        assertEquals("pizza", DiagramBlocks.exportName("pizza", 0, 1))
        assertEquals("pizza", DiagramBlocks.exportName("pizza", 0, 0)) // not rendered yet
        assertEquals("pizza-1", DiagramBlocks.exportName("pizza", 0, 3))
        assertEquals("pizza-3", DiagramBlocks.exportName("pizza", 2, 3))
    }

    @Test fun `titles are each block's own title line`() {
        val text = "title Outside\n@startuml\n  Title  Login flow \nA -> B\n@enduml\n@startuml\nA -> B\n@enduml\n@startuml\ntitle Last\n@enduml\n"
        assertEquals(listOf("Login flow", null, "Last"), DiagramBlocks.titles(text))
    }
}
