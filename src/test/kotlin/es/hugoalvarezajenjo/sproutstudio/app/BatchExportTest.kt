package es.hugoalvarezajenjo.sproutstudio.app

import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** `--export` with real PlantUML, in a temporary folder. */
class BatchExportTest {

    private val dir: File = Files.createTempDirectory("export").toFile()
    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()

    @AfterTest
    fun cleanUp() { dir.deleteRecursively() }

    private fun write(path: String, text: String) = dir.resolve(path).apply { parentFile.mkdirs(); writeText(text) }

    private fun export(vararg inputs: File, format: ExportFormat = ExportFormat.SVG, to: File? = null): Int =
        BatchExport(PrintStream(out, true), PrintStream(err, true), workingDir = dir)
            .run(CommandLine.Export(inputs.toList(), format, to))

    private val stdout get() = out.toString()
    private val stderr get() = err.toString()

    private fun diagram(body: String = "A -> B: hi") = "@startuml\n$body\n@enduml\n"

    private fun files(root: File = dir) = root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()

    @Test
    fun `one diagram becomes one image next to it`() {
        val a = write("a.puml", diagram())
        assertEquals(BatchExport.EXIT_OK, export(a))
        assertTrue(dir.resolve("a.svg").readText().startsWith("<svg"))
        assertTrue("a.puml → a.svg" in stdout, stdout)
        assertTrue("Exported 1." in stdout, stdout)
    }

    @Test
    fun `several diagrams in a file are numbered as in the preview`() {
        val a = write("a.puml", diagram() + "\n" + diagram("class Pizza"))
        assertEquals(BatchExport.EXIT_OK, export(a, format = ExportFormat.PNG))
        assertEquals(setOf("a.puml", "a-1.png", "a-2.png"), files())
    }

    @Test
    fun `a folder exports its diagrams, skipping include files and build folders`() {
        write("a.puml", diagram())
        write("common/style.iuml", "skinparam shadowing false\n") // no @startuml: an include
        write("build/old.puml", diagram())
        write(".hidden/x.puml", diagram())
        assertEquals(BatchExport.EXIT_OK, export(dir))
        assertTrue(dir.resolve("a.svg").exists())
        assertFalse(dir.resolve("common/style.svg").exists())
        assertFalse(dir.resolve("build/old.svg").exists())
        assertFalse(dir.resolve(".hidden/x.svg").exists())
    }

    @Test
    fun `under out the subfolders are kept, so two files with the same name don't collide`() {
        val src = dir.resolve("src")
        write("src/a.puml", diagram())
        write("src/billing/a.puml", diagram())
        val target = dir.resolve("out")
        assertEquals(BatchExport.EXIT_OK, export(src, to = target))
        assertEquals(setOf("a.svg", "billing/a.svg"), files(target))
    }

    @Test
    fun `a broken diagram is reported like a compiler error, once, and the others still export`() {
        val a = write("a.puml", diagram() + "\n" + diagram("class {\ncla sad"))
        assertEquals(BatchExport.EXIT_FAILED, export(a))
        assertTrue(dir.resolve("a-1.svg").exists())
        assertFalse(dir.resolve("a-2.svg").exists())
        assertTrue(Regex("""a\.puml:\d+: """).containsMatchIn(stderr), stderr)
        assertTrue("Exported 1, failed 1." in stdout, stdout) // two errors, still one diagram
    }

    @Test
    fun `a remote include is refused once for the whole file`() {
        val a = write("a.puml", "!include https://example.com/x.puml\n" + diagram() + diagram())
        assertEquals(BatchExport.EXIT_FAILED, export(a))
        assertEquals(1, stderr.lines().count { "remote !include refused" in it }, stderr)
        assertTrue("Exported 0, failed 2." in stdout, stdout)
        assertEquals(setOf("a.puml"), files())
    }

    @Test
    fun `a file named on the command line without a diagram is an error`() {
        val a = write("notes.puml", "just text\n")
        assertEquals(BatchExport.EXIT_FAILED, export(a))
        assertTrue("notes.puml: no @start… @end… diagram" in stderr, stderr)
    }

    @Test
    fun `a missing input stops before exporting anything`() {
        val a = write("a.puml", diagram())
        assertEquals(BatchExport.EXIT_USAGE, export(a, dir.resolve("nope.puml")))
        assertTrue("Not found:" in stderr, stderr)
        assertFalse(dir.resolve("a.svg").exists())
    }

    @Test
    fun `a folder with no diagrams says so`() {
        assertEquals(BatchExport.EXIT_FAILED, export(dir))
        assertTrue("No diagrams found." in stderr, stderr)
    }

    @Test
    fun `local includes resolve next to the diagram`() {
        write("common/style.iuml", "skinparam shadowing false\n")
        val a = write("a.puml", diagram("!include common/style.iuml\nA -> B"))
        assertEquals(BatchExport.EXIT_OK, export(a, format = ExportFormat.PDF))
        assertTrue(dir.resolve("a.pdf").readBytes().decodeToString(0, 4) == "%PDF")
    }
}
