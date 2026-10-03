package es.hugoalvarezajenjo.sproutstudio.git

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RevertHunkTest {
    private val base = "@startuml\nA -> B\nB -> C\nC -> D\n@enduml\n"

    private fun revertOnly(current: String, which: Int = 0): String {
        val ch = LineDiff.compute(base, current)[which]
        return LineDiff.revert(base, current, ch).text
    }

    @Test fun `added lines go away`() =
        assertEquals(base, revertOnly("@startuml\nA -> B\nX\nY\nB -> C\nC -> D\n@enduml\n"))

    @Test fun `modified line gets its old text`() =
        assertEquals(base, revertOnly(base.replace("B -> C", "B --> C : hi")))

    @Test fun `deleted lines come back`() =
        assertEquals(base, revertOnly(base.replace("B -> C\nC -> D\n", "")))

    @Test fun `only the chosen block is rolled back`() {
        val now = base.replace("A -> B", "A -> B2").replace("C -> D", "C -> D2")
        val out = revertOnly(now, which = 1)
        assertEquals(base.replace("A -> B", "A -> B2"), out)
    }

    @Test fun `caret lands at the start of the restored block`() {
        val now = base.replace("B -> C", "changed")
        val ch = LineDiff.compute(base, now).single()
        val r = LineDiff.revert(base, now, ch)
        assertEquals(r.text.indexOf("B -> C"), r.caret)
    }

    @Test fun `end of file without newline, both ways`() {
        val b1 = "a\nb"
        val c1 = "a\nb\nc\n"
        assertEquals(b1, revertAll(b1, c1))
        val b2 = "a\nb\n"
        val c2 = "a\nb\nc"
        assertEquals(b2, revertAll(b2, c2))
        assertEquals("a\nlast", revertAll("a\nlast", "a\nchanged"))
        assertEquals("a\nlast", revertAll("a\nlast", "a\nchanged\nmore\n"))
    }

    @Test fun `base text of a block`() {
        val now = base.replace("B -> C\nC -> D", "new")
        val ch = LineDiff.compute(base, now).single()
        assertEquals("B -> C\nC -> D\n", LineDiff.baseText(base, ch))
    }

    /** Rolling back block after block always ends exactly at the committed text. */
    @Test fun `random edits always revert to the base`() {
        val rnd = Random(42)
        val words = listOf("A -> B", "B -> C", "note", "end", "", "' c", "X")
        repeat(500) {
            val b = List(rnd.nextInt(0, 9)) { words.random(rnd) }.joinToString("\n") + if (rnd.nextBoolean()) "\n" else ""
            val lines = b.split('\n').toMutableList()
            repeat(rnd.nextInt(1, 5)) {
                when (rnd.nextInt(3)) {
                    0 -> lines.add(rnd.nextInt(0, lines.size + 1), words.random(rnd) + "!")
                    1 -> if (lines.isNotEmpty()) lines.removeAt(rnd.nextInt(lines.size))
                    else -> if (lines.isNotEmpty()) lines[rnd.nextInt(lines.size)] = "changed"
                }
            }
            val c = lines.joinToString("\n")
            assertEquals(b, revertAll(b, c), "base=${b.debug()} current=${c.debug()}")
        }
    }

    private fun revertAll(b: String, c: String): String {
        var cur = c
        var guard = 0
        while (true) {
            val changes = LineDiff.compute(b, cur)
            if (changes.isEmpty()) return cur
            val next = LineDiff.revert(b, cur, changes[0]).text
            assertTrue(next != cur, "revert made no progress: ${cur.debug()}")
            cur = next
            assertTrue(++guard < 50)
        }
    }

    private fun String.debug() = replace("\n", "⏎")
}
