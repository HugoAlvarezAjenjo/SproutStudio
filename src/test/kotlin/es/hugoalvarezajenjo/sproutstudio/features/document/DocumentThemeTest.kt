package es.hugoalvarezajenjo.sproutstudio.features.document

import es.hugoalvarezajenjo.sproutstudio.TestGraph
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentThemeTest {

    @Test fun `setting a theme is one undo step and keeps the caret on its text`() {
        val d = TestGraph.document(null, "@startuml\nA -> B\n@enduml\n")
        val caret = d.text.indexOf("B")
        d.value = TextFieldValue(d.text, TextRange(caret))

        d.setDiagramTheme(0, "cerulean")
        assertEquals("@startuml\n!theme cerulean\nA -> B\n@enduml\n", d.text)
        assertEquals(TextRange(d.text.indexOf("B")), d.value.selection)

        d.setDiagramTheme(0, "mars")
        d.undo()
        assertEquals("@startuml\n!theme cerulean\nA -> B\n@enduml\n", d.text, "undo takes back only the last pick")
        d.undo()
        assertEquals("@startuml\nA -> B\n@enduml\n", d.text)
    }
}
