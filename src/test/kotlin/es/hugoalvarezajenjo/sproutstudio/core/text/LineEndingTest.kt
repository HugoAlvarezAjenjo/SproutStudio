package es.hugoalvarezajenjo.sproutstudio.core.text

import kotlin.test.Test
import kotlin.test.assertEquals

class LineEndingTest {

    @Test fun `normalize turns CRLF and lone CR into LF`() {
        assertEquals("a\nb\nc\nd", LineEnding.normalize("a\r\nb\rc\nd"))
        assertEquals("a\n\nb\n", LineEnding.normalize("a\r\n\r\nb\r\n"))
    }

    @Test fun `normalize leaves LF text untouched`() {
        val text = "@startuml\nA -> B\n@enduml\n"
        assertEquals(text, LineEnding.normalize(text))
    }

    @Test fun `the first line break decides the ending`() {
        assertEquals(LineEnding.CRLF, LineEnding.of("a\r\nb\nc"))
        assertEquals(LineEnding.LF, LineEnding.of("a\nb\r\nc"))
        assertEquals(LineEnding.LF, LineEnding.of("one line"))
        assertEquals(LineEnding.LF, LineEnding.of("\nstarts with a break"))
    }

    @Test fun `apply writes the ending back`() {
        assertEquals("a\r\nb\r\n", LineEnding.CRLF.apply("a\nb\n"))
        assertEquals("a\nb\n", LineEnding.LF.apply("a\nb\n"))
    }
}
