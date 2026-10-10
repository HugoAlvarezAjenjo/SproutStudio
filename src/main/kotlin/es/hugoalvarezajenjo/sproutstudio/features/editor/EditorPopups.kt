package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Completion
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.CompletionKind
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.CompletionRequest
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Occurrence
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.OccurrenceRole
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.editor_completion_hint
import es.hugoalvarezajenjo.sproutstudio.resources.nav_usages
import es.hugoalvarezajenjo.sproutstudio.resources.nav_usages_hint
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.editorTextStyle
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

// The editor's caret popups. Both are keyboard-driven from the editor (↑ ↓ ⏎ esc), so they
// never take focus; they share [PopupList] for the look and the scrolling.

/** Completion suggestions; the letters the typed text matched are drawn in the accent colour. */
@Composable
internal fun CompletionPopup(req: CompletionRequest, selected: Int, onPick: (Completion) -> Unit) {
    val c = ide
    PopupList(
        items = req.items, selected = selected, onPick = onPick,
        footer = tr(Res.string.editor_completion_hint), minWidth = 260.dp, maxWidth = 380.dp,
    ) { item ->
        KindBadge(item.kind)
        Text(
            highlight(item.label, item.matched.map { it..it }, SpanStyle(color = c.accent, fontWeight = FontWeight.Bold)),
            style = editorTextStyle.copy(fontSize = 13.sp, color = c.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        item.detail?.let {
            Text(it.describe(), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/** Find Usages / ⌘B on a declaration: each line [list]'s symbol appears on, the name in bold. */
@Composable
internal fun UsagesPopup(list: UsagesList, text: String, onPick: (Occurrence) -> Unit) {
    val c = ide
    PopupList(
        items = list.items, selected = list.selected, onPick = onPick,
        header = tr(Res.plurals.nav_usages, list.items.size, list.items.size, list.symbol),
        footer = tr(Res.string.nav_usages_hint), minWidth = 320.dp, maxWidth = 560.dp,
        modifier = Modifier.testTag("usages-popup"),
    ) { o ->
        Text(
            o.line.toString(), fontSize = 11.sp, color = c.lineNumber, textAlign = TextAlign.End,
            style = editorTextStyle, modifier = Modifier.width(30.dp),
        )
        val (snippet, name) = lineSnippet(text, o)
        Text(
            highlight(snippet, listOf(name), SpanStyle(color = c.accent, fontWeight = FontWeight.Bold)),
            style = editorTextStyle.copy(fontSize = 13.sp, color = c.text),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (o.role == OccurrenceRole.DECLARATION) Text("def", fontSize = 10.sp, color = c.syntax.keyword, fontWeight = FontWeight.SemiBold)
    }
}

/** [o]'s line without its indentation, and where the name sits in it. */
private fun lineSnippet(text: String, o: Occurrence): Pair<String, IntRange> {
    val start = text.lastIndexOf('\n', (o.range.first - 1).coerceAtLeast(0)).let { if (o.range.first == 0) 0 else it + 1 }
    val end = text.indexOf('\n', o.range.first).let { if (it < 0) text.length else it }
    val line = text.substring(start, end)
    val indent = line.length - line.trimStart().length
    return line.trim() to (o.range.first - start - indent)..(o.range.last - start - indent)
}

/** [s] with [ranges] drawn in [style]. */
private fun highlight(s: String, ranges: List<IntRange>, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(s)
    for (r in ranges) {
        val a = r.first.coerceIn(0, s.length); val b = (r.last + 1).coerceIn(0, s.length)
        if (b > a) addStyle(style, a, b)
    }
}

@Composable
private fun <T> PopupList(
    items: List<T>,
    selected: Int,
    onPick: (T) -> Unit,
    footer: String,
    minWidth: Dp,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    header: String? = null,
    row: @Composable RowScope.(T) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem((selected - 3).coerceAtLeast(0)) }
    val c = ide
    Surface(
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 12.dp,
        color = c.popup,
        modifier = modifier.widthIn(min = minWidth, max = maxWidth).border(1.dp, c.popupBorder, RoundedCornerShape(8.dp)),
    ) {
        Column {
            if (header != null) {
                Text(header, fontSize = 12.sp, color = c.textMuted, modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp))
            }
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 260.dp).padding(4.dp)) {
                itemsIndexed(items) { i, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == selected) c.selection else Color.Transparent)
                            .clickable { onPick(item) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) { row(item) }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.popupBorder))
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text(footer, fontSize = 11.sp, color = c.textMuted)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KindBadge(kind: CompletionKind) {
    // IntelliJ completion icons: a coloured glyph per kind, no filled pill.
    val s = ide.syntax
    val fg = when (kind) {
        CompletionKind.KEYWORD -> s.keyword
        CompletionKind.SYMBOL -> s.arrow
        CompletionKind.SNIPPET -> s.string
        CompletionKind.SKINPARAM -> s.color
        CompletionKind.DIRECTIVE -> s.directive
    }
    Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
        Text(kind.badge, fontSize = 10.sp, color = fg, fontWeight = FontWeight.SemiBold)
    }
}
