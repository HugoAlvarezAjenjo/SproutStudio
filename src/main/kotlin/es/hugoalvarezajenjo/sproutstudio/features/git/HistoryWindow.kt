package es.hugoalvarezajenjo.sproutstudio.features.git

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.common_close
import es.hugoalvarezajenjo.sproutstudio.resources.history_changes_in
import es.hugoalvarezajenjo.sproutstudio.resources.history_commits
import es.hugoalvarezajenjo.sproutstudio.resources.history_none
import es.hugoalvarezajenjo.sproutstudio.resources.history_none_hint
import es.hugoalvarezajenjo.sproutstudio.resources.history_title
import es.hugoalvarezajenjo.sproutstudio.resources.history_vs_now
import es.hugoalvarezajenjo.sproutstudio.resources.history_window_title
import es.hugoalvarezajenjo.sproutstudio.resources.menu_dark_diagram_preview
import es.hugoalvarezajenjo.sproutstudio.resources.menu_file
import es.hugoalvarezajenjo.sproutstudio.resources.menu_view
import es.hugoalvarezajenjo.sproutstudio.resources.time_days_ago
import es.hugoalvarezajenjo.sproutstudio.resources.time_hours_ago
import es.hugoalvarezajenjo.sproutstudio.resources.time_just_now
import es.hugoalvarezajenjo.sproutstudio.resources.time_minutes_ago
import es.hugoalvarezajenjo.sproutstudio.resources.time_yesterday
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

import es.hugoalvarezajenjo.sproutstudio.core.os.Os

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import es.hugoalvarezajenjo.sproutstudio.domain.git.DiffSide
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.services.git.CommitInfo
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.AppIcon
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.EmptyState
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.HLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.VLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.io.File

@Composable
fun HistoryWindow(project: WorkspaceViewModel, file: File, onClose: () -> Unit) {
    val deps = project.deps
    Window(
        onCloseRequest = onClose,
        title = tr(Res.string.history_window_title, file.name),
        icon = AppIcon.painter,
        state = rememberWindowState(size = DpSize(1440.dp, 840.dp)),
    ) {
        MenuBar {
            Menu(tr(Res.string.menu_file)) {
                Item(tr(Res.string.common_close), shortcut = KeyShortcut(Key.W, meta = Os.isMac, ctrl = !Os.isMac)) { onClose() }
            }
            Menu(tr(Res.string.menu_view)) {
                CheckboxItem(tr(Res.string.menu_dark_diagram_preview), checked = deps.diagram.dark.value) { deps.diagram.toggle() }
            }
        }
        PumlTheme(dark = deps.theme.dark.value) { Surface(Modifier.fillMaxSize(), color = ide.panel) { HistoryView(project, file) } }
    }
}

/**
 * Commits that touched [file] on the left; on the right, what the selected commit changed
 * (its parent → it), or that version against today's file.
 */
@Composable
internal fun HistoryView(p: WorkspaceViewModel, file: File) {
    val c = ide
    var commits by remember { mutableStateOf<List<CommitInfo>?>(null) }
    var selected by remember { mutableStateOf<CommitInfo?>(null) }
    var vsNow by remember { mutableStateOf(false) }
    LaunchedEffect(file, p.git.status.head) {
        val list = p.git.history(file)
        commits = list
        if (selected == null || list.none { it.id == selected?.id }) selected = list.firstOrNull()
    }
    Row(Modifier.fillMaxSize().testTag("history")) {
        Column(Modifier.width(340.dp).fillMaxHeight().background(c.panel)) {
            Row(Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr(Res.string.history_title), style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
                commits?.let { Text(tr(Res.plurals.history_commits, it.size, it.size), fontSize = 12.sp, color = c.textMuted) }
            }
            HLine()
            val list = commits
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(Icons.Outlined.History, tr(Res.string.history_none), tr(Res.string.history_none_hint))
                else -> LazyColumn(Modifier.fillMaxSize().padding(4.dp)) {
                    items(list, key = { it.id }) { ci -> CommitRow(ci, ci.id == selected?.id) { selected = ci } }
                }
            }
        }
        VLine()
        Box(Modifier.weight(1f).fillMaxHeight().background(c.editor)) {
            val s = selected
            if (s != null) {
                // Changes in this commit: parent -> commit. "Compare with now": commit -> working copy.
                val left = if (vsNow) DiffSide.At(s.id) else DiffSide.Before(s.id)
                val right = if (vsNow) DiffSide.WorkingCopy else DiffSide.At(s.id)
                androidx.compose.runtime.key(s.id, vsNow) {
                    DiffView(p, file, left, right, leadingTools = {
                        ToolButton(null, label = tr(Res.string.history_changes_in, s.short), selected = !vsNow) { vsNow = false }
                        ToolButton(null, label = tr(Res.string.history_vs_now, s.short), selected = vsNow) { vsNow = true }
                    })
                }
            }
        }
    }
}

@Composable
private fun CommitRow(ci: CommitInfo, selected: Boolean, onClick: () -> Unit) {
    val c = ide
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
            .background(if (selected) c.selection else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("commit:${ci.short}"),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(ci.message, style = MaterialTheme.typography.bodyMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${ci.author} · ${relativeTime(ci.timeMillis)} · ${ci.short}", fontSize = 11.sp, color = c.textMuted, maxLines = 1)
    }
}

/** "just now", "5 min ago", "3 h ago", "yesterday", then a date. */
internal fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val s = (now - millis) / 1000
    return when {
        s < 60 -> text(Res.string.time_just_now)
        s < 3600 -> text(Res.string.time_minutes_ago, (s / 60).toInt())
        s < 86_400 -> text(Res.string.time_hours_ago, (s / 3600).toInt())
        s < 2 * 86_400 -> text(Res.string.time_yesterday)
        s < 7 * 86_400 -> text(Res.string.time_days_ago, (s / 86_400).toInt())
        else -> java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    }
}
