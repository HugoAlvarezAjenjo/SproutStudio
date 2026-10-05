package es.hugoalvarezajenjo.sproutstudio.model

import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileOpsTest {

    private lateinit var dir: File
    private lateinit var p: ProjectState
    private val trashed = ArrayList<File>()
    private val realTrash = FileOps.trash

    @BeforeTest fun setUp() {
        dir = Files.createTempDirectory("fileops").toFile().canonicalFile
        File(dir, "a.puml").writeText("@startuml\nA -> B\n@enduml\n")
        File(dir, "docs").mkdirs(); File(dir, "docs/b.puml").writeText("@startuml\nB -> C\n@enduml\n")
        // Tests never touch the real Trash: record and delete.
        FileOps.trash = { f -> trashed += f; f.deleteRecursively() }
        p = ProjectState().apply { openRoot(dir) }
    }

    @AfterTest fun tearDown() { FileOps.trash = realTrash; dir.deleteRecursively() }

    @Test fun `new diagram is empty, gets puml, opens in a tab and shows in the tree`() = runBlocking<Unit> {
        val f = p.newDiagramIn(File(dir, "docs"), "flow").getOrThrow()
        assertEquals("flow.puml", f.name)
        assertEquals("", f.readText(), "no content unless a template was chosen")
        assertEquals(f.absoluteFile, p.active?.file?.absoluteFile)
        assertTrue(p.tree.any { it.file.absoluteFile == f.absoluteFile }, "parent folder was expanded")
    }

    @Test fun `new diagram from a template gets its text and the caret on the marker`() = runBlocking<Unit> {
        val t = es.hugoalvarezajenjo.sproutstudio.lang.Templates.all.first { it.id == "seq" }
        val f = p.newDiagramIn(dir, "login", t).getOrThrow()
        assertEquals(t.previewSource, f.readText())
        val d = p.active!!
        assertFalse(d.dirty)
        assertEquals("request", d.text.substring(d.value.selection.start, d.value.selection.start + 7))
    }

    @Test fun `quick new diagram is empty too`() = runBlocking<Unit> {
        val d = p.newDocument()
        assertEquals("", d.text)
        assertEquals("", d.file!!.readText())
    }

    @Test fun `a diagram extension is kept`() = runBlocking<Unit> {
        assertEquals("x.plantuml", p.newDiagramIn(dir, "x.plantuml").getOrThrow().name)
    }

    @Test fun `new empty folder is visible in the tree`() = runBlocking<Unit> {
        val f = p.newFolderIn(dir, "empty").getOrThrow()
        assertTrue(f.isDirectory)
        assertTrue(p.tree.any { it.file.absoluteFile == f && it.isDir })
    }

    @Test fun `names are validated`() = runBlocking<Unit> {
        assertNotNull(p.newFolderIn(dir, "docs").exceptionOrNull())   // exists
        assertNotNull(p.newFolderIn(dir, "a/b").exceptionOrNull())    // slash
        assertNotNull(p.newDiagramIn(dir, "  ").exceptionOrNull())    // blank
        assertNotNull(p.newDiagramIn(dir, "a").exceptionOrNull())     // a.puml exists
        assertNull(FileOps.validate(dir, "fresh.puml"))
    }

    @Test fun `renaming an open file keeps the tab, with its unsaved edits`() = runBlocking<Unit> {
        p.open(File(dir, "a.puml"))
        val d = p.active!!
        d.value = d.value.copy(text = "@startuml\nedited\n@enduml\n")
        val to = p.rename(File(dir, "a.puml"), "renamed.puml").getOrThrow()
        assertFalse(File(dir, "a.puml").exists())
        assertEquals(to, d.file?.absoluteFile)
        assertEquals("@startuml\nedited\n@enduml\n", to.readText())
        assertFalse(d.dirty)
    }

    @Test fun `renaming a folder moves the tabs inside it`() = runBlocking<Unit> {
        p.open(File(dir, "docs/b.puml"))
        p.toggleDir(File(dir, "docs"))
        p.rename(File(dir, "docs"), "notes").getOrThrow()
        assertEquals(File(dir, "notes/b.puml"), p.active?.file?.absoluteFile)
        assertTrue(File(dir, "notes") in p.expanded, "stays expanded under its new name")
    }

    @Test fun `case-only rename is allowed`() = runBlocking<Unit> {
        assertEquals("A.puml", p.rename(File(dir, "a.puml"), "A.puml").getOrThrow().name)
    }

    @Test fun `rename onto an existing name is refused and changes nothing`() = runBlocking<Unit> {
        File(dir, "c.puml").writeText("x")
        assertNotNull(p.rename(File(dir, "a.puml"), "c.puml").exceptionOrNull())
        assertEquals("x", File(dir, "c.puml").readText())
        assertTrue(File(dir, "a.puml").exists())
    }

    @Test fun `deleting goes to the Trash and closes tabs inside`() = runBlocking<Unit> {
        p.open(File(dir, "docs/b.puml"))
        p.open(File(dir, "a.puml"))
        p.delete(File(dir, "docs")).getOrThrow()
        assertEquals(listOf(File(dir, "docs")), trashed.map { it.absoluteFile })
        assertEquals(listOf("a.puml"), p.docs.map { it.name })
    }

    @Test fun `a failing Trash leaves the file`() = runBlocking<Unit> {
        FileOps.trash = { false }
        assertNotNull(p.delete(File(dir, "a.puml")).exceptionOrNull())
        assertTrue(File(dir, "a.puml").exists())
    }

    @Test fun `the project folder itself is protected`() = runBlocking<Unit> {
        assertNotNull(p.delete(dir).exceptionOrNull())
        assertNotNull(p.rename(dir, "other").exceptionOrNull())
        assertTrue(dir.exists())
    }
}

class FileMoveTest {
    private lateinit var dir: File
    private lateinit var p: ProjectState

    @BeforeTest fun setUp() {
        dir = Files.createTempDirectory("filemove").toFile().canonicalFile
        File(dir, "a.puml").writeText("@startuml\nA -> B\n@enduml\n")
        File(dir, "docs/inner").mkdirs(); File(dir, "docs/b.puml").writeText("@startuml\nB\n@enduml\n")
        File(dir, "other").mkdirs()
        p = ProjectState().apply { openRoot(dir) }
    }

    @AfterTest fun tearDown() { dir.deleteRecursively() }

    @Test fun `moving an open file keeps its tab and unsaved edits, and shows it`() = runBlocking<Unit> {
        p.open(File(dir, "a.puml"))
        val d = p.active!!
        d.value = d.value.copy(text = "@startuml\nmoved\n@enduml\n")
        val to = p.move(File(dir, "a.puml"), File(dir, "other")).getOrThrow()
        assertEquals(File(dir, "other/a.puml"), to)
        assertEquals(to, d.file?.absoluteFile)
        assertEquals("@startuml\nmoved\n@enduml\n", to.readText())
        assertTrue(p.tree.any { it.file.absoluteFile == to }, "destination folder expanded")
    }

    @Test fun `moving a folder carries the tabs inside it`() = runBlocking<Unit> {
        p.open(File(dir, "docs/b.puml"))
        p.move(File(dir, "docs"), File(dir, "other")).getOrThrow()
        assertEquals(File(dir, "other/docs/b.puml"), p.active?.file?.absoluteFile)
    }

    @Test fun `back up to the project folder`() = runBlocking<Unit> {
        assertEquals(File(dir, "b.puml"), p.move(File(dir, "docs/b.puml"), dir).getOrThrow())
    }

    @Test fun `refused moves change nothing`() = runBlocking<Unit> {
        assertNotNull(p.move(File(dir, "docs"), File(dir, "docs/inner")).exceptionOrNull())   // into itself
        assertNotNull(p.move(File(dir, "docs"), File(dir, "docs")).exceptionOrNull())
        File(dir, "other/a.puml").writeText("x")
        assertNotNull(p.move(File(dir, "a.puml"), File(dir, "other")).exceptionOrNull())       // name clash
        assertEquals("x", File(dir, "other/a.puml").readText())
        assertNotNull(p.move(dir, File(dir, "other")).exceptionOrNull())                       // the root
        assertTrue(File(dir, "a.puml").exists() && File(dir, "docs/b.puml").exists())
    }

    @Test fun `same folder is a no-op and canMove says so`() = runBlocking<Unit> {
        assertEquals(File(dir, "a.puml"), p.move(File(dir, "a.puml"), dir).getOrThrow())
        assertFalse(canMove(File(dir, "a.puml"), dir))
        assertFalse(canMove(File(dir, "docs"), File(dir, "docs/inner")))
        assertTrue(canMove(File(dir, "a.puml"), File(dir, "docs")))
    }
}

class CloseProjectTest {
    @Test fun `close project goes back to the welcome state`() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("close").toFile().canonicalFile
        try {
            File(dir, "a.puml").writeText("@startuml\nA -> B\n@enduml\n"); File(dir, "d").mkdirs()
            val p = ProjectState().apply { openRoot(dir); open(File(dir, "a.puml")); toggleDir(File(dir, "d")); rootCollapsed = true }
            p.closeProject()
            assertNull(p.root); assertTrue(p.docs.isEmpty()); assertTrue(p.tree.isEmpty()); assertTrue(p.expanded.isEmpty())
            assertFalse(p.rootCollapsed)
            // Reopening works like a fresh start.
            p.openRoot(dir)
            assertTrue(p.tree.any { it.file.name == "a.puml" })
        } finally { dir.deleteRecursively() }
    }
}
