package es.hugoalvarezajenjo.sproutstudio.core.os

/** Shortcut label in the platform's style: "⇧⌘F" on macOS, "Ctrl+Shift+F" elsewhere. */
fun shortcutHint(key: String, shift: Boolean = false, alt: Boolean = false): String =
    if (Os.isMac) buildString { if (alt) append('⌥'); if (shift) append('⇧'); append('⌘'); append(key) }
    else buildString { append("Ctrl+"); if (alt) append("Alt+"); if (shift) append("Shift+"); append(key) }
