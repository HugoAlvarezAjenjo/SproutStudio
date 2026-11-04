package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.platform.CommandInstaller.Result
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.dialog_ok
import es.hugoalvarezajenjo.sproutstudio.resources.install_command_app_not_installed
import es.hugoalvarezajenjo.sproutstudio.resources.install_command_done
import es.hugoalvarezajenjo.sproutstudio.resources.install_command_done_title
import es.hugoalvarezajenjo.sproutstudio.resources.install_command_failed
import es.hugoalvarezajenjo.sproutstudio.resources.install_command_failed_title
import es.hugoalvarezajenjo.sproutstudio.resources.install_command_not_ours
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

/** What Install 'sproutstudio' Command did, once it's done. */
@Composable
fun InstallCommandDialog(state: InstallCommand) {
    val r = state.result ?: return
    val title = if (r is Result.Installed) Res.string.install_command_done_title else Res.string.install_command_failed_title
    val body = when (r) {
        is Result.Installed -> tr(Res.string.install_command_done)
        is Result.NotOurs -> tr(Res.string.install_command_not_ours, r.target.toString())
        Result.AppNotInstalled -> tr(Res.string.install_command_app_not_installed)
        is Result.Failed -> tr(Res.string.install_command_failed, r.message)
        Result.Cancelled -> return
    }
    AlertDialog(
        onDismissRequest = state::dismiss,
        containerColor = ide.popup,
        shape = RoundedCornerShape(10.dp),
        title = { Text(tr(title), style = MaterialTheme.typography.titleMedium) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = state::dismiss) { Text(tr(Res.string.dialog_ok)) } },
    )
}
