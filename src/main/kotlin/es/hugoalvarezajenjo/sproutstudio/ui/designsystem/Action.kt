package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import es.hugoalvarezajenjo.sproutstudio.core.os.shortcutHint
import org.jetbrains.compose.resources.StringResource

/**
 * Something the user can do, defined once and shown in several places: the menu bar, the
 * command palette, right-click menus. Each place picks the actions it shows and their order;
 * the words, the shortcut, when it's enabled and what it does live here.
 *
 * [title] is a resource so each place resolves it its own way (the menu bar follows a language
 * change live). [restoresFocus] is for the palette: false when the action moves focus itself.
 */
data class Action(
    val id: String,
    val title: StringResource,
    val shortcut: Shortcut? = null,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val restoresFocus: Boolean = true,
    val run: () -> Unit,
)

/**
 * A key combination: [key] for the menu bar to listen to, [label] for how the key is written
 * ("F", "-", "←", "F7"). [command] is ⌘ on macOS, Ctrl elsewhere.
 */
data class Shortcut(val key: Key, val label: String, val command: Boolean = true, val shift: Boolean = false, val alt: Boolean = false) {
    /** "⇧⌘F" on macOS, "Ctrl+Shift+F" elsewhere. */
    val hint: String get() = shortcutHint(label, shift = shift, alt = alt, command = command)

    val keyShortcut: KeyShortcut get() = KeyShortcut(key, meta = command && Os.isMac, ctrl = command && !Os.isMac, alt = alt, shift = shift)
}

/** The action as a right-click menu entry. */
fun Action.toMenuItem(): MenuItem = MenuItem(text(title), icon, shortcut?.hint, isEnabled = enabled, onClick = run)
