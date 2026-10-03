package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.IndeterminateCheckBox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.git.ChangeType
import es.hugoalvarezajenjo.sproutstudio.git.FileChange
import es.hugoalvarezajenjo.sproutstudio.git.GitRepo
import es.hugoalvarezajenjo.sproutstudio.model.AppState
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import es.hugoalvarezajenjo.sproutstudio.model.isDiagram
import kotlinx.coroutines.launch
import java.io.File

/**
 * IntelliJ's Commit tool window (⌘0): what changed since the last commit, checkboxes to pick
 * what goes in, a message and Commit. Local only, nothing is pushed.
 */
@Composable
internal fun CommitPanel(p: ProjectState) {
    val c = ide
    val git = p.git
    val scope = rememberCoroutineScope()
    var confirmRollback by remember { mutableStateOf<FileChange?>(null) }
    val messageFocus = remember { FocusRequester() }
    // ⌘K / "Commit…" from elsewhere puts the caret in the message box.
    LaunchedEffect(p.commitFocusTick) { if (p.commitFocusTick > 0) runCatching { messageFocus.requestFocus() } }

    fun doCommit() {
        val files = git.included.map { it.file }
        val msg = git.commitMessage
        if (files.isEmpty() || msg.isBlank()) return
        scope.launch {
            // Like IntelliJ: commit what you see, so unsaved editor text is written first.
            p.docs.filter { it.file != null && it.dirty }.forEach { it.save() }
            val id = git.commit(files, msg)
            if (id != null) {
                git.commitMessage = ""
                git.selection.clear()
                git.lastResult = "Committed ${files.size} file${if (files.size == 1) "" else "s"} ($id)"
                p.refreshTree()
            }
        }
    }

    fun rollback(ch: FileChange) {
        scope.launch {
            // Save the editor's copy first so autosave can't write it back over the restored file.
            val doc = p.docs.firstOrNull { it.file != null && git.changeOf(it.file!!)?.path == ch.path }
            doc?.takeIf { it.dirty }?.save()
            if (git.rollback(ch.file)) {
                doc?.replaceFromDisk()
                p.refreshTree()
            }
        }
    }

    Column(Modifier.width(p.sidebarWidth.dp).fillMaxHeight().background(c.panel).testTag("commit-panel")) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Commit", style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
            if (git.isRepo) ToolButton(Icons.Outlined.Refresh, "Refresh") { git.requestRefresh() }
            ToolButton(Icons.Outlined.Remove, "Hide (${shortcutHint("0")})") { p.showSidebar(false) }
        }

        if (!git.isRepo) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                EmptyState(Icons.Outlined.Commit, "No Git repository", "Keep a history of your diagrams: every commit is a snapshot you can go back to.")
                ToolButton(null, label = "Create Git Repository", primary = true) {
                    scope.launch { git.init() }
                }
                git.error?.let { Text(it, color = c.error, fontSize = 12.sp) }
            }
            return@Column
        }

        val changes = git.status.changes
        val tracked = changes.filter { it.type != ChangeType.UNVERSIONED }
        val unversioned = changes.filter { it.type == ChangeType.UNVERSIONED }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (changes.isEmpty()) {
                Text(
                    if (git.status.head == null) "No files yet" else "No changes since the last commit",
                    fontSize = 12.sp, color = c.textMuted, modifier = Modifier.padding(start = 14.dp, top = 8.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                if (tracked.isNotEmpty()) {
                    item(key = "h-changes") { GroupHeader(p, "Changes", tracked) }
                    items(tracked, key = { "c:" + it.path }) { ch -> ChangeRow(p, ch, onRollback = { confirmRollback = it }) }
                }
                if (unversioned.isNotEmpty()) {
                    item(key = "h-unversioned") { GroupHeader(p, "Unversioned Files", unversioned) }
                    items(unversioned, key = { "u:" + it.path }) { ch -> ChangeRow(p, ch, onRollback = null) }
                }
            }
        }
        HLine()

        // ── message + Commit ──
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 84.dp, max = 180.dp)
                    .clip(RoundedCornerShape(6.dp)).background(c.editor)
                    .border(1.dp, c.popupBorder, RoundedCornerShape(6.dp))
                    .padding(8.dp),
            ) {
                if (git.commitMessage.isEmpty()) Text("Commit message", fontSize = 13.sp, color = c.textDisabled)
                BasicTextField(
                    value = git.commitMessage,
                    onValueChange = { git.commitMessage = it; git.lastResult = null },
                    textStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = c.text),
                    cursorBrush = SolidColor(c.text),
                    modifier = Modifier.fillMaxWidth().focusRequester(messageFocus).testTag("commit-message")
                        .onPreviewKeyEvent { e ->
                            val cmd = e.isMetaPressed || e.isCtrlPressed
                            if (e.type == KeyEventType.KeyDown && cmd && e.key == Key.Enter) { doCommit(); true } else false
                        },
                )
            }
            val n = git.included.size
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton(
                    null, tooltip = "Commit (${shortcutHint("⏎")})", label = "Commit", primary = true,
                    enabled = n > 0 && git.commitMessage.isNotBlank(),
                ) { doCommit() }
                Text(
                    if (changes.isEmpty()) "" else "$n of ${changes.size} file${if (changes.size == 1) "" else "s"}",
                    fontSize = 12.sp, color = c.textMuted,
                )
            }
            AuthorLine(p)
            git.error?.let { Text(it, color = c.error, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            git.lastResult?.let { Text(it, color = c.success, fontSize = 12.sp) }
        }
    }

    confirmRollback?.let { ch ->
        AlertDialog(
            onDismissRequest = { confirmRollback = null },
            containerColor = ide.popup,
            shape = RoundedCornerShape(10.dp),
            title = { Text("Rollback ${ch.file.name}?", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    if (ch.type == ChangeType.ADDED) "It goes back to unversioned. The file stays on disk."
                    else "Your changes since the last commit are lost. This can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = { TextButton(onClick = { confirmRollback = null; rollback(ch) }) { Text("Rollback", color = ide.error) } },
            dismissButton = { TextButton(onClick = { confirmRollback = null }) { Text("Cancel") } },
        )
    }
}

/** "Changes  3 files" with a tri-state checkbox for the whole group. */
@Composable
private fun GroupHeader(p: ProjectState, title: String, items: List<FileChange>) {
    val c = ide
    val git = p.git
    val on = items.count { git.isIncluded(it) }
    Row(
        Modifier.fillMaxWidth().height(26.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Check(
            when (on) { 0 -> false; items.size -> true; else -> null },
            "Include all $title",
        ) { val all = on < items.size; items.forEach { git.setIncluded(it, all) } }
        Text(title, style = MaterialTheme.typography.labelLarge, color = c.text)
        Text("${items.size} file${if (items.size == 1) "" else "s"}", fontSize = 12.sp, color = c.textMuted)
    }
}

@Composable
private fun ChangeRow(p: ProjectState, ch: FileChange, onRollback: ((FileChange) -> Unit)?) {
    val c = ide
    val git = p.git
    val canOpen = ch.file.isDiagram()
    val canCompare = canOpen && ch.type == ChangeType.MODIFIED
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val isActive = p.active?.file?.let { git.changeOf(it)?.path } == ch.path
    val menu = buildList {
        if (canOpen) add(ContextMenuItem("Open") { p.open(ch.file) })
        if (canCompare) add(ContextMenuItem("Compare Diagram with HEAD") { AppState.compareWithHead(p, ch.file) })
        if (onRollback != null) add(ContextMenuItem("Rollback…") { onRollback(ch) })
    }
    ContextMenuArea(items = { menu }) {
        Row(
            Modifier
                .fillMaxWidth().height(26.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isActive) c.selection else if (hovered) c.hover else Color.Transparent)
                .hoverable(interaction)
                .pointerInput(ch, canOpen, canCompare) {
                    detectTapGestures(
                        onTap = { if (canOpen) p.open(ch.file) },
                        // IntelliJ opens a diff on double-click; ours compares the two pictures.
                        onDoubleTap = { if (canCompare) AppState.compareWithHead(p, ch.file) else if (canOpen) p.open(ch.file) },
                    )
                }
                .padding(start = 22.dp, end = 8.dp)
                .testTag("change:${ch.path}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Check(git.isIncluded(ch), "Include ${ch.file.name}", enabled = ch.type != ChangeType.CONFLICT) {
                git.setIncluded(ch, !git.isIncluded(ch))
            }
            Icon(
                if (canOpen) Icons.Outlined.Schema else Icons.Outlined.Description, null,
                tint = if (canOpen) c.syntax.arrow else c.textMuted, modifier = Modifier.size(16.dp),
            )
            Text(
                ch.file.name, style = MaterialTheme.typography.bodyMedium,
                color = c.vcsColor(ch.type) ?: c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val dir = ch.path.substringBeforeLast('/', "")
            if (dir.isNotEmpty()) {
                Text(dir, fontSize = 12.sp, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

/** 16dp checkbox; [checked] null = some but not all (group header). */
@Composable
private fun Check(checked: Boolean?, label: String, enabled: Boolean = true, onToggle: () -> Unit) {
    val c = ide
    Box(
        Modifier.size(18.dp).clip(RoundedCornerShape(3.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .testTag("check:$label"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            when (checked) { true -> Icons.Outlined.CheckBox; false -> Icons.Outlined.CheckBoxOutlineBlank; null -> Icons.Outlined.IndeterminateCheckBox },
            label,
            tint = if (!enabled) c.textDisabled else if (checked != false) c.accent else c.textMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Who the commit will be signed as; warns when git has no user configured. */
@Composable
private fun AuthorLine(p: ProjectState) {
    val c = ide
    var author by remember { mutableStateOf<GitRepo.Author?>(null) }
    LaunchedEffect(p.git.repo) { author = p.git.author() }
    val a = author ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.AutoMirrored.Outlined.CallSplit, null, tint = c.textMuted, modifier = Modifier.size(13.dp))
        Text(p.git.status.branch ?: "", fontSize = 12.sp, color = c.textMuted)
        Text("·", fontSize = 12.sp, color = c.textMuted)
        Text(
            if (a.implicit) "No git user set: commits as ${a.name}" else "${a.name} <${a.email}>",
            fontSize = 12.sp, color = if (a.implicit) c.warning else c.textMuted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Opens [f] next to its committed version. */
internal fun canCompareWithHead(p: ProjectState, f: File?): Boolean =
    f != null && f.isDiagram() && p.git.changeOf(f)?.type == ChangeType.MODIFIED
