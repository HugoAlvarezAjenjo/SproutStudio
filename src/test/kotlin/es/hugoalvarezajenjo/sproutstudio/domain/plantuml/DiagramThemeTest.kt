package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiagramThemeTest {

    private fun set(text: String, theme: String?, block: Int = 0) =
        DiagramTheme.set(text, block, theme)?.applyTo(text) ?: text

    @Test fun `reads the theme of the block`() {
        assertEquals("cerulean", DiagramTheme.current("@startuml\n!theme cerulean\nA -> B\n@enduml\n", 0))
        assertNull(DiagramTheme.current("@startuml\nA -> B\n@enduml\n", 0))
    }

    @Test fun `adds the line right after @start`() {
        assertEquals("@startuml\n!theme sketchy\nA -> B\n@enduml\n", set("@startuml\nA -> B\n@enduml\n", "sketchy"))
    }

    @Test fun `a block without @end is not a diagram, so there is nothing to edit`() {
        assertNull(DiagramTheme.set("@startuml\nA -> B\n", 0, "sketchy"))
        assertNull(DiagramTheme.current("@startuml\n!theme cerulean", 0))
    }

    @Test fun `keeps the indentation of the @start line`() {
        assertEquals("  @startuml\n  !theme mars\n  A -> B\n  @enduml", set("  @startuml\n  A -> B\n  @enduml", "mars"))
    }

    @Test fun `replaces an existing theme, keeping its indentation`() {
        assertEquals("@startuml\n  !theme mars\nA -> B\n@enduml\n", set("@startuml\n  !THEME cerulean\nA -> B\n@enduml\n", "mars"))
    }

    @Test fun `default removes the whole line`() {
        assertEquals("@startuml\nA -> B\n@enduml\n", set("@startuml\n!theme cerulean\nA -> B\n@enduml\n", null))
    }

    @Test fun `removes a theme right before @end`() {
        assertEquals("@startuml\n@enduml", set("@startuml\n!theme cerulean\n@enduml", null))
    }

    @Test fun `nothing to change gives no edit`() {
        assertNull(DiagramTheme.set("@startuml\n!theme mars\n@enduml", 0, "mars"))
        assertNull(DiagramTheme.set("@startuml\n@enduml", 0, null))
        assertNull(DiagramTheme.set("A -> B", 0, "mars"), "no diagram block")
        assertNull(DiagramTheme.set("@startuml\n@enduml", 1, "mars"), "no second block")
    }

    @Test fun `only touches the chosen block`() {
        val two = "@startuml\n!theme mars\n@enduml\n@startmindmap\n* a\n@endmindmap\n"
        assertNull(DiagramTheme.current(two, 1), "the first block's theme is not the second's")
        assertEquals("@startuml\n!theme mars\n@enduml\n@startmindmap\n!theme toy\n* a\n@endmindmap\n", set(two, "toy", block = 1))
        assertEquals("@startuml\n@enduml\n@startmindmap\n* a\n@endmindmap\n", set(two, null, block = 0))
    }

    @Test fun `numbers blocks like PlantUML, so it edits the diagram the preview shows`() {
        // PlantUML ignores a @start inside an open block and a @Start in the wrong case.
        val nested = "@startuml\nA -> B\n@startuml\n@enduml\n@startuml\nC -> D\n@enduml\n"
        assertEquals("@startuml\nA -> B\n@startuml\n@enduml\n@startuml\n!theme toy\nC -> D\n@enduml\n", set(nested, "toy", block = 1))
        val wrongCase = "@StartUml\nX\n@EndUml\n@startuml\nA -> B\n@enduml\n"
        assertEquals("@StartUml\nX\n@EndUml\n@startuml\n!theme toy\nA -> B\n@enduml\n", set(wrongCase, "toy"))
    }

    @Test fun `the caret moves with the text after the edit`() {
        val edit = DiagramTheme.set("@startuml\nA -> B\n@enduml\n", 0, "mars")!!
        assertEquals(5, edit.shift(5), "before the edit")
        assertEquals("@startuml\n!theme mars\n".length + 2, edit.shift("@startuml\n".length + 2), "after the edit")
    }
}
