package es.hugoalvarezajenjo.sproutstudio.platform

import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.io.File
import java.lang.reflect.Modifier
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The commands LinuxPlatform runs, checked without starting any (they run on any OS). */
class LinuxPlatformTest {

    private val calls = mutableListOf<List<String>>()

    /** A platform whose commands answer [exit] by program name (0 when not listed). */
    private fun platform(vararg exit: Pair<String, Int>) = LinuxPlatform(
        run = { cmd -> calls += cmd; exit.toMap()[cmd.first()] ?: 0 },
        background = { it() },
    )

    private val file = File("/home/ana/My Diagrams/it's here.puml")

    /** The paths above are Unix ones: Windows would make them D:\home\ana\..., so skip there. */
    @BeforeTest
    fun unixPathsOnly() = assumeFalse(Os.family == Os.Family.WINDOWS)

    @Test
    fun `the Trash goes through gio, with the path as one argument`() {
        assertTrue(platform().moveToTrash(file))
        assertEquals(listOf(listOf("gio", "trash", "--", file.absolutePath)), calls)
    }

    @Test
    fun `a failed gio trash reports false, so nothing is deleted for good`() {
        assertFalse(platform("gio" to 1).moveToTrash(file))
        assertFalse(platform("gio" to -1).moveToTrash(file)) // gio missing or hung
    }

    @Test
    fun `reveal asks the file manager over D-Bus to select the file`() {
        platform().reveal(file)
        val cmd = calls.single()
        assertEquals("gdbus", cmd.first())
        assertEquals("org.freedesktop.FileManager1.ShowItems", cmd[cmd.indexOf("--method") + 1])
        // A GVariant array of one URI: spaces percent-encoded, the quote escaped.
        assertEquals("['file:///home/ana/My%20Diagrams/it\\'s%20here.puml']", cmd[cmd.size - 2])
        assertEquals("", cmd.last()) // startup id
    }

    @Test
    fun `without a FileManager1 service, reveal opens the folder instead`() {
        platform("gdbus" to 1).reveal(file)
        assertEquals(listOf("xdg-open", "/home/ana/My Diagrams"), calls.last())
    }

    private val hooks = AppHooks(openFiles = {}, openSettings = {}, beforeQuit = {}, dark = false)

    @Test
    fun `integrate names the windows after the installer's menu entry`() {
        val classes = mutableListOf<String>()
        LinuxPlatform(desktopId = "sproutstudio-SproutStudio", setWindowClass = { classes += it }).integrate(hooks)
        assertEquals(listOf("sproutstudio-SproutStudio"), classes)
    }

    @Test
    fun `without a menu entry id, integrate keeps AWT's window class`() {
        val classes = mutableListOf<String>()
        LinuxPlatform(desktopId = null, setWindowClass = { classes += it }).integrate(hooks)
        LinuxPlatform(desktopId = " ", setWindowClass = { classes += it }).integrate(hooks)
        assertEquals(emptyList(), classes)
    }

    /** The field setAwtAppClassName writes; if a JDK renames it, the dock icon breaks silently. */
    @Test
    fun `the X11 toolkit still keeps the window class where we set it`() {
        assumeTrue(Os.family == Os.Family.LINUX && !GraphicsEnvironment.isHeadless())
        val toolkit = Toolkit.getDefaultToolkit()
        assumeTrue(toolkit.javaClass.name == "sun.awt.X11.XToolkit") // not a native Wayland toolkit
        val field = toolkit.javaClass.getDeclaredField("awtAppClassName")
        assertTrue(Modifier.isStatic(field.modifiers))
        assertEquals(String::class.java, field.type)
    }

    @Test
    fun `the terminal is the first one installed, started in the folder`() {
        val started = mutableListOf<Pair<List<String>, File>>()
        val installed = setOf("konsole", "xterm")
        val dir = File("/home/ana/My Diagrams")
        LinuxPlatform(start = { cmd, d -> started += cmd to d; cmd.first() in installed }).openTerminal(dir)
        // Tried in order until one starts, then no more.
        assertEquals(listOf("x-terminal-emulator", "gnome-terminal", "konsole"), started.map { it.first.single() })
        assertTrue(started.all { it.second == dir })
    }
}
