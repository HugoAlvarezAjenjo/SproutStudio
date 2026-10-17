package es.hugoalvarezajenjo.sproutstudio

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Image
import java.awt.EventQueue

/**
 * Off-screen renders for tests, on the Swing UI thread.
 *
 * A scene watches state on the thread it renders on, and Compose Desktop sends its global state
 * notifications on the Swing thread (as do the app's ViewModels). Rendering from the test thread
 * worked only until a notification landed mid-frame, then failed with "multithreaded access to
 * SnapshotStateObserver". So every scene call goes to the UI thread, and the waits between
 * frames stay on the test thread, so the UI thread is free to run the app's work meanwhile.
 */
object Offscreen {

    /**
     * Renders [content] at [width]×[height]: a first frame, then one more after each pause (ms)
     * in [frames], so async work (renders, debounces) can settle. [read] gets the last frame
     * while the scene is still open.
     */
    fun <T> render(
        width: Int, height: Int, frames: List<Long> = emptyList(),
        content: @Composable () -> Unit, read: (Image) -> T,
    ): T {
        val scene = onUi { ImageComposeScene(width, height, Density(1f), content = content) }
        try {
            var img = onUi { scene.render(0) }
            for (t in frames) {
                Thread.sleep(t)
                img = onUi { scene.render(System.nanoTime()) }
            }
            return onUi { read(img) }
        } finally {
            onUi { scene.close() }
        }
    }

    private fun <T> onUi(block: () -> T): T {
        if (EventQueue.isDispatchThread()) return block()
        var result: Result<T>? = null
        EventQueue.invokeAndWait { result = runCatching(block) }
        return result!!.getOrThrow()
    }
}
