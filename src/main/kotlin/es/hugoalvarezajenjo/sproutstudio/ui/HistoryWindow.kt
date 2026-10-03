package es.hugoalvarezajenjo.sproutstudio.ui

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
import es.hugoalvarezajenjo.sproutstudio.git.CommitInfo
import es.hugoalvarezajenjo.sproutstudio.git.DiffSide
import es.hugoalvarezajenjo.sproutstudio.model.AppState
import es.hugoalvarezajenjo.sproutstudio.model.AppWindow
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import java.io.File

@Composable
fun HistoryWindow(win: AppWindow.History) {
    Window(
        onCloseRequest = { AppState.close(win) },
        title = "History: ${win.file.name} — SproutStudio",
        icon = AppIcon.painter,
        state = rememberWindowState(size = DpSize(1440.dp, 840.dp)),
    ) {
        MenuBar {
            Menu("File") {
                Item("Close", shortcut = KeyShortcut(Key.W, meta = Dialogs.isMac, ctrl = !Dialogs.isMac)) { AppState.close(win) }
            }
            Menu("View") {
                CheckboxItem("Dark Diagram Preview", checked = DiagramPrefs.dark) { DiagramPrefs.toggle() }
            }
        }
        PumlTheme { Surface(Modifier.fillMaxSize(), color = ide.panel) { HistoryView(win.project, win.file) } }
    }
}

/**
 * Commits that touched [file] on the left; on the right, what the selected commit changed
 * (its parent → it), or that version against today's file.
 */
@Composable
internal fun HistoryView(p: ProjectState, file: File) {
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
                Text("History", style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
                commits?.let { Text("${it.size} commit${if (it.size == 1) "" else "s"}", fontSize = 12.sp, color = c.textMuted) }
            }
            HLine()
            val list = commits
            when {
                list == null -> Unit
                list.isEmpty() -> EmptyState(Icons.Outlined.History, "No history yet", "This file hasn't been committed")
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
                        ToolButton(null, label = "Changes in ${s.short}", selected = !vsNow) { vsNow = false }
                        ToolButton(null, label = "${s.short} vs now", selected = vsNow) { vsNow = true }
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
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86_400 -> "${s / 3600} h ago"
        s < 2 * 86_400 -> "yesterday"
        s < 7 * 86_400 -> "${s / 86_400} days ago"
        else -> java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
    }
}
