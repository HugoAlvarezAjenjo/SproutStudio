package es.hugoalvarezajenjo.sproutstudio.core.viewmodel

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Base of every ViewModel: owns a [scope] on the UI thread that lives exactly as long as the
 * ViewModel. Work launched there survives recomposition and panels opening or closing; [close]
 * cancels it. A child ViewModel passes its parent's job, so closing a window closes all of it.
 *
 * State is Compose state with a private setter; methods are the user's actions.
 */
abstract class ViewModel(main: CoroutineDispatcher, parent: Job? = null) : AutoCloseable {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob(parent) + main)

    override fun close() {
        scope.cancel()
    }
}
