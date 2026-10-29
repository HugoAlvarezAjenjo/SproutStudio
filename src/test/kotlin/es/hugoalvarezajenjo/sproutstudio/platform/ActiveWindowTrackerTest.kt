package es.hugoalvarezajenjo.sproutstudio.platform

import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.Window
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Which window another launch brings forward. Windows are made but never shown. */
class ActiveWindowTrackerTest {

    private val tracker = ActiveWindowTracker()
    private lateinit var editor: Frame
    private lateinit var preview: Frame
    private lateinit var popup: Window

    @BeforeTest
    fun windows() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        editor = Frame(); preview = Frame(); popup = Window(editor)
    }

    @AfterTest
    fun dispose() {
        if (::editor.isInitialized) listOf(popup, editor, preview).forEach(Window::dispose)
    }

    @Test
    fun `the window the user was last in comes forward`() {
        tracker.activated(editor)
        tracker.activated(preview)
        assertSame(preview, tracker.frontmost(shown = listOf(editor, preview)))
    }

    @Test
    fun `if that window was closed, a shown frame comes forward instead of a popup`() {
        tracker.activated(preview)
        assertSame(editor, tracker.frontmost(shown = listOf(popup, editor)))
    }

    @Test
    fun `before the user was in any window, a shown one still comes forward`() {
        assertSame(editor, tracker.frontmost(shown = listOf(editor)))
    }

    @Test
    fun `with nothing shown there is nothing to bring forward`() {
        tracker.activated(editor)
        assertNull(tracker.frontmost(shown = emptyList()))
    }
}
