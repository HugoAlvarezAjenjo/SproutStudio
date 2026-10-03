package es.hugoalvarezajenjo.sproutstudio.git

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Undo, amend, history, stash, .gitignore, text diff. */
class GitHistoryTest {
    private val dir: File = Files.createTempDirectory("sprout-hist").toFile()
    private val opened = ArrayList<GitRepo>()
    @AfterTest fun cleanup() { opened.forEach { it.close() }; dir.deleteRecursively() }

    private fun repo(gitignore: Boolean = false): GitRepo {
        GitRepo.init(dir, gitignore).close()
        File(dir, ".git/config").appendText("[user]\n\tname = Test\n\temail = t@e\n[commit]\n\tgpgsign = false\n")
        return GitRepo.find(dir)!!.also { opened += it }
    }

    private fun file(name: String, text: String) = File(dir, name).apply { writeText(text) }
    private fun GitRepo.typeOf(path: String) = status().changes.firstOrNull { it.path == path }?.type

    @Test fun `undo last commit keeps the changes ready to commit again`() {
        val r = repo()
        val a = file("a.puml", "1\n"); r.commit(listOf(a), "one")
        a.writeText("2\n"); val b = file("b.puml", "B\n")
        r.commit(listOf(a, b), "two")
        val undone = r.undoLastCommit()
        assertEquals("two", undone.message)
        assertEquals("one", r.lastCommit()!!.message)
        assertEquals("2\n", a.readText()) // disk untouched
        assertEquals(ChangeType.MODIFIED, r.typeOf("a.puml"))
        assertEquals(ChangeType.ADDED, r.typeOf("b.puml"))
        // ...and committing again works with the usual --only selection.
        r.commit(listOf(a), "two, only a")
        assertEquals("2\n", r.headText(a))
        assertEquals(ChangeType.ADDED, r.typeOf("b.puml"))
    }

    @Test fun `the first commit can't be undone`() {
        val r = repo()
        r.commit(listOf(file("a.puml", "1\n")), "one")
        assertFailsWith<IllegalArgumentException> { r.undoLastCommit() }
    }

    @Test fun `amend adds the selected files to the last commit, or just rewords it`() {
        val r = repo()
        val a = file("a.puml", "1\n"); r.commit(listOf(a), "one")
        val b = file("b.puml", "B\n")
        r.commit(listOf(b), "one plus b", amend = true)
        val last = r.lastCommit()!!
        assertEquals("one plus b", last.message)
        assertEquals(0, last.parents) // still the first commit, not a new one
        assertEquals("B\n", r.headText(b))
        r.commit(emptyList(), "reworded", amend = true)
        assertEquals("reworded", r.lastCommit()!!.message)
        assertEquals(1, r.history(a).size)
    }

    @Test fun `history lists the commits of a file, newest first, with its old text`() {
        val r = repo()
        val a = file("a.puml", "v1\n"); r.commit(listOf(a), "first")
        val other = file("o.puml", "o\n"); r.commit(listOf(other), "unrelated")
        a.writeText("v2\n"); r.commit(listOf(a), "second")
        val h = r.history(a)
        assertEquals(listOf("second", "first"), h.map { it.message })
        assertEquals("v1\n", r.textAt(h[1].id, a))
        assertEquals("v1\n", r.textAt(h[0].id + "^", a))
        assertNull(r.textAt(h[1].id + "^", a)) // before the file existed
    }

    @Test fun `stash puts changes aside and pop brings them back`() {
        val r = repo()
        val a = file("a.puml", "1\n"); r.commit(listOf(a), "one")
        assertFalse(r.stash(null)) // nothing to stash
        a.writeText("wip\n")
        assertTrue(r.stash("half-done idea"))
        assertEquals("1\n", a.readText())
        assertTrue(r.status().changes.isEmpty())
        assertEquals(1, r.stashes().size)
        assertTrue(r.stashes()[0].message.contains("half-done idea"))
        r.popStash(0)
        assertEquals("wip\n", a.readText())
        assertTrue(r.stashes().isEmpty())
        // drop
        r.stash("throw away")
        r.dropStash(0)
        assertTrue(r.stashes().isEmpty())
        assertEquals("1\n", a.readText())
    }

    @Test fun `init can add a starter gitignore without replacing an existing one`() {
        repo(gitignore = true)
        assertTrue(File(dir, ".gitignore").readText().contains(".DS_Store"))
        val other = Files.createTempDirectory("sprout-ig").toFile()
        try {
            File(other, ".gitignore").writeText("mine\n")
            GitRepo.init(other, gitignore = true).close()
            assertEquals("mine\n", File(other, ".gitignore").readText())
        } finally { other.deleteRecursively() }
    }

    @Test fun `unified text diff rows`() {
        val rows = LineDiff.rows("a\nb\nc\n", "a\nB\nc\nd\n")
        assertEquals(
            listOf(
                LineDiff.Row(LineDiff.Row.Kind.SAME, 1, 1, "a"),
                LineDiff.Row(LineDiff.Row.Kind.DELETED, 2, null, "b"),
                LineDiff.Row(LineDiff.Row.Kind.ADDED, null, 2, "B"),
                LineDiff.Row(LineDiff.Row.Kind.SAME, 3, 3, "c"),
                LineDiff.Row(LineDiff.Row.Kind.ADDED, null, 4, "d"),
            ),
            rows,
        )
    }
}
