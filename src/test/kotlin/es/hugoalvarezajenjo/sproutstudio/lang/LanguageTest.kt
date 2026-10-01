package es.hugoalvarezajenjo.sproutstudio.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LanguageTest {

    private val seq = "@startuml\nactor User\nparticipant \"Web App\" as App\nUser -> App: hi\nApp -> Db: query\n@enduml\n"

    @Test
    fun `detects diagram kinds`() {
        assertEquals(DiagramKind.SEQUENCE, PlantUmlLanguage.kindAt(seq, 20))
        assertEquals(DiagramKind.CLASS, PlantUmlLanguage.kindAt("@startuml\nclass A\nA <|-- B\n@enduml", 12))
        assertEquals(DiagramKind.ACTIVITY, PlantUmlLanguage.kindAt("@startuml\nstart\n:Hello;\nstop\n@enduml", 12))
        assertEquals(DiagramKind.MINDMAP, PlantUmlLanguage.kindAt("@startmindmap\n* a\n@endmindmap", 15))
    }

    @Test
    fun `kind is taken from the block containing the caret`() {
        val two = "@startuml\nclass A\n@enduml\n@startuml\nAlice -> Bob\n@enduml\n"
        assertEquals(DiagramKind.CLASS, PlantUmlLanguage.kindAt(two, 12))
        assertEquals(DiagramKind.SEQUENCE, PlantUmlLanguage.kindAt(two, two.indexOf("Alice") + 2))
    }

    @Test
    fun `extracts declared symbols, aliases and arrow endpoints`() {
        val names = PlantUmlLanguage.symbols(seq).map { it.name }
        assertTrue("User" in names)
        assertTrue("App" in names, "alias should win over display name: $names")
        assertTrue("Db" in names, "implicit participant from arrow: $names")
    }

    @Test
    fun `completes declared participants after an arrow`() {
        val text = seq.replace("@enduml", "User -> A\n@enduml")
        val caret = text.indexOf("User -> A") + "User -> A".length
        val req = assertNotNull(CompletionEngine.complete(text, caret))
        assertEquals("App", req.items.first().label)
    }

    @Test
    fun `completes keywords for the current diagram kind`() {
        val text = "@startuml\nAlice -> Bob\nact\n@enduml"
        val caret = text.indexOf("act") + 3
        val labels = assertNotNull(CompletionEngine.complete(text, caret)).items.map { it.label }
        assertTrue("activate" in labels && "actor" in labels, "$labels")
    }

    @Test
    fun `offers the right @end to close an open diagram`() {
        val text = "@startuml\nA -> B\n@e"
        val req = assertNotNull(CompletionEngine.complete(text, text.length))
        assertEquals("@enduml", req.items.first().label)
    }

    @Test
    fun `skinparam context offers skinparam names`() {
        val text = "@startuml\nskinparam hand"
        val req = assertNotNull(CompletionEngine.complete(text, text.length))
        assertEquals("handwritten", req.items.first().label)
    }

    @Test
    fun `no popup without a prefix unless forced`() {
        assertNull(CompletionEngine.complete("@startuml\n", 10))
        assertNotNull(CompletionEngine.complete("@startuml\nA -> B\n", 17, force = true))
    }

    @Test
    fun `applying a snippet places the caret at the marker`() {
        val text = "@st"
        val req = assertNotNull(CompletionEngine.complete(text, 3))
        val snippet = req.items.first { it.kind == CompletionKind.SNIPPET && it.label.endsWith("sequence") }
        val applied = CompletionEngine.apply(text, req, snippet, 3)
        assertTrue(applied.text.startsWith("@startuml\nactor User"))
        assertEquals("hello", applied.text.substring(applied.caret, applied.caret + 5))
    }

    @Test
    fun `tokenizer classifies the main categories`() {
        val text = "@startuml\n' a comment\nclass Foo <<entity>> #FF0000\nFoo --> Bar : \"label\"\n@enduml"
        val tokens = Highlighter.tokenize(text)
        fun typeOf(s: String) = tokens.first { text.substring(it.start, it.end) == s }.type
        assertEquals(TokenType.DIRECTIVE, typeOf("@startuml"))
        assertEquals(TokenType.COMMENT, typeOf("' a comment"))
        assertEquals(TokenType.KEYWORD, typeOf("class"))
        assertEquals(TokenType.STEREOTYPE, typeOf("<<entity>>"))
        assertEquals(TokenType.COLOR, typeOf("#FF0000"))
        assertEquals(TokenType.ARROW, typeOf("-->"))
        assertEquals(TokenType.STRING, typeOf("\"label\""))
    }

    @Test
    fun `finds matching brackets around the caret`() {
        val text = "class A {\n  x()\n}"
        assertEquals(8 to 16, Highlighter.matchingBracket(text, 9))
        assertEquals(8 to 16, Highlighter.matchingBracket(text, 17))
        assertNull(Highlighter.matchingBracket("abc", 1))
    }
}
