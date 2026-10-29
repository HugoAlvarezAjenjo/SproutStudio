package es.hugoalvarezajenjo.sproutstudio.features.outline

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Outline
import kotlin.test.Test
import kotlin.test.assertEquals

class OutlineRowsTest {

    private val nested = Outline.of("@startuml\npackage shop {\n  package pay {\n    class Card\n  }\n}\nclass Client\n@enduml\n")

    private fun List<OutlineRow>.shape() = joinToString(", ") { r ->
        when (r) {
            is OutlineRow.Header -> "#${r.section.line}"
            is OutlineRow.Entry -> "${"  ".repeat(r.depth)}${r.node.symbol.name}${if (r.collapsed) "+" else ""}"
        }
    }

    @Test fun `entries are indented by depth, with no header for a single diagram`() {
        assertEquals("shop,   pay,     Card, Client", outlineRows(nested, emptySet()).shape())
    }

    @Test fun `a collapsed entry hides what is under it`() {
        assertEquals("shop+, Client", outlineRows(nested, setOf("1:shop")).shape())
        assertEquals("shop,   pay+, Client", outlineRows(nested, setOf("1:pay")).shape())
    }

    @Test fun `each diagram gets a header when there are several`() {
        val o = Outline.of("@startuml\nA -> B\n@enduml\n@startuml\nclass C\n@enduml\n")
        assertEquals("#1, A, B, #4, C", outlineRows(o, emptySet()).shape())
    }

    @Test fun `collapse all closes only entries with something under them`() {
        assertEquals(setOf("1:shop", "1:pay"), nested.containerKeys())
    }
}
