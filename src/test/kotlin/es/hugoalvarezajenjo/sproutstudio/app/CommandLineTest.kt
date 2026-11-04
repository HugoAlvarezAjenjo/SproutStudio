package es.hugoalvarezajenjo.sproutstudio.app

import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** What the program arguments ask for. Nothing runs here. */
class CommandLineTest {

    private fun parse(vararg args: String) = CommandLine.parse(args.toList())

    @Test
    fun `plain paths open the app, as a double-click does`() {
        assertEquals(CommandLine.Gui(emptyList()), parse())
        assertEquals(CommandLine.Gui(listOf(File("a.puml"), File("docs"))), parse("a.puml", "docs"))
    }

    @Test
    fun `export defaults to SVG next to each diagram`() {
        assertEquals(CommandLine.Export(listOf(File("docs")), ExportFormat.SVG, out = null), parse("--export", "docs"))
    }

    @Test
    fun `format and out can go anywhere after export, among the inputs`() {
        assertEquals(
            CommandLine.Export(listOf(File("a.puml"), File("docs")), ExportFormat.PDF, File("out")),
            parse("--export", "a.puml", "--format", "PDF", "docs", "--out", "out"),
        )
    }

    @Test
    fun `help wins over anything else`() {
        assertEquals(CommandLine.Help, parse("--help"))
        assertEquals(CommandLine.Help, parse("--export", "docs", "-h"))
    }

    @Test
    fun `wrong arguments say what is wrong`() {
        fun invalid(vararg args: String) = assertIs<CommandLine.Invalid>(parse(*args)).message
        assertEquals("--export needs at least one file or folder", invalid("--export"))
        assertEquals("--export needs at least one file or folder", invalid("--export", "--format", "png"))
        assertEquals("unknown format: gif (svg, png or pdf)", invalid("--export", "docs", "--format", "gif"))
        assertEquals("--out needs a value", invalid("--export", "docs", "--out"))
        assertEquals("--format needs a value", invalid("--export", "docs", "--format", "--out", "x"))
        assertEquals("unknown option: --zip", invalid("--export", "docs", "--zip"))
        assertEquals("unknown option: --verbose", invalid("--verbose", "docs"))
    }

    @Test
    fun `the usage text names every option`() {
        listOf("--export", "--format", "--out", "--help", "svg", "png", "pdf").forEach {
            assertTrue(it in CommandLine.USAGE, "usage lacks $it")
        }
    }
}
