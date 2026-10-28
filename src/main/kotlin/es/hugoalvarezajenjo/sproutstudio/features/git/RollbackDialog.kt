package es.hugoalvarezajenjo.sproutstudio.features.git

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.dialog_cancel
import es.hugoalvarezajenjo.sproutstudio.resources.editor_rollback
import es.hugoalvarezajenjo.sproutstudio.resources.git_rollback_added
import es.hugoalvarezajenjo.sproutstudio.resources.git_rollback_modified
import es.hugoalvarezajenjo.sproutstudio.resources.git_rollback_title
import es.hugoalvarezajenjo.sproutstudio.services.git.ChangeType
import es.hugoalvarezajenjo.sproutstudio.services.git.FileChange
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

/** "Rollback a.puml?": throws away [ch]'s uncommitted changes once confirmed. */
@Composable
internal fun RollbackDialog(p: WorkspaceViewModel, ch: FileChange, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ide.popup,
        shape = RoundedCornerShape(10.dp),
        title = { Text(tr(Res.string.git_rollback_title, ch.file.name), style = MaterialTheme.typography.titleMedium) },
        text = {
            Text(
                if (ch.type == ChangeType.ADDED) tr(Res.string.git_rollback_added) else tr(Res.string.git_rollback_modified),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = { onDismiss(); p.gitFlow.rollback(ch) }) { Text(tr(Res.string.editor_rollback), color = ide.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(Res.string.dialog_cancel)) } },
    )
}

/** Whether "Rollback…" can do something for [ch]: a change git knows about (not a file git doesn't track). */
internal fun canRollback(ch: FileChange?): Boolean = ch != null && ch.type != ChangeType.UNVERSIONED
