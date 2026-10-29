package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.rememberPopupPositionProviderAtPosition

/**
 * Every right-click menu in the app, in the IDE's colours (Compose's default one is always light
 * grey). Provided by [PumlTheme], so it covers the text fields' Cut/Copy/Paste too. The keyboard
 * works as in a native menu: arrows move, Enter picks, → opens a submenu, ←/Esc go back.
 */
object IdeContextMenu : ContextMenuRepresentation {
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status as? ContextMenuState.Status.Open ?: return
        fun close() { state.status = ContextMenuState.Status.Closed }
        Popup(
            popupPositionProvider = rememberPopupPositionProviderAtPosition(positionPx = status.rect.center),
            onDismissRequest = ::close,
            properties = PopupProperties(focusable = true),
            onKeyEvent = { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { close(); true } else false },
        ) {
            MenuPanel(items().tidy(), onPicked = ::close, onBack = null)
        }
    }
}

/** The menu itself, shared by the right-click menu and [IdeDropdownMenu]. Scrolls when taller than the window. */
@Composable
internal fun MenuPanel(items: List<ContextMenuItem>, onPicked: () -> Unit, onBack: (() -> Unit)?) {
    val c = ide
    val focus = LocalFocusManager.current
    val first = remember { FocusRequester() }
    // Which row's submenu is open (one at a time, like a native menu).
    var open by remember { mutableStateOf<MenuItem?>(null) }
    LaunchedEffect(Unit) { if (onBack != null) runCatching { first.requestFocus() } }
    Surface(
        shape = RoundedCornerShape(8.dp), color = c.popup, shadowElevation = 12.dp,
        modifier = Modifier.border(1.dp, c.popupBorder, RoundedCornerShape(8.dp)).testTag("context-menu"),
    ) {
        Column(
            Modifier.width(IntrinsicSize.Max).widthIn(min = 180.dp, max = 360.dp)
                .verticalScroll(rememberScrollState()).padding(4.dp)
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.DirectionDown -> focus.moveFocus(FocusDirection.Next)
                        Key.DirectionUp -> focus.moveFocus(FocusDirection.Previous)
                        Key.DirectionLeft -> onBack?.let { it(); true } ?: false
                        else -> false
                    }
                },
        ) {
            var focusSet = false
            items.forEach { item ->
                if (item === MenuSeparator) {
                    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(c.popupBorder))
                } else {
                    val requester = if (!focusSet && item.enabledInMenu) { focusSet = true; first } else null
                    MenuRow(item, requester, submenuOpen = open === item, onOpenSubmenu = { open = it }, onPicked = onPicked)
                }
            }
        }
    }
}

/** Plain [ContextMenuItem]s are always enabled: the text field leaves out what it can't do. */
private val ContextMenuItem.enabledInMenu get() = (this as? MenuItem)?.isEnabled ?: (this !== MenuSeparator)

@Composable
private fun MenuRow(
    item: ContextMenuItem,
    requester: FocusRequester?,
    submenuOpen: Boolean,
    onOpenSubmenu: (MenuItem?) -> Unit,
    onPicked: () -> Unit,
) {
    val c = ide
    val rich = item as? MenuItem
    val enabled = item.enabledInMenu
    val hasChildren = rich?.children?.isNotEmpty() == true
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    var anchor by remember { mutableStateOf(IntRect.Zero) }
    // Hovering a row opens its submenu, and closes another row's.
    LaunchedEffect(hovered) { if (hovered && enabled) onOpenSubmenu(if (hasChildren) rich else null) }
    fun activate() {
        if (!enabled) return
        if (hasChildren) onOpenSubmenu(rich) else { onPicked(); item.onClick() }
    }
    val fg = when {
        !enabled -> c.textDisabled
        rich?.danger == true -> c.error
        else -> c.text
    }
    Row(
        Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(5.dp))
            .background(if (enabled && (hovered || focused || submenuOpen)) c.hover else Color.Transparent)
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onGloballyPositioned { val p = it.positionInWindow(); anchor = IntRect(p.x.toInt(), p.y.toInt(), (p.x + it.size.width).toInt(), (p.y + it.size.height).toInt()) }
            .hoverable(interaction)
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.Enter || e.key == Key.NumPadEnter || e.key == Key.Spacebar -> { activate(); true }
                    e.key == Key.DirectionRight && hasChildren -> { onOpenSubmenu(rich); true }
                    else -> false
                }
            }
            .focusable(enabled, interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = ::activate)
            .padding(horizontal = 8.dp)
            .testTag("menu-item:${item.label}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (rich?.checked == true) Icon(Icons.Outlined.Check, null, tint = c.accent, modifier = Modifier.size(16.dp))
            else rich?.icon?.let { Icon(it, null, tint = if (rich.danger && enabled) c.error else if (enabled) c.textMuted else c.textDisabled, modifier = Modifier.size(16.dp)) }
        }
        Text(item.label, color = fg, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
        rich?.shortcut?.let { Text(it, color = c.textMuted, fontSize = 12.sp, maxLines = 1) }
        if (hasChildren) Icon(Icons.Outlined.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
        else if (rich?.shortcut == null) Spacer(Modifier.width(14.dp))
    }
    if (submenuOpen && rich != null) {
        Popup(
            popupPositionProvider = remember(anchor) { BesideRow(anchor) },
            onDismissRequest = { onOpenSubmenu(null) },
            properties = PopupProperties(focusable = false),
        ) {
            MenuPanel(rich.children.tidy(), onPicked = onPicked, onBack = { onOpenSubmenu(null); runCatching { requester?.requestFocus() } })
        }
    }
}

/** A submenu: to the right of its row (left if it doesn't fit), top aligned with it. */
private class BesideRow(private val row: IntRect) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val right = row.right + 4
        val x = if (right + popupContentSize.width <= windowSize.width) right else (row.left - 4 - popupContentSize.width).coerceAtLeast(0)
        val y = (row.top - 5).coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        return IntOffset(x, y)
    }
}

