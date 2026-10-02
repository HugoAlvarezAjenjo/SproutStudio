package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The command palette (⇧⌘P / ⇧⌘A / double Shift): a search box over every action, the
 * project's diagrams, templates and recent projects. ⏎ runs, ↑↓ move, Esc closes.
 *
 * Esc or a click outside calls [onDismiss]; choosing an entry calls only [onRun], which
 * closes the palette and runs the command.
 */
@Composable
fun CommandPalette(
    commands: List<Command>,
    onDismiss: () -> Unit,
    onRun: (Command) -> Unit,
    recentIds: List<String> = remember { PaletteRecents.ids() },
    initialQuery: String = "",
) {
    val c = ide
    var query by remember { mutableStateOf(TextFieldValue(initialQuery, androidx.compose.ui.text.TextRange(initialQuery.length))) }
    var selected by remember { mutableStateOf(0) }
    val hits = remember(query.text, commands) { CommandSearch.search(query.text, commands, recentIds) }
    val list = rememberLazyListState()
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query.text) { selected = 0; list.scrollToItem(0) }
    // Keep the selection on screen when moving with the arrows.
    LaunchedEffect(selected) {
        val visible = list.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) return@LaunchedEffect
        val first = visible.first().index
        val last = visible.last().index
        when {
            selected < first -> list.scrollToItem(selected)
            selected >= last -> list.scrollToItem((selected - (last - first) + 1).coerceAtLeast(0))
        }
    }

    fun run(i: Int) {
        val hit = hits.getOrNull(i) ?: return
        onRun(hit.command)
    }

    // Scrim: a click outside closes, like IntelliJ.
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = if (c.isDark) 0.25f else 0.12f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 72.dp, start = 24.dp, end = 24.dp)
                .widthIn(max = 640.dp).fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(c.popup)
                .border(1.dp, c.popupBorder, RoundedCornerShape(10.dp))
                // Swallow clicks so they don't reach the scrim.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .semantics { contentDescription = "Command palette" },
        ) {
            // ── search box ──
            Row(
                Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Outlined.Search, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f)) {
                    if (query.text.isEmpty()) {
                        Text("Type an action, a file or a template…", color = c.textDisabled, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 14.sp),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> { if (hits.isNotEmpty()) selected = (selected + 1) % hits.size; true }
                                Key.DirectionUp -> { if (hits.isNotEmpty()) selected = (selected - 1 + hits.size) % hits.size; true }
                                Key.PageDown -> { selected = (selected + 8).coerceAtMost((hits.size - 1).coerceAtLeast(0)); true }
                                Key.PageUp -> { selected = (selected - 8).coerceAtLeast(0); true }
                                Key.Enter, Key.NumPadEnter -> { run(selected); true }
                                Key.Escape -> { onDismiss(); true }
                                else -> false
                            }
                        },
                    )
                }
            }
            HLine()
            // ── results ──
            if (hits.isEmpty()) {
                Text(
                    "Nothing matches “${query.text.trim()}”",
                    color = c.textMuted, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp).padding(vertical = 4.dp), state = list) {
                    itemsIndexed(hits, key = { _, h -> h.command.id }) { i, hit ->
                        PaletteRow(hit, i == selected, onHover = { selected = i }, onClick = { run(i) })
                    }
                }
            }
            HLine()
            Text(
                if (query.text.isEmpty() && recentIds.isNotEmpty()) "Recently used first  ·  ↑↓ move  ·  ⏎ run  ·  Esc close"
                else "↑↓ move  ·  ⏎ run  ·  Esc close  ·  type a file name to open it",
                color = c.textMuted, fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun PaletteRow(hit: Hit, selected: Boolean, onHover: () -> Unit, onClick: () -> Unit) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered) onHover() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(if (selected) c.selection else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            highlight(hit.command.title, hit.positions, c.accent),
            color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 380.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            hit.command.category,
            color = c.textMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        hit.command.shortcut?.let {
            Spacer(Modifier.width(10.dp))
            Text(it, color = c.textMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun highlight(text: String, positions: Set<Int>, color: Color): AnnotatedString = buildAnnotatedString {
    text.forEachIndexed { i, ch ->
        if (i in positions) withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(ch) } else append(ch)
    }
}
