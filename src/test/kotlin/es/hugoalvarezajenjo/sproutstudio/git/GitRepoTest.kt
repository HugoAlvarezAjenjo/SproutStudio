package es.hugoalvarezajenjo.sproutstudio.git

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitRepoTest {
    private val dir: File = Files.createTempDirectory("sprout-git").toFile()
    private val opened = ArrayList<GitRepo>()

    @AfterTest fun cleanup() {
        opened.forEach { it.close() }
        dir.deleteRecursively()
    }

    /** A repo with a fixed author, so tests don't depend on the machine's git config. */
    private fun newRepo(): GitRepo {
        val r = GitRepo.init(dir).also { opened += it }
        File(dir, ".git/config").appendText("[user]\n\tname = Test\n\temail = test@example.com\n[commit]\n\tgpgsign = false\n")
        return GitRepo.find(dir)!!.also { opened += it }.also { r.close() }
    }

    private fun write(path: String, text: String) = File(dir, path).apply { parentFile.mkdirs(); writeText(text) }

    private fun GitRepo.typeOf(path: String) = status().changes.firstOrNull { it.path == path }?.type

    @Test fun `find returns null outside a repo and the repo from a subfolder`() {
        assertNull(GitRepo.find(dir))
        newRepo()
        val sub = File(dir, "docs/deep").apply { mkdirs() }
        val found = GitRepo.find(sub)
        assertNotNull(found); opened += found
        assertEquals(dir.canonicalFile, found.workTree.canonicalFile)
    }

    @Test fun `a fresh repo is on main with everything unversioned`() {
        val r = newRepo()
        write("a.puml", "@startuml\nA -> B\n@enduml\n")
        val s = r.status()
        assertEquals("main", s.branch)
        assertNull(s.head)
        assertEquals(listOf("a.puml" to ChangeType.UNVERSIONED), s.changes.map { it.path to it.type })
    }

    @Test fun `first commit takes only the selected files`() {
        val r = newRepo()
        val a = write("a.puml", "A\n")
        write("b.puml", "B\n")
        val id = r.commit(listOf(a), "first")
        assertEquals(7, id.length)
        assertEquals("A\n", r.headText(a))
        assertNull(r.headText(File(dir, "b.puml")))
        assertEquals(ChangeType.UNVERSIONED, r.typeOf("b.puml"))
        assertNull(r.typeOf("a.puml"))
    }

    @Test fun `modified, deleted and added states and committing them`() {
        val r = newRepo()
        val a = write("a.puml", "A\n")
        val b = write("b.puml", "B\n")
        r.commit(listOf(a, b), "init")

        a.writeText("A2\n")
        b.delete()
        val c = write("sub/c.puml", "C\n")
        assertEquals(ChangeType.MODIFIED, r.typeOf("a.puml"))
        assertEquals(ChangeType.DELETED, r.typeOf("b.puml"))
        assertEquals(ChangeType.UNVERSIONED, r.typeOf("sub/c.puml"))

        // Commit only the deletion and the new file; the edit to a.puml stays pending.
        r.commit(listOf(b, c), "second")
        assertEquals(ChangeType.MODIFIED, r.typeOf("a.puml"))
        assertNull(r.typeOf("b.puml"))
        assertNull(r.typeOf("sub/c.puml"))
        assertEquals("C\n", r.headText(c))
        assertEquals("A\n", r.headText(a))

        r.commit(listOf(a), "third")
        assertTrue(r.status().changes.isEmpty())
        assertEquals("A2\n", r.headText(a))
    }

    @Test fun `rollback restores HEAD and never deletes a new file`() {
        val r = newRepo()
        val a = write("a.puml", "A\n")
        r.commit(listOf(a), "init")
        a.writeText("changed\n")
        r.rollback(a)
        assertEquals("A\n", a.readText())

        a.delete()
        r.rollback(a)
        assertEquals("A\n", a.readText())

        // New file, added to the index: rollback un-adds it but keeps it on disk.
        val n = write("n.puml", "new\n")
        org.eclipse.jgit.api.Git.open(dir).use { it.add().addFilepattern("n.puml").call() }
        assertEquals(ChangeType.ADDED, r.typeOf("n.puml"))
        r.rollback(n)
        assertTrue(n.exists())
        assertEquals(ChangeType.UNVERSIONED, r.typeOf("n.puml"))
    }

    @Test fun `status is scoped to the project folder and reports ignored files`() {
        val r = newRepo()
        write(".gitignore", "out/\n*.png\n")
        write("docs/a.puml", "A\n")
        write("other/b.puml", "B\n")
        write("docs/pic.png", "x")
        write("docs/out/x.puml", "x")
        val s = r.status(File(dir, "docs"))
        assertEquals(listOf("docs/a.puml"), s.changes.map { it.path })
        assertTrue(IgnoredPath("docs/pic.png") in s.ignored)
        assertTrue(IgnoredPath("docs/out") in s.ignored)
    }

    @Test fun `author comes from the config`() {
        val r = newRepo()
        val who = r.author()
        assertEquals("Test", who.name)
        assertEquals("test@example.com", who.email)
        assertFalse(who.implicit)
    }
}
