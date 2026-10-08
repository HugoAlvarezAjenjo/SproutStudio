package es.hugoalvarezajenjo.sproutstudio.core.concurrency

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The threads the app uses, injected so tests can run everything on a test dispatcher.
 *
 *  - [main]: the UI thread. The only place UI state (ViewModels) changes.
 *  - [io]: disk and git.
 *  - [default]: CPU work that isn't PlantUML (PlantUML has its own thread in the render service).
 */
class AppDispatchers(
    val main: CoroutineDispatcher,
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
) {
    companion object {
        val Production = AppDispatchers(Dispatchers.Main, Dispatchers.IO, Dispatchers.Default)
    }
}
