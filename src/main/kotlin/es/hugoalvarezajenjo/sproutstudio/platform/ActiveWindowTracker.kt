package es.hugoalvarezajenjo.sproutstudio.platform

import java.awt.AWTEvent
import java.awt.Frame
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.WindowEvent
import java.lang.ref.WeakReference

/**
 * Remembers the app window the user was last in, so [AwtPlatform.bringToFront] raises that one.
 * AWT can't tell it once the app is in the background: its active window is null then, and
 * `Window.getWindows()` lists windows in no particular order.
 */
class ActiveWindowTracker {

    /** Weak: a window that was closed isn't kept alive for this. */
    private var last: WeakReference<Window>? = null

    /** Starts listening. Creates the AWT toolkit, so call it where that is already allowed. */
    fun install() {
        Toolkit.getDefaultToolkit().addAWTEventListener(
            { e -> if (e.id == WindowEvent.WINDOW_ACTIVATED) activated((e as WindowEvent).window) },
            AWTEvent.WINDOW_EVENT_MASK,
        )
    }

    internal fun activated(w: Window) { last = WeakReference(w) }

    /** The last active window if it is still shown, otherwise any shown window; null if none. */
    fun frontmost(shown: List<Window> = Window.getWindows().filter { it.isShowing }): Window? =
        last?.get()?.takeIf { it in shown } ?: shown.firstOrNull { it is Frame } ?: shown.firstOrNull()
}
