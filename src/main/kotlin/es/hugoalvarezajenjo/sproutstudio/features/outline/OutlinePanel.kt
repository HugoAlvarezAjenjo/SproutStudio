package es.hugoalvarezajenjo.sproutstudio.features.outline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Outline
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.OutlineSection
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SymbolIndex
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.features.editor.navigateTo
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.outline_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.outline_empty
import es.hugoalvarezajenjo.sproutstudio.resources.outline_empty_hint
import es.hugoalvarezajenjo.sproutstudio.resources.outline_outside
import es.hugoalvarezajenjo.sproutstudio.resources.outline_title
import es.hugoalvarezajenjo.sproutstudio.resources.tree_collapse_all
import es.hugoalvarezajenjo.sproutstudio.resources.tree_expand_all
import es.hugoalvarezajenjo.sproutstudio.resources.tree_hide
import es.hugoalvarezajenjo.sproutstudio.resources.workspace_no_file
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.EmptyState
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ListRow
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

/**
 * The Outline tool window: what [doc] declares, per diagram, nested by package. Clicking an
 * entry selects its name in the editor (Back returns); the entry at the caret is highlighted
 * and kept in view. [onReveal] brings the editor on screen first, [onHide] closes the panel.
 */
@Composable
fun OutlinePanel(doc: Document?, width: Dp, hideShortcut: String, onReveal: () -> Unit, onHide: () -> Unit) {
    val c = ide
    val text = doc?.text.orEmpty()
    val outline = remember(text) { Outline.of(text) }
    var collapsed by remember(doc) { mutableStateOf(emptySet<String>()) }
    val rows = remember(outline, collapsed) { outlineRows(outline, collapsed) }
    val containers = remember(outline) { outline.containerKeys() }
    val selected = doc?.let { outline.selectedAt(it.value.selection.start) }

    Column(Modifier.width(width).fillMaxHeight().background(c.panel).testTag("outline-panel")) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr(Res.string.outline_title), style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
            ToolButton(Icons.Outlined.UnfoldMore, tr(Res.string.tree_expand_all), enabled = collapsed.isNotEmpty()) { collapsed = emptySet() }
            ToolButton(Icons.Outlined.UnfoldLess, tr(Res.string.tree_collapse_all), enabled = (containers - collapsed).isNotEmpty()) { collapsed = containers }
            ToolButton(Icons.Outlined.Remove, tr(Res.string.tree_hide, hideShortcut), onClick = onHide)
        }
        when {
            doc == null -> Centered { EmptyState(Icons.Outlined.Schema, tr(Res.string.workspace_no_file), "") }
            outline.isEmpty -> Centered { EmptyState(Icons.Outlined.Schema, tr(Res.string.outline_empty), tr(Res.string.outline_empty_hint)) }
            else -> {
                val list = rememberLazyListState()
                val selectedRow = rows.indexOfFirst { it is OutlineRow.Entry && it.node == selected }
                // Follow the caret, but only scroll when its entry is off screen.
                LaunchedEffect(selectedRow) {
                    if (selectedRow >= 0 && list.layoutInfo.visibleItemsInfo.none { it.index == selectedRow }) list.animateScrollToItem(selectedRow)
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp), state = list) {
                    itemsIndexed(rows, key = { _, r -> r.rowKey }) { i, row ->
                        when (row) {
                            is OutlineRow.Header -> SectionHeader(row.section) { onReveal(); doc.jumpTo(row.section.line); doc.requestFocus() }
                            is OutlineRow.Entry -> EntryRow(
                                row, selected = i == selectedRow,
                                onToggle = { collapsed = if (row.collapsed) collapsed - row.node.key else collapsed + row.node.key },
                                onOpen = { onReveal(); doc.navigateTo(row.node.symbol.range); doc.requestFocus() },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val OutlineRow.rowKey: String get() = when (this) {
    is OutlineRow.Header -> "h:${section.line}"
    is OutlineRow.Entry -> "e:${node.key}"
}

@Composable
private fun Centered(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }

@Composable
private fun SectionHeader(section: OutlineSection, onClick: () -> Unit) {
    val c = ide
    val name = section.block?.let { tr(Res.string.outline_diagram, it + 1) } ?: tr(Res.string.outline_outside)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 8.dp, top = 8.dp, bottom = 2.dp).testTag("outline-header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name.uppercase(), fontSize = 10.sp, color = c.textMuted, maxLines = 1)
        section.title?.let {
            Text("  $it", fontSize = 11.sp, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EntryRow(row: OutlineRow.Entry, selected: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val c = ide
    val s = row.node.symbol
    ListRow(selected = selected, onClick = onOpen, height = 24, startPadding = 4 + row.depth * 16, modifier = Modifier.testTag("outline-row-${s.name}")) {
        if (row.node.children.isNotEmpty()) {
            Icon(
                if (row.collapsed) Icons.Outlined.ChevronRight else Icons.Outlined.ExpandMore, null, tint = c.textMuted,
                modifier = Modifier.size(14.dp).clickable(onClick = onToggle).testTag("outline-toggle-${s.name}"),
            )
        } else {
            Spacer(Modifier.width(14.dp))
        }
        Icon(outlineIcon(s.kind), null, tint = c.syntax.keyword, modifier = Modifier.size(15.dp))
        Text(s.name, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        // The declaring keyword; a name only used (an implicit participant) has none.
        if (s.kind != SymbolIndex.USED) Text(s.kind, fontSize = 11.sp, color = c.textMuted, maxLines = 1)
    }
}
