package es.hugoalvarezajenjo.sproutstudio.core.os

/** Facts about the operating system that never change while the app runs. */
object Os {
    val isMac: Boolean = System.getProperty("os.name").lowercase().contains("mac")
}
