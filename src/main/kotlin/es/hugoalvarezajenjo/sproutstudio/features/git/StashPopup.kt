package es.hugoalvarezajenjo.sproutstudio.features.git

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.dialog_cancel
import es.hugoalvarezajenjo.sproutstudio.resources.git_drop
import es.hugoalvarezajenjo.sproutstudio.resources.git_drop_hint
import es.hugoalvarezajenjo.sproutstudio.resources.git_drop_text
import es.hugoalvarezajenjo.sproutstudio.resources.git_drop_title
import es.hugoalvarezajenjo.sproutstudio.resources.git_pop
import es.hugoalvarezajenjo.sproutstudio.resources.git_pop_hint
import es.hugoalvarezajenjo.sproutstudio.resources.git_stash
import es.hugoalvarezajenjo.sproutstudio.resources.git_stash_description
import es.hugoalvarezajenjo.sproutstudio.resources.git_stash_hint
import es.hugoalvarezajenjo.sproutstudio.resources.git_stash_saved
import es.hugoalvarezajenjo.sproutstudio.resources.git_stash_title
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import es.hugoalvarezajenjo.sproutstudio.services.git.StashInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.features.git.describe
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.services.git.ChangeType
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide

/**
 * Stash: put your uncommitted changes aside (to switch branch or try something else) and bring
 * them back later. Opens below the Commit panel's stash button.
 */
@Composable
internal fun StashPopup(p: WorkspaceViewModel, onDismiss: () -> Unit) {
    val c = ide
    val git = p.git
    var message by remember { mutableStateOf("") }
    var confirmDrop by remember { mutableStateOf<StashInfo?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { git.clearError() }

    fun run(op: suspend () -> Boolean) {
        busy = true
        // Disk changes under open tabs: same care as switching branches.
        p.gitFlow.changeWorkingTree(op) { busy = false }
    }

    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, 34),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp), color = c.popup, shadowElevation = 12.dp,
            modifier = Modifier.width(320.dp).border(1.dp, c.popupBorder, RoundedCornerShape(8.dp)).testTag("stash-popup"),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(Res.string.git_stash_title), style = MaterialTheme.typography.labelLarge, color = c.text)
                Text(tr(Res.string.git_stash_hint), fontSize = 12.sp, color = c.textMuted)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(c.editor)
                        .border(1.dp, c.popupBorder, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    if (message.isEmpty()) Text(tr(Res.string.git_stash_description), fontSize = 13.sp, color = c.textDisabled)
                    BasicTextField(
                        message, { message = it }, singleLine = true,
                        textStyle = TextStyle(fontSize = 13.sp, color = c.text), cursorBrush = SolidColor(c.text),
                        modifier = Modifier.fillMaxWidth().testTag("stash-message"),
                    )
                }
                Row {
                    Spacer(Modifier.weight(1f))
                    ToolButton(
                        null, label = tr(Res.string.git_stash), primary = true,
                        enabled = !busy && git.status.head != null && git.status.changes.any { it.type != ChangeType.UNVERSIONED },
                    ) { run { git.stash(message).also { if (it) message = "" } } }
                }
                if (git.stashes.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.popupBorder))
                    Text(tr(Res.string.git_stash_saved), fontSize = 11.sp, color = c.textMuted)
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(git.stashes, key = { "${it.index}:${it.timeMillis}" }) { st ->
                            Row(
                                Modifier.fillMaxWidth().height(30.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(st.message.removePrefix("On ").substringAfter(": ", st.message), fontSize = 13.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(relativeTime(st.timeMillis), fontSize = 11.sp, color = c.textMuted)
                                }
                                ToolButton(null, tooltip = tr(Res.string.git_pop_hint), label = tr(Res.string.git_pop), enabled = !busy) { run { git.popStash(st.index) } }
                                ToolButton(null, tooltip = tr(Res.string.git_drop_hint), label = tr(Res.string.git_drop), enabled = !busy) { confirmDrop = st }
                            }
                        }
                    }
                }
                git.error?.let { Text(it.describe(), color = c.error, fontSize = 12.sp) }
            }
        }
    }

    confirmDrop?.let { st ->
        AlertDialog(
            onDismissRequest = { confirmDrop = null },
            containerColor = ide.popup,
            shape = RoundedCornerShape(10.dp),
            title = { Text(tr(Res.string.git_drop_title), style = MaterialTheme.typography.titleMedium) },
            text = { Text(tr(Res.string.git_drop_text), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { TextButton(onClick = { confirmDrop = null; run { git.dropStash(st.index) } }) { Text(tr(Res.string.git_drop), color = ide.error) } },
            dismissButton = { TextButton(onClick = { confirmDrop = null }) { Text(tr(Res.string.dialog_cancel)) } },
        )
    }
}
