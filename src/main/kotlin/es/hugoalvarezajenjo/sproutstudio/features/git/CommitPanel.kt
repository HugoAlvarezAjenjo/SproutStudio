package es.hugoalvarezajenjo.sproutstudio.features.git

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.git_add_gitignore
import es.hugoalvarezajenjo.sproutstudio.resources.git_add_gitignore_hint
import es.hugoalvarezajenjo.sproutstudio.resources.git_amend
import es.hugoalvarezajenjo.sproutstudio.resources.git_amend_commit
import es.hugoalvarezajenjo.sproutstudio.resources.git_amend_tooltip
import es.hugoalvarezajenjo.sproutstudio.resources.git_changes
import es.hugoalvarezajenjo.sproutstudio.resources.git_commit
import es.hugoalvarezajenjo.sproutstudio.resources.git_commit_message
import es.hugoalvarezajenjo.sproutstudio.resources.git_commit_tooltip
import es.hugoalvarezajenjo.sproutstudio.resources.git_files
import es.hugoalvarezajenjo.sproutstudio.resources.git_hide_panel
import es.hugoalvarezajenjo.sproutstudio.resources.git_include_all
import es.hugoalvarezajenjo.sproutstudio.resources.git_include_file
import es.hugoalvarezajenjo.sproutstudio.resources.git_last_commit
import es.hugoalvarezajenjo.sproutstudio.resources.git_n_of_files
import es.hugoalvarezajenjo.sproutstudio.resources.git_no_changes
import es.hugoalvarezajenjo.sproutstudio.resources.git_no_files_yet
import es.hugoalvarezajenjo.sproutstudio.resources.git_no_repo
import es.hugoalvarezajenjo.sproutstudio.resources.git_no_repo_hint
import es.hugoalvarezajenjo.sproutstudio.resources.git_no_user
import es.hugoalvarezajenjo.sproutstudio.resources.git_open
import es.hugoalvarezajenjo.sproutstudio.resources.git_refresh
import es.hugoalvarezajenjo.sproutstudio.resources.git_rollback_menu
import es.hugoalvarezajenjo.sproutstudio.resources.git_show_diff
import es.hugoalvarezajenjo.sproutstudio.resources.git_show_history
import es.hugoalvarezajenjo.sproutstudio.resources.git_stash_changes
import es.hugoalvarezajenjo.sproutstudio.resources.git_stash_count
import es.hugoalvarezajenjo.sproutstudio.resources.git_undo_last_hint
import es.hugoalvarezajenjo.sproutstudio.resources.git_unversioned
import es.hugoalvarezajenjo.sproutstudio.resources.menu_compare_with_head
import es.hugoalvarezajenjo.sproutstudio.resources.menu_create_git_repo
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr
import es.hugoalvarezajenjo.sproutstudio.services.git.GitAuthor
import es.hugoalvarezajenjo.sproutstudio.core.os.shortcutHint
import androidx.compose.foundation.ContextMenuArea
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
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.History
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.MenuItem
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.menu
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.IndeterminateCheckBox
import androidx.compose.material.icons.outlined.Inventory2
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
import es.hugoalvarezajenjo.sproutstudio.features.document.isDiagram
import es.hugoalvarezajenjo.sproutstudio.features.git.describe
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.services.git.ChangeType
import es.hugoalvarezajenjo.sproutstudio.services.git.FileChange
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.EmptyState
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.HLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide

/**
 * IntelliJ's Commit tool window (⌘0): what changed since the last commit, checkboxes to pick
 * what goes in, a message and Commit. Local only, nothing is pushed.
 */
@Composable
internal fun CommitPanel(p: WorkspaceViewModel) {
    val c = ide
    val git = p.git
    var confirmRollback by remember { mutableStateOf<FileChange?>(null) }
    val messageFocus = remember { FocusRequester() }
    var stashOpen by remember { mutableStateOf(false) }
    var withGitignore by remember { mutableStateOf(true) }
    LaunchedEffect(git) { git.stashPopupRequests.collect { stashOpen = true } }
    // ⌘K / "Commit…" from elsewhere puts the caret in the message box.
    LaunchedEffect(p) { p.commitFocusRequests.collect { runCatching { messageFocus.requestFocus() } } }

    Column(Modifier.width(p.sidebarWidth.dp).fillMaxHeight().background(c.panel).testTag("commit-panel")) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(tr(Res.string.git_commit), style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.weight(1f))
            if (git.isRepo) {
                Box {
                    ToolButton(
                        Icons.Outlined.Inventory2,
                        if (git.stashes.isEmpty()) tr(Res.string.git_stash_changes) else tr(Res.string.git_stash_count, git.stashes.size),
                        selected = stashOpen,
                    ) { stashOpen = !stashOpen }
                    if (stashOpen) StashPopup(p, onDismiss = { stashOpen = false })
                }
                ToolButton(Icons.Outlined.Refresh, tr(Res.string.git_refresh)) { git.requestRefresh() }
            }
            ToolButton(Icons.Outlined.Remove, tr(Res.string.git_hide_panel, shortcutHint("0"))) { p.showSidebar(false) }
        }

        if (!git.isRepo) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            ) {
                EmptyState(Icons.Outlined.Commit, tr(Res.string.git_no_repo), tr(Res.string.git_no_repo_hint))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Check(withGitignore, tr(Res.string.git_add_gitignore)) { withGitignore = !withGitignore }
                    Text(tr(Res.string.git_add_gitignore_hint), fontSize = 12.sp, color = c.textMuted)
                }
                ToolButton(null, label = tr(Res.string.menu_create_git_repo), primary = true) {
                    p.gitFlow.init(withGitignore)
                }
                git.error?.let { Text(it.describe(), color = c.error, fontSize = 12.sp) }
            }
            return@Column
        }

        val changes = git.status.changes
        val tracked = changes.filter { it.type != ChangeType.UNVERSIONED }
        val unversioned = changes.filter { it.type == ChangeType.UNVERSIONED }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (changes.isEmpty()) {
                Text(
                    if (git.status.head == null) tr(Res.string.git_no_files_yet) else tr(Res.string.git_no_changes),
                    fontSize = 12.sp, color = c.textMuted, modifier = Modifier.padding(start = 14.dp, top = 8.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                if (tracked.isNotEmpty()) {
                    item(key = "h-changes") { GroupHeader(p, tr(Res.string.git_changes), tracked) }
                    items(tracked, key = { "c:" + it.path }) { ch -> ChangeRow(p, ch, onRollback = { confirmRollback = it }) }
                }
                if (unversioned.isNotEmpty()) {
                    item(key = "h-unversioned") { GroupHeader(p, tr(Res.string.git_unversioned), unversioned) }
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
                if (git.commitMessage.isEmpty()) Text(tr(Res.string.git_commit_message), fontSize = 13.sp, color = c.textDisabled)
                BasicTextField(
                    value = git.commitMessage,
                    onValueChange = git::editMessage,
                    textStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = c.text),
                    cursorBrush = SolidColor(c.text),
                    modifier = Modifier.fillMaxWidth().focusRequester(messageFocus).testTag("commit-message")
                        .onPreviewKeyEvent { e ->
                            val cmd = e.isMetaPressed || e.isCtrlPressed
                            if (e.type == KeyEventType.KeyDown && cmd && e.key == Key.Enter) { p.gitFlow.commit(); true } else false
                        },
                )
            }
            val n = git.included.size
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton(
                    null, tooltip = tr(if (git.amend) Res.string.git_amend_tooltip else Res.string.git_commit_tooltip, shortcutHint("⏎")),
                    label = if (git.amend) tr(Res.string.git_amend_commit) else tr(Res.string.git_commit), primary = true,
                    enabled = git.canCommit,
                ) { p.gitFlow.commit() }
                Text(
                    if (changes.isEmpty()) "" else tr(Res.plurals.git_n_of_files, changes.size, n, changes.size),
                    fontSize = 12.sp, color = c.textMuted, modifier = Modifier.weight(1f),
                )
                if (git.status.head != null) {
                    Check(git.amend, tr(Res.string.git_amend)) { git.toggleAmend() }
                    Text(tr(Res.string.git_amend), fontSize = 12.sp, color = c.textMuted)
                }
            }
            git.lastCommit?.let { last ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        tr(Res.string.git_last_commit, last.message), fontSize = 12.sp, color = c.textMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    Text("· ${last.short}", fontSize = 12.sp, color = c.textMuted)
                    if (last.parents > 0) {
                        Spacer(Modifier.width(2.dp))
                        ToolButton(Icons.AutoMirrored.Outlined.Undo, tr(Res.string.git_undo_last_hint)) { p.gitFlow.undoLastCommit() }
                    }
                }
            }
            AuthorLine(p)
            git.error?.let { Text(it.describe(), color = c.error, fontSize = 12.sp, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            git.notice?.let { Text(it.describe(), color = c.success, fontSize = 12.sp) }
        }
    }

    confirmRollback?.let { ch -> RollbackDialog(p, ch, onDismiss = { confirmRollback = null }) }
}

/** "Changes  3 files" with a tri-state checkbox for the whole group. */
@Composable
private fun GroupHeader(p: WorkspaceViewModel, title: String, items: List<FileChange>) {
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
            tr(Res.string.git_include_all, title),
        ) { val all = on < items.size; items.forEach { git.setIncluded(it, all) } }
        Text(title, style = MaterialTheme.typography.labelLarge, color = c.text)
        Text(tr(Res.plurals.git_files, items.size, items.size), fontSize = 12.sp, color = c.textMuted)
    }
}

@Composable
private fun ChangeRow(p: WorkspaceViewModel, ch: FileChange, onRollback: ((FileChange) -> Unit)?) {
    val c = ide
    val git = p.git
    val canOpen = ch.file.isDiagram()
    val canCompare = canOpen && ch.type == ChangeType.MODIFIED
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val isActive = p.active?.file?.let { git.changeOf(it)?.path } == ch.path
    val hasHead = ch.type == ChangeType.MODIFIED || ch.type == ChangeType.DELETED
    val menu = menu {
        group { if (canOpen) add(MenuItem(text(Res.string.git_open)) { p.open(ch.file) }) }
        group {
            if (canCompare) add(MenuItem(text(Res.string.menu_compare_with_head), Icons.Outlined.Difference) { p.deps.windows.compareWithHead(p, ch.file) })
            if (hasHead) add(MenuItem(text(Res.string.git_show_diff)) { p.deps.windows.compareWithHead(p, ch.file, textFirst = true) })
            if (hasHead) add(MenuItem(text(Res.string.git_show_history), Icons.Outlined.History) { p.deps.windows.showHistory(p, ch.file) })
        }
        group { if (onRollback != null) add(MenuItem(text(Res.string.git_rollback_menu), Icons.AutoMirrored.Outlined.Undo) { onRollback(ch) }) }
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
                        onDoubleTap = {
                            when {
                                canCompare -> p.deps.windows.compareWithHead(p, ch.file)
                                hasHead -> p.deps.windows.compareWithHead(p, ch.file, textFirst = true)
                                canOpen -> p.open(ch.file)
                            }
                        },
                    )
                }
                .padding(start = 22.dp, end = 8.dp)
                .testTag("change:${ch.path}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Check(git.isIncluded(ch), tr(Res.string.git_include_file, ch.file.name), enabled = ch.type != ChangeType.CONFLICT) {
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
private fun AuthorLine(p: WorkspaceViewModel) {
    val c = ide
    var author by remember { mutableStateOf<GitAuthor?>(null) }
    LaunchedEffect(p.git.repo) { author = p.git.author() }
    val a = author ?: return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.AutoMirrored.Outlined.CallSplit, null, tint = c.textMuted, modifier = Modifier.size(13.dp))
        Text(p.git.status.branch ?: "", fontSize = 12.sp, color = c.textMuted)
        Text("·", fontSize = 12.sp, color = c.textMuted)
        Text(
            if (a.implicit) tr(Res.string.git_no_user, a.name) else "${a.name} <${a.email}>",
            fontSize = 12.sp, color = if (a.implicit) c.warning else c.textMuted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}
