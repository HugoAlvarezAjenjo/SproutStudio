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
}
