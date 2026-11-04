package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.hugoalvarezajenjo.sproutstudio.platform.CommandInstaller
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Help > Install 'sproutstudio' Command. The install can wait on the system password dialog, so
 * it runs on [io]; [result] is what the dialog shows afterwards (null: nothing to show).
 */
class InstallCommand(
    private val installer: CommandInstaller,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
) {
    var result by mutableStateOf<CommandInstaller.Result?>(null)
        private set

    var running by mutableStateOf(false)
        private set

    fun install(): Job? {
        if (running) return null
        running = true
        return scope.launch {
            val r = withContext(io) { installer.install() }
            running = false
            // Cancelling the password dialog is the user's answer: nothing to report.
            result = r.takeUnless { it == CommandInstaller.Result.Cancelled }
        }
    }

    fun dismiss() { result = null }
}
