package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import es.hugoalvarezajenjo.sproutstudio.core.os.Os

/**
 * ⌘-hover underlines what can be followed (with a hand cursor) and ⌘-click follows it; Ctrl
 * off macOS. It listens on the initial pass, so a ⌘-click on a link is ours before the field
 * moves the caret. [onFollow] gets the offset that was clicked.
 */
internal fun Modifier.followLinks(
    state: CodeEditorState,
    offsetAt: (Offset) -> Int?,
    linkAt: (Offset) -> IntRange?,
    onFollow: (offset: Int) -> Unit,
): Modifier = this
    .pointerHoverIcon(if (state.link != null) PointerIcon.Hand else PointerIcon.Text, overrideDescendants = state.link != null)
    .pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val pos = e.changes.firstOrNull()?.position ?: continue
                val mods = e.keyboardModifiers
                state.linkKeyDown = if (Os.isMac) mods.isMetaPressed else mods.isCtrlPressed
                when (e.type) {
                    PointerEventType.Exit -> {
                        state.hoverPos = null
                        state.link = null
                    }
                    PointerEventType.Press -> if (state.linkKeyDown && e.buttons.isPrimaryPressed && linkAt(pos) != null) {
                        state.pressedLink = offsetAt(pos)
                        e.changes.forEach { it.consume() }
                    }
                    PointerEventType.Release -> state.pressedLink?.let { o ->
                        state.pressedLink = null
                        e.changes.forEach { it.consume() }
                        onFollow(o)
                    }
                    else -> {
                        state.hoverPos = pos
                        state.link = if (state.linkKeyDown) linkAt(pos) else null
                    }
                }
            }
        }
    }
