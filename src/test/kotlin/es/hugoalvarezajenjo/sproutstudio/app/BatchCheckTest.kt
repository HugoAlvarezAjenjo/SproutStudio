package es.hugoalvarezajenjo.sproutstudio.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `--check` with real PlantUML, in a temporary folder. */
class BatchCheckTest {

    private val dir: File = Files.createTempDirectory("check").toFile()
    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()

    @AfterTest
    fun cleanUp() { dir.deleteRecursively() }

    private fun write(path: String, text: String) = dir.resolve(path).apply { parentFile.mkdirs(); writeText(text) }

    private fun check(vararg inputs: File): Int =
        Batch(PrintStream(out, true), PrintStream(err, true), workingDir = dir).run(CommandLine.Check(inputs.toList()))

    private val stdout get() = out.toString()
    private val stderr get() = err.toString()

    private fun diagram(body: String = "A -> B: hi") = "@startuml\n$body\n@enduml\n"

    private fun files() = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.toSet()

    @Test
    fun `valid diagrams pass and nothing is written`() {
        write("a.puml", diagram() + "\n" + diagram("class Pizza"))
        write("billing/b.puml", diagram())
        assertEquals(Batch.EXIT_OK, check(dir))
        assertTrue("3 OK." in stdout, stdout)
        assertEquals(setOf("a.puml", "billing/b.puml"), files())
    }

    @Test
    fun `a broken diagram fails the check with its line, counted once`() {
        val a = write("a.puml", diagram() + "\n" + diagram("class {\ncla sad"))
        assertEquals(Batch.EXIT_FAILED, check(a))
        assertTrue(Regex("""a\.puml:\d+: """).containsMatchIn(stderr), stderr)
        assertTrue("1 OK, 1 with errors." in stdout, stdout)
        assertEquals(setOf("a.puml"), files())
    }

    @Test
    fun `a remote include fails every diagram of the file`() {
        val a = write("a.puml", "!include https://example.com/x.puml\n" + diagram() + "\n" + diagram())
        assertEquals(Batch.EXIT_FAILED, check(a))
        assertTrue("remote !include refused" in stderr, stderr)
        assertTrue("0 OK, 2 with errors." in stdout, stdout)
    }

    @Test
    fun `no diagrams fails, so an empty or mistyped folder doesn't pass CI`() {
        write("common/style.iuml", "skinparam shadowing false\n")
        assertEquals(Batch.EXIT_FAILED, check(dir))
        assertTrue("No diagrams found." in stderr, stderr)
    }

    @Test
    fun `a missing input is a usage error`() {
        assertEquals(Batch.EXIT_USAGE, check(dir.resolve("nope")))
        assertTrue("Not found:" in stderr, stderr)
    }
}
