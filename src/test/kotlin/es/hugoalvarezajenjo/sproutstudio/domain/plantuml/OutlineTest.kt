package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutlineTest {

    private fun OutlineNode.tree(): String =
        symbol.name + if (children.isEmpty()) "" else children.joinToString(", ", " [", "]") { it.tree() }

    private fun Outline.trees() = sections.map { s -> s.nodes.joinToString(", ") { it.tree() } }

    @Test fun `participants in text order, implicit ones included`() {
        val o = Outline.of("@startuml\nactor User\nparticipant Shop\nUser -> Shop\nShop -> Bank\n@enduml\n")
        assertEquals(listOf("User, Shop, Bank"), o.trees())
        assertEquals(listOf("actor", "participant", SymbolIndex.USED), o.sections[0].nodes.map { it.symbol.kind })
    }

    @Test fun `classes nest inside the packages that hold them`() {
        val text = """
            @startuml
            package shop {
              class Order {
                id
              }
              package pay {
                class Card
              }
            }
            class Client
            @enduml
        """.trimIndent()
        val o = Outline.of(text)
        assertEquals(listOf("shop [Order, pay [Card]], Client"), o.trees())
        assertEquals(9, o.sections[0].nodes[0].lastLine)
    }

    @Test fun `one section per diagram, with its kind, title and preview index`() {
        val text = "@startuml\ntitle Login\nA -> B\n@enduml\n\n@startuml\nclass C\n@enduml\n"
        val s = Outline.of(text).sections
        assertEquals(listOf(0, 1), s.map { it.block })
        assertEquals(listOf("Login", null), s.map { it.title })
        assertEquals(listOf(1, 6), s.map { it.line })
        assertEquals("uml", s[0].kind)
    }

    @Test fun `the same name in two diagrams is two entries`() {
        val o = Outline.of("@startuml\nA -> B\n@enduml\n@startuml\nA -> C\n@enduml\n")
        assertEquals(listOf("A, B", "A, C"), o.trees())
    }

    @Test fun `procedures and an include file's names go in a section of their own, first`() {
        val text = "!procedure \$box(\$x)\n\$x\n!endprocedure\n@startuml\nactor U\n@enduml\n"
        val s = Outline.of(text).sections
        assertNull(s[0].block)
        assertEquals(listOf("\$box", "U"), s.map { it.nodes.single().symbol.name })
        assertEquals(listOf("A"), Outline.of("participant A\n").trees())
    }

    @Test fun `nothing declared is an empty outline`() {
        assertTrue(Outline.of("@startmindmap\n* root\n@endmindmap\n").isEmpty)
        assertTrue(Outline.of("").isEmpty)
    }

    @Test fun `the caret selects the symbol under it, at any use`() {
        val text = "@startuml\nparticipant Shop\nUser -> Shop: buy\n@enduml\n"
        val o = Outline.of(text)
        assertEquals("Shop", o.selectedAt(text.lastIndexOf("Shop") + 1)?.symbol?.name)
        assertEquals("User", o.selectedAt(text.indexOf("User"))?.symbol?.name)
    }

    @Test fun `elsewhere it selects the innermost block the caret is in`() {
        val text = "@startuml\npackage shop {\n  class Order {\n    id\n  }\n}\n@enduml\n"
        val o = Outline.of(text)
        assertEquals("Order", o.selectedAt(text.indexOf("id"))?.symbol?.name)
        assertEquals("shop", o.selectedAt(text.indexOf("}\n@end"))?.symbol?.name)
        assertNull(o.selectedAt(text.indexOf("@enduml")))
    }
}
