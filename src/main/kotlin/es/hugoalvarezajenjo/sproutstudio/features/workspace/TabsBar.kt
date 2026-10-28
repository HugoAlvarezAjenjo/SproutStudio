package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.common_close
import es.hugoalvarezajenjo.sproutstudio.resources.tab_close_all
import es.hugoalvarezajenjo.sproutstudio.resources.tab_close_left
import es.hugoalvarezajenjo.sproutstudio.resources.tab_close_others
import es.hugoalvarezajenjo.sproutstudio.resources.tab_close_right
import es.hugoalvarezajenjo.sproutstudio.resources.tab_copy_path
import es.hugoalvarezajenjo.sproutstudio.features.explorer.OsTexts
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.platform.LocalDensity

import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.MenuItem
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.menu
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.core.os.shortcutHint
import es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Dot
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.HLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.VLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide

@Composable
internal fun TabsBar(p: WorkspaceViewModel, onCloseTab: (Document) -> Unit) {
    if (p.docs.isEmpty()) return
    val c = ide
    val drag = remember(p) { TabDrag({ p.docs }, p::moveTab) }
    val scroll = rememberScrollState()
    var viewport by remember { mutableIntStateOf(0) }
    EdgeScrollWhileDragging(drag, scroll) { viewport }
    Column {
        Row(Modifier.fillMaxWidth().height(36.dp).background(c.panel), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).fillMaxHeight().onSizeChanged { viewport = it.width }.horizontalScroll(scroll).testTag("tab-strip")) {
                p.docs.forEachIndexed { i, d ->
                    // Keyed by document, so a tab keeps its drag gesture while it changes places.
                    key(d) {
                        ContextMenuArea(items = { tabMenu(p, d, onCloseTab) }) {
                            EditorTab(d, active = i == p.activeIndex, drag, onSelect = { p.selectTab(p.docs.indexOf(d)) }, onClose = { onCloseTab(d) })
                        }
                    }
                }
            }
            LayoutSwitcher(p)
        }
        HLine()
    }
}

/** One tab: click selects, middle-click or the X closes, dragging reorders. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EditorTab(d: Document, active: Boolean, drag: TabDrag<Document>, onSelect: () -> Unit, onClose: () -> Unit) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val dragging = drag.dragged === d
    DisposableEffect(d) { onDispose { drag.forget(d) } }
    Row(
        Modifier
            .fillMaxHeight()
            .onSizeChanged { drag.measured(d, it.width) }
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationX = if (dragging) drag.offset else 0f }
            .pointerInput(d) {
                detectHorizontalDragGestures(
                    onDragStart = { onSelect(); drag.start(d) },
                    onDragEnd = drag::end,
                    onDragCancel = drag::end,
                ) { change, dx -> change.consume(); drag.dragBy(dx) }
            }
            .onPointerEvent(PointerEventType.Release) { if (it.button == PointerButton.Tertiary) onClose() }
            .testTag("tab-${d.name}"),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .background(if (active || dragging) c.editor else if (hovered) c.hover else c.panel)
                .hoverable(interaction)
                .clickable(onClick = onSelect),
        ) {
            Row(
                Modifier.fillMaxHeight().padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.Schema, null, tint = c.syntax.arrow, modifier = Modifier.size(15.dp))
                Text(d.name, style = MaterialTheme.typography.bodyMedium, color = if (active) c.text else c.textMuted)
                Box(
                    Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        d.dirty && !hovered -> Dot(c.accent, 6)
                        hovered || active -> Icon(Icons.Outlined.Close, tr(Res.string.common_close), tint = c.textMuted, modifier = Modifier.size(13.dp))
                    }
                }
            }
            // Active-tab indicator, like the New UI.
            if (active) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(c.accent))
        }
        VLine()
    }
}

/**
 * While a tab is dragged near an edge of a bar that has more tabs than fit, scrolls the bar every
 * frame and carries the tab along, so it can reach tabs that are out of sight.
 */
@Composable
private fun EdgeScrollWhileDragging(drag: TabDrag<Document>, scroll: ScrollState, viewport: () -> Int) {
    val density = LocalDensity.current
    val edge = with(density) { EDGE_ZONE.toPx() }
    val maxStep = with(density) { EDGE_MAX_STEP.toPx() }
    LaunchedEffect(drag, scroll) {
        // Runs only while the dragged tab sits in an edge zone, not for every drag.
        snapshotFlow { drag.edgeScroll(scroll.value, viewport(), edge, maxStep) != 0f }
            .collectLatest { atEdge ->
                while (atEdge) {
                    withFrameNanos { }
                    val step = drag.edgeScroll(scroll.value, viewport(), edge, maxStep)
                    if (step == 0f) break
                    drag.dragBy(scroll.scrollBy(step))
                }
            }
    }
}

/** How close to an edge a dragged tab starts scrolling the bar, and its top speed per frame. */
private val EDGE_ZONE = 40.dp
private val EDGE_MAX_STEP = 12.dp

/** Right-click on a tab. Entries that would do nothing (no tabs to the left...) are left out. */
private fun tabMenu(p: WorkspaceViewModel, d: Document, onCloseTab: (Document) -> Unit) = menu {
    val i = p.docs.indexOf(d)
    group {
        add(MenuItem(text(Res.string.common_close), Icons.Outlined.Close, shortcut = shortcutHint("W")) { onCloseTab(d) })
        if (p.docs.size > 1) add(MenuItem(text(Res.string.tab_close_others)) { p.closeOtherTabs(d) })
        if (i > 0) add(MenuItem(text(Res.string.tab_close_left)) { p.closeTabsToTheLeft(d) })
        if (i < p.docs.lastIndex) add(MenuItem(text(Res.string.tab_close_right)) { p.closeTabsToTheRight(d) })
        add(MenuItem(text(Res.string.tab_close_all)) { p.closeAllTabs() })
    }
    d.file?.let { f ->
        group {
            add(MenuItem(text(Res.string.tab_copy_path), Icons.Outlined.ContentCopy) { p.deps.platform.copyTextToClipboard(f.absolutePath) })
            add(MenuItem(text(OsTexts.reveal), Icons.Outlined.FolderOpen) { p.deps.platform.reveal(f) })
        }
    }
}

/** IntelliJ's top-right editor switcher: code only, code + preview, preview only. */
@Composable
private fun LayoutSwitcher(p: WorkspaceViewModel) {
    Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        LAYOUT_BUTTONS.forEach { (l, icon) ->
            ToolButton(icon, "${tr(l.label)} (${l.shortcutHint})", selected = p.layout == l) { p.changeLayout(l) }
        }
    }
}

private val LAYOUT_BUTTONS = listOf(
    EditorLayout.EDITOR to Icons.Outlined.Code,
    EditorLayout.SPLIT to Icons.Outlined.VerticalSplit,
    EditorLayout.PREVIEW to Icons.Outlined.Image,
)

internal val EditorLayout.shortcutKey: Key
    get() = when (this) { EditorLayout.EDITOR -> Key.One; EditorLayout.SPLIT -> Key.Two; EditorLayout.PREVIEW -> Key.Three }

private val EditorLayout.shortcutHint: String
    get() = (if (Os.isMac) "⌥⌘" else "Ctrl+Alt+") + (ordinal + 1)
