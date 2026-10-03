package es.hugoalvarezajenjo.sproutstudio.git

import es.hugoalvarezajenjo.sproutstudio.git.LineDiff.HunkRow
import es.hugoalvarezajenjo.sproutstudio.git.LineDiff.Row.Kind.ADDED
import es.hugoalvarezajenjo.sproutstudio.git.LineDiff.Row.Kind.DELETED
import kotlin.test.Test
import kotlin.test.assertEquals

/** The per-block diff shown in the gutter popup. */
class HunkRowsTest {
    private val base = "@startuml\nA -> B\nB -> C\nC -> D\n@enduml\n"

    private fun rows(current: String) = LineDiff.compute(base, current).map { LineDiff.hunkRows(base, current, it) }

    @Test fun modifiedLineShowsOldThenNewWithTheChangedMiddleMarked() {
        assertEquals(
            listOf(HunkRow(DELETED, "B -> C", 5..5), HunkRow(ADDED, "B -> X", 5..5)),
            rows(base.replace("B -> C", "B -> X")).single(),
        )
        // A pure insertion inside the line: nothing to mark on the old side.
        assertEquals(
            listOf(HunkRow(DELETED, "B -> C", null), HunkRow(ADDED, "B --> C", 3..3)),
            rows(base.replace("B -> C", "B --> C")).single(),
        )
    }

    @Test fun appendedTextMarksOnlyTheNewPart() {
        val r = rows(base.replace("B -> C", "B -> C : hi")).single()
        assertEquals(listOf(HunkRow(DELETED, "B -> C", null), HunkRow(ADDED, "B -> C : hi", 6..10)), r)
    }

    @Test fun addedAndDeletedBlocksHaveOnlyOneSide() {
        assertEquals(listOf(HunkRow(ADDED, "X -> Y")), rows(base.replace("C -> D\n", "C -> D\nX -> Y\n")).single())
        assertEquals(listOf(HunkRow(DELETED, "C -> D")), rows(base.replace("C -> D\n", "")).single())
    }

    @Test fun unrelatedLinesAreNotMarkedInside() {
        val r = rows(base.replace("B -> C", "note: z")).single()
        assertEquals(listOf(HunkRow(DELETED, "B -> C"), HunkRow(ADDED, "note: z")), r)
    }

    @Test fun lastLineWithoutNewline() {
        val b = "a\nb"
        val cur = "a\nbb"
        val ch = LineDiff.compute(b, cur).single()
        assertEquals(listOf(HunkRow(DELETED, "b", null), HunkRow(ADDED, "bb", 1..1)), LineDiff.hunkRows(b, cur, ch))
    }
}
