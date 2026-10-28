@file:OptIn(ExperimentalFoundationApi::class)

package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.core.os.shortcutHint
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.edit_copy
import es.hugoalvarezajenjo.sproutstudio.resources.edit_cut
import es.hugoalvarezajenjo.sproutstudio.resources.edit_paste
import es.hugoalvarezajenjo.sproutstudio.resources.edit_select_all
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.MenuItem
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.MenuSeparator
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text

/**
 * The editor's right-click menu: the text field's own Cut/Copy/Paste/Select All (they know the
 * selection and the clipboard), in their usual place after [extra] — what the screen around the
 * editor adds (navigation, git, export...). The editor knows nothing of those actions.
 */
internal class EditorTextMenu(private val extra: () -> List<ContextMenuItem>) : TextContextMenu {
    @Composable
    override fun Area(textManager: TextContextMenu.TextManager, state: ContextMenuState, content: @Composable () -> Unit) {
        ContextMenuArea(items = { editorMenuItems(extra(), textItems(textManager)) }, state = state, content = content)
    }
}

/** [extra] first, then the clipboard group, with one separator between them. */
internal fun editorMenuItems(extra: List<ContextMenuItem>, clipboard: List<ContextMenuItem>): List<ContextMenuItem> =
    if (extra.isEmpty()) clipboard else extra + MenuSeparator + clipboard

private fun textItems(t: TextContextMenu.TextManager): List<ContextMenuItem> = listOfNotNull(
    t.cut?.let { MenuItem(text(Res.string.edit_cut), Icons.Outlined.ContentCut, shortcut = shortcutHint("X"), isEnabled = it.enabled, onClick = it.execute) },
    t.copy?.let { MenuItem(text(Res.string.edit_copy), Icons.Outlined.ContentCopy, shortcut = shortcutHint("C"), isEnabled = it.enabled, onClick = it.execute) },
    t.paste?.let { MenuItem(text(Res.string.edit_paste), Icons.Outlined.ContentPaste, shortcut = shortcutHint("V"), isEnabled = it.enabled, onClick = it.execute) },
    t.selectAll?.let { MenuItem(text(Res.string.edit_select_all), Icons.Outlined.SelectAll, shortcut = shortcutHint("A"), isEnabled = it.enabled, onClick = it.execute) },
)

/**
 * Right-click moves the caret to where it lands, like IntelliJ, so the menu's actions work on
 * the clicked word; a right-click inside the selection keeps it (to cut or copy it).
 * Positions are in the field's content coordinates, as [offsetAt] expects.
 */
internal fun Modifier.moveCaretOnRightClick(offsetAt: (Offset) -> Int?, selection: () -> TextRange, select: (Int) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.type != PointerEventType.Press || !e.buttons.isSecondaryPressed) continue
                val at = e.changes.firstOrNull()?.position?.let(offsetAt) ?: continue
                val sel = selection()
                if (sel.collapsed || at !in sel.min..sel.max) select(at)
            }
        }
    }
