package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PlantUmlFormatterTest {

    private fun fmt(vararg lines: String) = PlantUmlFormatter.format(lines.joinToString("\n"))
    private fun text(vararg lines: String) = lines.joinToString("\n") + "\n"

    @Test
    fun `indents sequence blocks and puts else at the block level`() {
        assertEquals(
            text("@startuml", "alt ok", "  A -> B", "  loop 3 times", "    B -> C", "  end", "else fail", "  A -> D", "end", "@enduml"),
            fmt("@startuml", "    alt ok", "A -> B", "loop 3 times", "      B -> C", "end", "  else fail", "A -> D", "     end", "@enduml"),
        )
    }

    @Test
    fun `indents activity if, switch, fork and repeat`() {
        assertEquals(
            text(
                "@startuml", "start", "if (a?) then (yes)", "  :x;", "elseif (b?) then (yes)", "  :y;", "else (no)", "  :z;", "endif",
                "switch (s)", "case (1)", "  :one;", "endswitch",
                "fork", "  :p;", "fork again", "  :q;", "end fork",
                "repeat", "  :r;", "repeat while (more?)", "stop", "@enduml",
            ),
            fmt(
                "@startuml", "start", "if (a?) then (yes)", ":x;", "  elseif (b?) then (yes)", ":y;", "else (no)", ":z;", "  endif",
                "switch (s)", "  case (1)", ":one;", "endswitch",
                "fork", ":p;", "  fork again", ":q;", "end fork",
                "repeat", ":r;", "  repeat while (more?)", "stop", "@enduml",
            ),
        )
    }

    @Test
    fun `indents braces and preprocessor blocks`() {
        assertEquals(
            text("!procedure \$call(\$a)", "  !if \$a == \"x\"", "    \$a -> B", "  !else", "    \$a -> C", "  !endif", "!endprocedure", "class Pizza {", "  +size: Size", "}"),
            fmt("!procedure \$call(\$a)", "!if \$a == \"x\"", "\$a -> B", "!else", "  \$a -> C", "!endif", "  !endprocedure", "class Pizza {", "      +size: Size", "  }"),
        )
    }

    @Test
    fun `closes the gap after a member's visibility, only where PlantUML still reads it`() {
        assertEquals(
            text(
                "class Pizza {", "  +size: Size", "  +toppings: List<Topping>", "  #price(): Money", "  -id",
                "  + x", "  + {static} count", "  -- private --", "}",
                "package p {", "  +    not a member", "}",
            ),
            fmt(
                "class Pizza {", "  +    size: Size", "+toppings: List<Topping>", "  #\tprice(): Money", "-   id",
                "+    x", "+   {static} count", "-- private --", "}",
                "package p {", "+    not a member", "}",
            ),
        )
    }

    @Test
    fun `note bodies keep their own indentation and follow the opener`() {
        assertEquals(
            text("alt ok", "  note over A", "    first", "      nested", "  end note", "end"),
            fmt("alt ok", "note over A", "  first", "    nested", "end note", "end"),
        )
    }

    @Test
    fun `block comments and multi-line activity labels are left alone`() {
        val src = text("if (a) then", "/'", "   keep me", "'/", ":first line", "      second line;", "endif")
        assertEquals(
            text("if (a) then", "  /'", "   keep me", "  '/", "  :first line", "      second line;", "endif"),
            PlantUmlFormatter.format(src),
        )
    }

    @Test
    fun `non uml diagrams are copied as they are`() {
        val yaml = text("@startyaml", "fruit: Apple", "size:   ", "    big: yes", "@endyaml")
        assertEquals(yaml, PlantUmlFormatter.format(yaml))
    }

    @Test
    fun `trims trailing spaces, collapses blank lines and ends in one newline`() {
        assertEquals(text("@startuml", "A -> B", "", "B -> C", "@enduml"), fmt("", "", "@startuml   ", "A -> B  ", "", "", "", "B -> C", "@enduml", "", ""))
    }

    @Test
    fun `spaces arrows only where it is safe`() {
        assertEquals("A -> B: hi->there", Arrows.space("A->B: hi->there"))
        assertEquals("A --> B", Arrows.space("A   -->   B"))
        assertEquals("[*] --> Idle", Arrows.space("[*]-->Idle"))
        assertEquals("\"Pizza App\" ..> Kitchen", Arrows.space("\"Pizza App\"..>Kitchen"))
        assertEquals("A.b ..> C", Arrows.space("A.b..>C"))
        assertEquals("A -[#red]-> B", Arrows.space("A-[#red]->B"))
        assertEquals("Pizza \"1\" *-- \"many\" Topping", Arrows.space("Pizza \"1\"*--\"many\" Topping"))
        assertEquals("Foo <|-- Bar", Arrows.space("Foo<|--Bar"))
        assertEquals("Pedido -> Pago", Arrows.space("Pedido->Pago"))
        // PlantUML reads names greedily: `Ao--B` is `Ao -- B`, `A--oB` is `A -- oB`.
        assertEquals("Ao -- B", Arrows.space("Ao--B"))
        assertEquals("A -- oB", Arrows.space("A--oB"))
        assertEquals("A --o B", Arrows.space("A--o B"))
        assertEquals("A o--B", Arrows.space("A o--B"))
        // Not relations.
        assertEquals("title a->b", Arrows.space("title a->b"))
        assertEquals("title x->b", Arrows.space("title x->b"))
        assertEquals("-> yes;", Arrows.space("-> yes;"))
        assertEquals("my-service", Arrows.space("my-service"))
        assertEquals("A ->(10) B", Arrows.space("A ->(10) B"))
    }

    @Test
    fun `already formatted text is returned untouched`() {
        val v = TextFieldValue(text("@startuml", "A -> B", "@enduml"), TextRange(3))
        assertSame(v, PlantUmlFormatter.format(v))
    }

    @Test
    fun `caret stays on the same code`() {
        val src = "alt ok\n        A -> B\nend\n"
        val caret = src.indexOf("B")
        val r = PlantUmlFormatter.format(TextFieldValue(src, TextRange(caret)))
        assertEquals("alt ok\n  A -> B\nend\n", r.text)
        assertEquals(r.text.indexOf("B"), r.selection.start)
    }

    @Test
    fun `with a selection only the selected lines change`() {
        val src = "alt ok\nA -> B\nA -> C\n   end\n"
        val start = src.indexOf("A -> C")
        val r = PlantUmlFormatter.format(TextFieldValue(src, TextRange(start, start + 6)))
        assertEquals("alt ok\nA -> B\n  A -> C\n   end\n", r.text)
        assertEquals("  A -> C", r.text.substring(r.selection.min, r.selection.max))
    }
}
