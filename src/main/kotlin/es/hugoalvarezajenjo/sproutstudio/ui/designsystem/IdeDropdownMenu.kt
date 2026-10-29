package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * A menu opened from a button (a toolbar dropdown), drawn like the right-click menus. Put it in
 * the same `Box` as the button: it opens under that box, or above it when there's no room below.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun IdeDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit, items: () -> List<ContextMenuItem>) {
    if (!expanded) return
    Popup(
        popupPositionProvider = BelowAnchor,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
        onKeyEvent = { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onDismissRequest(); true } else false },
    ) {
        MenuPanel(items().tidy(), onPicked = onDismissRequest, onBack = null)
    }
}

/** Left-aligned under the anchor; above it if it doesn't fit below; always inside the window. */
internal object BelowAnchor : PopupPositionProvider {
    private const val GAP = 2

    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val x = anchorBounds.left.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val below = anchorBounds.bottom + GAP
        val above = anchorBounds.top - GAP - popupContentSize.height
        val y = when {
            below + popupContentSize.height <= windowSize.height -> below
            above >= 0 -> above
            else -> (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}
