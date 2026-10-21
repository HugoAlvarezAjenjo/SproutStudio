package es.hugoalvarezajenjo.sproutstudio.platform

import java.io.File

/**
 * What the OS may ask the running app to do from outside its windows, handed to
 * [Platform.integrate]. Every callback runs on the UI thread except [beforeQuit].
 */
data class AppHooks(
    /** Files opened from the file manager while the app runs (or while it starts). */
    val openFiles: (List<File>) -> Unit,
    val openSettings: () -> Unit,
    /** The OS is about to end the app (⌘Q): save what is pending. Blocks; runs on the OS's thread. */
    val beforeQuit: () -> Unit,
    /** The app theme at startup, for a native title bar that matches it. */
    val dark: Boolean,
)
