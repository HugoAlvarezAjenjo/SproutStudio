package es.hugoalvarezajenjo.sproutstudio.core.os

/**
 * Shortcut label in the platform's style: "⇧⌘F" on macOS, "Ctrl+Shift+F" elsewhere. With
 * [command] false there is no ⌘/Ctrl: "⌥F7" / "Alt+F7".
 */
fun shortcutHint(key: String, shift: Boolean = false, alt: Boolean = false, command: Boolean = true): String =
    shortcutHint(key, shift, alt, command, mac = Os.isMac)

/** [shortcutHint] for a given OS, so both styles can be tested anywhere. */
internal fun shortcutHint(key: String, shift: Boolean, alt: Boolean, command: Boolean, mac: Boolean): String =
    if (mac) buildString { if (alt) append('⌥'); if (shift) append('⇧'); if (command) append('⌘'); append(key) }
    else buildString { if (command) append("Ctrl+"); if (alt) append("Alt+"); if (shift) append("Shift+"); append(key) }
