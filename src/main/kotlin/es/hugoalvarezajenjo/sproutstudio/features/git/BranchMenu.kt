package es.hugoalvarezajenjo.sproutstudio.features.git

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.branch_create
import es.hugoalvarezajenjo.sproutstudio.resources.branch_current
import es.hugoalvarezajenjo.sproutstudio.resources.branch_current_desc
import es.hugoalvarezajenjo.sproutstudio.resources.branch_delete
import es.hugoalvarezajenjo.sproutstudio.resources.branch_delete_named
import es.hugoalvarezajenjo.sproutstudio.resources.branch_delete_text
import es.hugoalvarezajenjo.sproutstudio.resources.branch_delete_title
import es.hugoalvarezajenjo.sproutstudio.resources.branch_local
import es.hugoalvarezajenjo.sproutstudio.resources.branch_need_commit
import es.hugoalvarezajenjo.sproutstudio.resources.branch_new
import es.hugoalvarezajenjo.sproutstudio.resources.branch_new_hint
import es.hugoalvarezajenjo.sproutstudio.resources.branch_stash_and_switch
import es.hugoalvarezajenjo.sproutstudio.resources.dialog_cancel
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import es.hugoalvarezajenjo.sproutstudio.features.git.describe
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.StatusText
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide

/**
 * The branch in the status bar, as a button: IntelliJ's branch popup with the local branches
 * (click to switch), tr(Res.string.branch_new) and delete. Local only: no fetch, pull or push.
 */
@Composable
internal fun BranchButton(p: WorkspaceViewModel, initiallyOpen: Boolean = false) {
    val branch = p.git.status.branch ?: return
    val c = ide
    var open by remember { mutableStateOf(initiallyOpen) }
    Box {
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Box(
            Modifier.clip(RoundedCornerShape(4.dp))
                .background(if (open || hovered) c.hover else Color.Transparent)
                .hoverable(interaction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { open = !open }
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .testTag("branch-button"),
        ) { StatusText(branch, c.text.copy(alpha = 0.85f), Icons.AutoMirrored.Outlined.CallSplit) }
        if (open) BranchPopup(p, onDismiss = { open = false })
    }
}

/** Opens upward from the status bar, right-aligned with the button. */
private object AboveAnchor : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = (anchorBounds.top - popupContentSize.height - 4).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

@Composable
private fun BranchPopup(p: WorkspaceViewModel, onDismiss: () -> Unit) {
    val c = ide
    val git = p.git
    var creating by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun run(op: suspend () -> Boolean, closeOnSuccess: Boolean = true) {
        busy = true
        p.gitFlow.changeWorkingTree(op) { ok ->
            busy = false
            if (ok && closeOnSuccess) onDismiss()
        }
    }

    Popup(AboveAnchor, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Surface(
            shape = RoundedCornerShape(8.dp), color = c.popup, shadowElevation = 12.dp,
            modifier = Modifier.width(300.dp).border(1.dp, c.popupBorder, RoundedCornerShape(8.dp)).testTag("branch-popup"),
        ) {
            Column(Modifier.padding(vertical = 6.dp)) {
                if (creating) {
                    NewBranchField(
                        error = git.error?.describe(),
                        onCancel = { creating = false; git.clearError() },
                        onCreate = { name -> run({ git.createBranch(name) }) },
                    )
                } else {
                    MenuRow(Icons.Outlined.Add, tr(Res.string.branch_new), enabled = git.status.head != null && !busy) {
                        git.clearError(); creating = true
                    }
                    if (git.status.head == null) Hint(tr(Res.string.branch_need_commit))
                    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(c.popupBorder))
                    Text(
                        tr(Res.string.branch_local), fontSize = 11.sp, color = c.textMuted,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(git.branches, key = { it }) { b ->
                            val current = b == git.status.branch
                            BranchRow(
                                b, current, enabled = !busy,
                                onClick = { if (!current) run({ git.checkout(b) }) else onDismiss() },
                                onDelete = if (current) null else ({ confirmDelete = b }),
                            )
                        }
                    }
                    git.error?.let {
                        Text(it.describe(), color = c.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                    git.blockedCheckout?.let { target ->
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
                            ToolButton(null, label = tr(Res.string.branch_stash_and_switch, target), primary = true, enabled = !busy) {
                                run({ git.stashAndCheckout(target) })
                            }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = ide.popup,
            shape = RoundedCornerShape(10.dp),
            title = { Text(tr(Res.string.branch_delete_title, b), style = MaterialTheme.typography.titleMedium) },
            text = { Text(tr(Res.string.branch_delete_text, git.status.branch.orEmpty()), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; run({ git.deleteBranch(b) }, closeOnSuccess = false) }) { Text(tr(Res.string.branch_delete), color = ide.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(tr(Res.string.dialog_cancel)) } },
        )
    }
}

@Composable
private fun NewBranchField(error: String?, onCancel: () -> Unit, onCreate: (String) -> Unit) {
    val c = ide
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr(Res.string.branch_new_hint), fontSize = 12.sp, color = c.textMuted)
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.editor)
                .border(1.dp, if (error != null) c.error else c.accent, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            if (name.isEmpty()) Text("feature/my-change", fontSize = 13.sp, color = c.textDisabled)
            BasicTextField(
                value = name,
                // Spaces are never valid in a branch name; IntelliJ turns them into dashes too.
                onValueChange = { name = it.replace(' ', '-') },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = c.text),
                cursorBrush = SolidColor(c.text),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("new-branch-name").onPreviewKeyEvent { e ->
                    when {
                        e.type != KeyEventType.KeyDown -> false
                        e.key == Key.Enter -> { if (name.isNotBlank()) onCreate(name.trim()); true }
                        e.key == Key.Escape -> { onCancel(); true }
                        else -> false
                    }
                },
            )
        }
        error?.let { Text(it, color = c.error, fontSize = 12.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            ToolButton(null, label = tr(Res.string.dialog_cancel)) { onCancel() }
            ToolButton(null, label = tr(Res.string.branch_create), primary = true, enabled = name.isNotBlank()) { onCreate(name.trim()) }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 4.dp).clip(RoundedCornerShape(4.dp))
            .background(if (hovered && enabled) c.selection else Color.Transparent)
            .hoverable(interaction).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (enabled) c.text else c.textDisabled, modifier = Modifier.size(15.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (enabled) c.text else c.textDisabled)
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, fontSize = 11.sp, color = ide.textMuted, modifier = Modifier.padding(start = 35.dp, end = 12.dp, bottom = 2.dp))

@Composable
private fun BranchRow(name: String, current: Boolean, enabled: Boolean, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 4.dp).clip(RoundedCornerShape(4.dp))
            .background(if (hovered) c.selection else Color.Transparent)
            .hoverable(interaction).clickable(enabled = enabled, onClick = onClick).padding(start = 8.dp, end = 4.dp)
            .testTag("branch:$name"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(15.dp), contentAlignment = Alignment.Center) {
            if (current) Icon(Icons.Outlined.Check, tr(Res.string.branch_current_desc), tint = c.accent, modifier = Modifier.size(15.dp))
            else Icon(Icons.AutoMirrored.Outlined.CallSplit, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
        }
        Text(
            name, style = MaterialTheme.typography.bodyMedium, color = c.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        if (current) Text(tr(Res.string.branch_current), fontSize = 11.sp, color = c.textMuted, modifier = Modifier.padding(end = 6.dp))
        if (onDelete != null && hovered) {
            Box(
                Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)).clickable(enabled = enabled, onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.DeleteOutline, tr(Res.string.branch_delete_named, name), tint = c.textMuted, modifier = Modifier.size(15.dp)) }
        }
    }
}

