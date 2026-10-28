package es.hugoalvarezajenjo.sproutstudio.core.os

import kotlin.test.Test
import kotlin.test.assertEquals

class ShortcutsTest {
    private fun both(key: String, shift: Boolean = false, alt: Boolean = false, command: Boolean = true) =
        shortcutHint(key, shift, alt, command, mac = true) to shortcutHint(key, shift, alt, command, mac = false)

    @Test fun `with command`() {
        assertEquals("⌘B" to "Ctrl+B", both("B"))
        assertEquals("⇧⌘F" to "Ctrl+Shift+F", both("F", shift = true))
        assertEquals("⌥⌘1" to "Ctrl+Alt+1", both("1", alt = true))
    }

    @Test fun `without command`() {
        assertEquals("⌥F7" to "Alt+F7", both("F7", alt = true, command = false))
        assertEquals("⇧F6" to "Shift+F6", both("F6", shift = true, command = false))
        assertEquals("⌥↑" to "Alt+↑", both("↑", alt = true, command = false))
    }
}
