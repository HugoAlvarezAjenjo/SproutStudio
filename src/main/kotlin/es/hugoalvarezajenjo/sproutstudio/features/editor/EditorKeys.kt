package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.BlockComment
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.EditOps
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.MoveLines

/**
 * The editor's own keys, seen before the text field. True when the key was handled.
 *
 * Order matters: moving lines comes before the popups (which would take the arrows), and an
 * open popup takes ↑ ↓ ⏎ esc before the editing shortcuts do.
 */
internal fun CodeEditorState.handleKey(
    ev: KeyEvent,
    find: FindState,
    closeFind: () -> Unit,
    linkAt: (Offset) -> IntRange?,
): Boolean {
    if (ev.isLinkKey) {
        // ⌘ pressed or let go over a name: underline it without waiting for the mouse to move.
        linkKeyDown = ev.type == KeyEventType.KeyDown
        link = if (linkKeyDown) hoverPos?.let(linkAt) else null
        return false
    }
    if (ev.type != KeyEventType.KeyDown) return false
    return moveLinesKey(ev) || usagesKey(ev) || completionKey(ev) || findKey(ev, find, closeFind) || editingKey(ev)
}

private val KeyEvent.isLinkKey: Boolean
    get() = if (Os.isMac) key == Key.MetaLeft || key == Key.MetaRight else key == Key.CtrlLeft || key == Key.CtrlRight

private val KeyEvent.isCmd: Boolean get() = isMetaPressed || isCtrlPressed

/** ⌥↑ / ⌥↓. */
private fun CodeEditorState.moveLinesKey(ev: KeyEvent): Boolean {
    if (!ev.isAltPressed || ev.isCmd || ev.isShiftPressed) return false
    val moved = when (ev.key) {
        Key.DirectionUp -> MoveLines.up(doc.value)
        Key.DirectionDown -> MoveLines.down(doc.value)
        else -> return false
    }
    closePopups()
    update(moved, typed = false)
    return true
}

/** The Find Usages list, while it is open. */
private fun CodeEditorState.usagesKey(ev: KeyEvent): Boolean {
    val nav = doc.nav
    if (nav.usages == null) return false
    when (ev.key) {
        Key.DirectionDown -> nav.moveSelection(1)
        Key.DirectionUp -> nav.moveSelection(-1)
        Key.Enter -> doc.openSelectedUsage()
        Key.Escape -> nav.closeUsages()
        else -> return false
    }
    return true
}

/** The completion popup, while it is open. */
private fun CodeEditorState.completionKey(ev: KeyEvent): Boolean {
    if (completion == null) return false
    when (ev.key) {
        Key.DirectionDown -> moveCompletion(1)
        Key.DirectionUp -> moveCompletion(-1)
        Key.Enter, Key.Tab -> acceptSelected()
        Key.Escape -> closeCompletion()
        else -> return false
    }
    return true
}

private fun findKey(ev: KeyEvent, find: FindState, closeFind: () -> Unit): Boolean {
    if (ev.key != Key.Escape || !find.visible) return false
    closeFind()
    return true
}

private fun CodeEditorState.editingKey(ev: KeyEvent): Boolean {
    val cmd = ev.isCmd
    val v = doc.value
    val edited = when {
        cmd && ev.key == Key.Z -> { undo(redo = ev.isShiftPressed); return true }
        ev.isCtrlPressed && ev.key == Key.Spacebar -> { forceCompletion(); return true }
        cmd && ev.isAltPressed && ev.key == Key.Slash -> BlockComment.toggle(v)
        cmd && ev.key == Key.Slash -> EditOps.toggleComment(v)
        cmd && ev.key == Key.D -> EditOps.duplicateLines(v)
        ev.key == Key.Tab && ev.isShiftPressed -> EditOps.outdent(v)
        ev.key == Key.Tab && !cmd -> EditOps.indent(v)
        ev.key == Key.Enter && !cmd && !ev.isShiftPressed -> EditOps.newline(v)
        else -> return false
    }
    update(edited, typed = false)
    return true
}
