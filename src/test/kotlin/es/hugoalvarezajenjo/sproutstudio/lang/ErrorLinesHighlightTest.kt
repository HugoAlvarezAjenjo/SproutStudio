package es.hugoalvarezajenjo.sproutstudio.lang

import androidx.compose.ui.text.style.TextDecoration
import es.hugoalvarezajenjo.sproutstudio.ui.DarkIde
import kotlin.test.Test
import kotlin.test.assertEquals

/** The editor underlines EVERY problem line, not just one. */
class ErrorLinesHighlightTest {

    @Test fun everyErrorLineIsUnderlined() {
        val text = "@startuml\nok\nbad one\nok\nbad two\n@enduml\n"
        val h = Highlighter.highlight(text, DarkIde.syntax, errorLines = setOf(3, 5))
        val underlined = h.spanStyles
            .filter { it.item.textDecoration == TextDecoration.Underline }
            .map { text.substring(it.start, it.end) }
        assertEquals(listOf("bad one", "bad two"), underlined.sorted())
    }
}
