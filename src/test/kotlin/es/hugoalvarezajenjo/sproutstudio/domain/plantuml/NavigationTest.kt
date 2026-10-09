package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavigationTest {

    /** Files by path, as an in-memory [IncludeReader] (paths resolve as written, ignoring the includer). */
    private fun reader(vararg files: Pair<String, String>) = IncludeReader { _, path ->
        files.toMap()[path]?.let { SourceFile(path, it) }
    }

    @Test
    fun includedDeclarationsAreFoundThroughIncludeCyclesAndSkipTheStdlib() {
        val main = "!include <C4/C4_Container>\n!include a.puml\nContainer(api)\n\$fromB()\n"
        val r = reader(
            "a.puml" to "!include a.puml\n!include b.puml\n",
            "b.puml" to "!include a.puml\n!procedure \$fromB()\n!endprocedure\n",
        )
        val t = assertIs<NavigationTarget.InFile>(Navigation.definitionAt(main, main.indexOf("\$fromB") + 1, "/p/main.puml", r))
        assertEquals("b.puml", t.path)
        assertNull(Navigation.definitionAt(main, main.indexOf("Container") + 1, "/p/main.puml", r), "declared nowhere we can read")
        assertEquals(main.indexOf("\$fromB").let { it..it + 5 }, Navigation.linkAt(main, main.indexOf("\$fromB"), "/p/main.puml", r))
    }

    @Test
    fun labelProseDoesNotLinkToAnIncludedNameButACallInItDoes() {
        val main = "!include lib.puml\nA -> B : bold text \$bold(\"x\")\n"
        val r = reader("lib.puml" to "!function \$bold(\$t)\n!return \$t\n!endfunction\n!define bold x\n")
        assertNull(Navigation.linkAt(main, main.indexOf("bold text") + 1, "/p/main.puml", r))
        assertIs<NavigationTarget.InFile>(Navigation.definitionAt(main, main.indexOf("\$bold") + 1, "/p/main.puml", r))
        assertNull(Navigation.definitionAt(main, main.indexOf("\$bold") + 1), "no reader: within the text only")
    }

    private val seq = """
        @startuml
        actor User
        participant "Web App" as App
        User -> App : login App
        App -> Db : query
        activate App
        note over App
          App talks to Db here
        end note
        ' App in a comment
        @enduml
    """.trimIndent()

    private fun at(text: String, needle: String, nth: Int = 0): Int {
        var i = -1
        repeat(nth + 1) { i = text.indexOf(needle, i + 1) }
        check(i >= 0) { "$needle #$nth not found" }
        return i
    }

    private fun IntRange.of(text: String) = text.substring(first, last + 1)

    @Test
    fun `a reference goes to the alias that declares it`() {
        val t = assertIs<NavigationTarget.InText>(Navigation.definitionAt(seq, at(seq, "App ", 1) + 1))
        assertEquals(at(seq, "App\n"), t.range.first)
        assertEquals("App", t.range.of(seq))
    }

    @Test
    fun `an undeclared participant is declared by its first use`() {
        val text = "@startuml\nA -> B\nB -> C\n@enduml"
        val t = assertIs<NavigationTarget.InText>(Navigation.definitionAt(text, at(text, "B", 1)))
        assertEquals(at(text, "B"), t.range.first)
        // Db's only other mention is in a note body, so it has nowhere to go.
        assertNull(Navigation.definitionAt(seq, at(seq, "Db")))
    }

    @Test
    fun `labels, note bodies, comments and display names are not usages`() {
        val uses = Navigation.occurrencesAt(seq, at(seq, "App\n"))
        val lines = uses.map { it.line }
        // declaration (3), arrow (4), arrow (5), activate (6), note opener (7)
        assertEquals(listOf(3, 4, 5, 6, 7), lines)
        assertTrue(Navigation.occurrencesAt(seq, at(seq, "Web")).isEmpty())
    }

    @Test
    fun `on a declaration it lists the usages`() {
        val u = assertIs<NavigationTarget.Usages>(Navigation.definitionAt(seq, at(seq, "App\n") + 1))
        assertEquals("App", u.symbol)
        assertEquals(listOf(4, 5, 6, 7), u.occurrences.map { it.line })
    }

    @Test
    fun `on a declaration with a single use it goes there`() {
        val text = "@startuml\nactor User\nUser -> Bob\n@enduml"
        val t = assertIs<NavigationTarget.InText>(Navigation.definitionAt(text, at(text, "User")))
        assertEquals(at(text, "User", 1), t.range.first)
    }

    @Test
    fun `nothing to go to off a symbol or on a lonely one`() {
        assertNull(Navigation.definitionAt(seq, at(seq, "participant") + 2))
        assertNull(Navigation.definitionAt("@startuml\nclass Lonely\n@enduml", 18))
    }

    @Test
    fun `a caret right after the name counts`() {
        val o = assertIs<NavigationTarget.InText>(Navigation.definitionAt(seq, at(seq, "User ->") + 4))
        assertEquals(at(seq, "User"), o.range.first)
    }

    @Test
    fun `each diagram block has its own symbols`() {
        val two = "@startuml\nactor A\nA -> B\n@enduml\n@startuml\nA -> C\n@enduml\n"
        val second = at(two, "A -> C")
        // The A of the second diagram is declared by its own first use, not by the first diagram.
        assertNull(Navigation.definitionAt(two, second))
        assertEquals(listOf(6), Navigation.occurrencesAt(two, second).map { it.line })
    }

    @Test
    fun `procedures and variables are global to the file`() {
        val text = "!procedure \$msg(\$a)\n  \$a -> X\n!endprocedure\n!\$who = \"Bob\"\n@startuml\n\$msg(A)\nA -> \$who\n@enduml"
        val call = assertIs<NavigationTarget.InText>(Navigation.definitionAt(text, at(text, "\$msg(A)") + 1))
        assertEquals(at(text, "\$msg"), call.range.first)
        val v = assertIs<NavigationTarget.InText>(Navigation.definitionAt(text, at(text, "\$who", 1) + 1))
        assertEquals(at(text, "\$who"), v.range.first)
    }

    @Test
    fun `a quoted participant without alias is found inside its quotes`() {
        val text = "@startuml\nparticipant \"Web App\"\n\"Web App\" -> Db\n@enduml"
        val t = assertIs<NavigationTarget.InText>(Navigation.definitionAt(text, at(text, "Web App", 1) + 2))
        assertEquals(at(text, "Web App"), t.range.first)
        assertEquals("Web App", t.range.of(text))
    }

    @Test
    fun `a local include is a link, the stdlib and URLs are not`() {
        val text = "!include common/style.puml\n!include <C4/C4_Container>\n!includeurl https://x.y/z.puml\n!includesub parts.puml!BASIC\n"
        val f = assertIs<NavigationTarget.IncludedFile>(Navigation.definitionAt(text, at(text, "style")))
        assertEquals("common/style.puml", f.path)
        assertEquals("common/style.puml", Navigation.linkAt(text, at(text, "style"))!!.of(text))
        assertNull(Navigation.definitionAt(text, at(text, "C4_Container")))
        assertNull(Navigation.definitionAt(text, at(text, "x.y")))
        assertEquals("parts.puml", assertIs<NavigationTarget.IncludedFile>(Navigation.definitionAt(text, at(text, "parts"))).path)
    }

    @Test
    fun `find usages works from a reference too`() {
        val u = Navigation.usagesAt(seq, at(seq, "User ->"))!!
        assertEquals(listOf(2), u.occurrences.map { it.line })
        assertEquals(OccurrenceRole.DECLARATION, u.occurrences.single().role)
    }

    @Test
    fun `a colon inside the display name does not start a label`() {
        val text = "@startuml\nparticipant \"a:b\" as App\nApp -> App\n@enduml"
        val t = assertIs<NavigationTarget.InText>(Navigation.definitionAt(text, at(text, "App ->")))
        assertEquals(at(text, "App\n"), t.range.first)
    }
}
