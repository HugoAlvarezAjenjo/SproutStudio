package es.hugoalvarezajenjo.sproutstudio.platform

import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The macOS `sproutstudio` script, run for real with sh against a fake app bundle. */
class ShellCommandTest {

    private val dir: File = Files.createTempDirectory("cmd").toFile()

    @AfterTest
    fun cleanUp() { dir.deleteRecursively() }

    /** A bundle whose binary prints each argument on its own line, then exits with 3. */
    private fun fakeApp(name: String): Path {
        val bin = dir.resolve("$name/Contents/MacOS/SproutStudio").apply { parentFile.mkdirs() }
        bin.writeText("#!/bin/sh\nfor a in \"$@\"; do echo \"\$a\"; done\nexit 3\n")
        bin.setExecutable(true)
        return dir.resolve(name).toPath()
    }

    private fun run(script: String, vararg args: String): Pair<Int, List<String>> {
        val f = dir.resolve("sproutstudio").apply { writeText(script); setExecutable(true) }
        val p = ProcessBuilder(listOf(f.path) + args).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readLines()
        return p.waitFor() to out
    }

    @Test
    fun `an option runs the app binary with every argument, and its exit code comes back`() {
        assumeFalse(Os.family == Os.Family.WINDOWS, "a POSIX shell script")
        val script = ShellCommand.macScript(fakeApp("SproutStudio.app"))
        assertEquals(3 to listOf("--check", "my docs", "a.puml"), run(script, "--check", "my docs", "a.puml"))
        // Also when the option is not first: the binary reports a wrong command line, not `open`.
        assertEquals(3 to listOf("a.puml", "--export"), run(script, "a.puml", "--export"))
    }

    @Test
    fun `an app path with quotes and spaces stays one word`() {
        assumeFalse(Os.family == Os.Family.WINDOWS, "a POSIX shell script")
        val script = ShellCommand.macScript(fakeApp("Hugo's \$HOME `x`.app"))
        assertEquals(3 to listOf("--help"), run(script, "--help"))
    }

    @Test
    fun `the script carries the marker, so only our own file is ever replaced`() {
        assertTrue(ShellCommand.isOurs(ShellCommand.macScript(Path.of("/Applications/SproutStudio.app"))))
        assertFalse(ShellCommand.isOurs("#!/bin/sh\nexec something-else \"$@\"\n"))
    }

    @Test
    fun `the bundle is found from the binary inside it`() {
        assertEquals(
            Path.of("/Applications/SproutStudio.app"),
            MacPlatform.appBundle("/Applications/SproutStudio.app/Contents/MacOS/SproutStudio"),
        )
        assertNull(MacPlatform.appBundle("/usr/bin/java")) // gradlew run: no bundle, no command
        assertNull(MacPlatform.appBundle(null))
    }
}
