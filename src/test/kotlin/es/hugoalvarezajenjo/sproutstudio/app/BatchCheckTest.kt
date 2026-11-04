package es.hugoalvarezajenjo.sproutstudio.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `--check` with real PlantUML, in a temporary folder. */
class BatchCheckTest : BatchTest() {

    private fun check(vararg inputs: File, batch: Batch = this.batch): ExitCode = batch.run(CommandLine.Check(inputs.toList()))

    @Test
    fun `valid diagrams pass and nothing is written`() {
        write("a.puml", diagram() + "\n" + diagram("class Pizza"))
        write("billing/b.puml", diagram())
        assertEquals(ExitCode.OK, check(dir))
        assertTrue("3 OK." in stdout, stdout)
        assertEquals(setOf("a.puml", "billing/b.puml"), files())
    }

    @Test
    fun `a broken diagram fails the check with its line, counted once`() {
        val a = write("a.puml", diagram() + "\n" + diagram("class {\ncla sad"))
        assertEquals(ExitCode.FAILED, check(a))
        assertTrue(Regex("""a\.puml:\d+: """).containsMatchIn(stderr), stderr)
        assertTrue("1 OK, 1 with errors." in stdout, stdout)
        assertEquals(setOf("a.puml"), files())
    }

    @Test
    fun `a remote include fails every diagram of the file`() {
        val a = write("a.puml", "!include https://example.com/x.puml\n" + diagram() + "\n" + diagram())
        assertEquals(ExitCode.FAILED, check(a))
        assertTrue("remote !include refused" in stderr, stderr)
        assertTrue("0 OK, 2 with errors." in stdout, stdout)
    }

    @Test
    fun `no diagrams fails, so an empty or mistyped folder doesn't pass CI`() {
        write("common/style.iuml", "skinparam shadowing false\n")
        assertEquals(ExitCode.FAILED, check(dir))
        assertTrue("No diagrams found." in stderr, stderr)
    }

    @Test
    fun `a missing input is a usage error`() {
        assertEquals(ExitCode.USAGE, check(dir.resolve("nope")))
        assertTrue("Not found:" in stderr, stderr)
    }

    @Test
    fun `each run counts on its own`() {
        write("a.puml", diagram())
        val b = batch
        assertEquals(ExitCode.OK, check(dir, batch = b))
        assertEquals(ExitCode.OK, check(dir, batch = b))
        assertEquals(2, stdout.lines().count { it == "1 OK." }, stdout)
    }
}
