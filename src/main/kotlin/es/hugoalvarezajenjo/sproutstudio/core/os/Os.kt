package es.hugoalvarezajenjo.sproutstudio.core.os

/** Facts about the operating system that never change while the app runs. */
object Os {
    private val name = System.getProperty("os.name").lowercase()
    val isMac: Boolean = "mac" in name
    val isWindows: Boolean = "windows" in name
}
