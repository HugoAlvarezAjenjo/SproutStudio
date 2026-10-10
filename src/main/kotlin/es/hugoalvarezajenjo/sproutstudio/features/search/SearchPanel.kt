package es.hugoalvarezajenjo.sproutstudio.features.search

import es.hugoalvarezajenjo.sproutstudio.domain.text.FileHits
import es.hugoalvarezajenjo.sproutstudio.domain.text.SearchHit
import es.hugoalvarezajenjo.sproutstudio.features.editor.FileJump
import es.hugoalvarezajenjo.sproutstudio.features.editor.FindOptions
import es.hugoalvarezajenjo.sproutstudio.features.palette.shortcutHint
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.find_bad_regex
import es.hugoalvarezajenjo.sproutstudio.resources.find_no_results
import es.hugoalvarezajenjo.sproutstudio.resources.find_results
import es.hugoalvarezajenjo.sproutstudio.resources.search_again
import es.hugoalvarezajenjo.sproutstudio.resources.search_files
import es.hugoalvarezajenjo.sproutstudio.resources.search_hide
import es.hugoalvarezajenjo.sproutstudio.resources.search_hint
import es.hugoalvarezajenjo.sproutstudio.resources.search_placeholder
import es.hugoalvarezajenjo.sproutstudio.resources.search_searching
import es.hugoalvarezajenjo.sproutstudio.resources.search_title
import es.hugoalvarezajenjo.sproutstudio.resources.search_truncated
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.HLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ListRow
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.SearchField
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.editorTextStyle
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * The Find in Files tool window (⇧⌘F): the query with IntelliJ's three toggles, then the hits
 * grouped by file. Clicking a hit opens the file with the match selected.
 */
@Composable
internal fun SearchPanel(p: WorkspaceViewModel) {
    val c = ide
    val s = p.search
    val focus = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(s.query.text)) }
    var selected by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(s) {
        s.focusRequests.collect {
            field = TextFieldValue(s.query.text, TextRange(0, s.query.text.length))
            runCatching { focus.requestFocus() }
        }
    }
    // Edits and saves move the hits: search again while the panel is on screen.
    LaunchedEffect(p) { snapshotFlow { p.docs.map { it.text } }.collect { s.refreshSoon() } }

    Column(Modifier.width(p.sidebarWidth.dp).fillMaxHeight().background(c.panel).testTag("search-panel")) {
        Row(Modifier.fillMaxWidth().height(36.dp).padding(start = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr(Res.string.search_title), style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
            ToolButton(Icons.Outlined.Refresh, tr(Res.string.search_again), enabled = s.query.text.isNotEmpty()) { s.refresh() }
            ToolButton(Icons.Outlined.Remove, tr(Res.string.search_hide, shortcutHint("F", shift = true))) { p.showSidebar(false) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            SearchField(
                value = field,
                onChange = { field = it; s.changeQuery(s.query.copy(text = it.text)) },
                placeholder = tr(Res.string.search_placeholder),
                icon = true,
                error = s.result.error != null,
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("search-field").onPreviewKeyEvent { ev ->
                    if (ev.type == KeyEventType.KeyDown && (ev.key == Key.Enter || ev.key == Key.NumPadEnter)) { s.refresh(); true } else false
                },
            )
            FindOptions(s.query) { s.changeQuery(it) }
        }
        Text(
            summary(s), fontSize = 12.sp, maxLines = 1,
            color = if (s.result.error != null) c.error else c.textMuted,
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 4.dp).testTag("search-summary"),
        )
        HLine()
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp)) {
            for (fh in s.result.files) {
                val open = fh.path !in collapsed
                item(key = "f:" + fh.path) {
                    FileRow(fh, p.root, open) { collapsed = if (open) collapsed + fh.path else collapsed - fh.path }
                }
                if (open) {
                    items(fh.hits, key = { "h:${fh.path}:${it.range.first}" }) { hit ->
                        HitRow(hit, selected = selected == (fh.path to hit.range.first)) {
                            selected = fh.path to hit.range.first
                            p.revealEditor()
                            p.open(FileJump(File(fh.path), hit.range))
                        }
                    }
                }
            }
        }
    }
}

/** "12 results · 3 files", or why there are none. */
@Composable
private fun summary(s: SearchViewModel): String {
    val r = s.result
    return when {
        r.error != null -> tr(Res.string.find_bad_regex)
        s.searching -> tr(Res.string.search_searching)
        s.query.text.isEmpty() -> tr(Res.string.search_hint)
        r.files.isEmpty() -> tr(Res.string.find_no_results)
        else -> buildString {
            append(tr(Res.plurals.find_results, r.hitCount, r.hitCount))
            append(" · ")
            append(tr(Res.plurals.search_files, r.files.size, r.files.size))
            if (r.truncated) append(" · ").append(tr(Res.string.search_truncated, r.hitCount))
        }
    }
}

@Composable
private fun FileRow(fh: FileHits, root: File?, open: Boolean, onToggle: () -> Unit) {
    val c = ide
    val file = File(fh.path)
    val folder = root?.let { file.parentFile?.relativeToOrNull(it)?.path }?.takeIf { it.isNotEmpty() }
    ListRow(onClick = onToggle, height = 24, startPadding = 4) {
        Icon(if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
        Icon(Icons.Outlined.Schema, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
        Text(file.name, fontSize = 12.sp, color = c.text, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(folder.orEmpty(), fontSize = 11.sp, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(fh.hits.size.toString(), fontSize = 11.sp, color = c.textMuted)
    }
}

@Composable
private fun HitRow(hit: SearchHit, selected: Boolean, onClick: () -> Unit) {
    val c = ide
    ListRow(selected = selected, onClick = onClick, height = 22, startPadding = 26, modifier = Modifier.testTag("search-hit")) {
        Text(hit.line.toString(), fontSize = 11.sp, color = c.textMuted, maxLines = 1, modifier = Modifier.width(28.dp))
        Text(
            highlighted(hit, SpanStyle(background = c.findMatch, color = c.text, fontWeight = FontWeight.Bold)),
            style = editorTextStyle.copy(fontSize = 12.sp, color = c.text), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun highlighted(hit: SearchHit, match: SpanStyle): AnnotatedString = buildAnnotatedString {
    val m = hit.previewMatch
    val text = hit.preview
    append(text.substring(0, m.first.coerceIn(0, text.length)))
    withStyle(match) { append(text.substring(m.first.coerceIn(0, text.length), (m.last + 1).coerceIn(0, text.length))) }
    append(text.substring((m.last + 1).coerceIn(0, text.length)))
}
