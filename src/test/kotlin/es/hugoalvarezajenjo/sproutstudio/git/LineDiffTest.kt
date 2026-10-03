package es.hugoalvarezajenjo.sproutstudio.git

import es.hugoalvarezajenjo.sproutstudio.git.LineChange.Type.ADDED
import es.hugoalvarezajenjo.sproutstudio.git.LineChange.Type.DELETED
import es.hugoalvarezajenjo.sproutstudio.git.LineChange.Type.MODIFIED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineDiffTest {
    private val base = "@startuml\nA -> B\nB -> C\nC -> D\n@enduml\n"

    @Test fun `no change, no markers`() = assertTrue(LineDiff.compute(base, base).isEmpty())

    @Test fun `added lines`() {
        val now = "@startuml\nA -> B\nX -> Y\nZ -> W\nB -> C\nC -> D\n@enduml\n"
        assertEquals(listOf(LineChange(ADDED, 2, 4)), LineDiff.compute(base, now))
    }

    @Test fun `modified line`() {
        val now = base.replace("B -> C", "B --> C")
        assertEquals(listOf(LineChange(MODIFIED, 2, 3)), LineDiff.compute(base, now))
    }

    @Test fun `deleted line is marked above the next one`() {
        val now = base.replace("B -> C\n", "")
        assertEquals(listOf(LineChange(DELETED, 2, 2)), LineDiff.compute(base, now))
        val idx = LineDiff.Index(LineDiff.compute(base, now))
        assertTrue(idx.deletedAbove(2))
        assertNull(idx.at(2))
    }

    @Test fun `typing at the end of the file without a newline`() {
        val now = base + "note"
        assertEquals(listOf(LineChange(ADDED, 5, 6)), LineDiff.compute(base, now))
    }

    @Test fun `range lookup for folded lines prefers modified`() {
        val idx = LineDiff.Index(listOf(LineChange(ADDED, 3, 4), LineChange(MODIFIED, 5, 6)))
        assertEquals(MODIFIED, idx.inRange(2, 6))
        assertEquals(ADDED, idx.inRange(0, 3))
        assertNull(idx.inRange(0, 2))
        // A deletion hidden inside a fold shows as a change on the folded line.
        assertEquals(MODIFIED, LineDiff.Index(listOf(LineChange(DELETED, 4, 4))).inRange(2, 6))
    }
}
