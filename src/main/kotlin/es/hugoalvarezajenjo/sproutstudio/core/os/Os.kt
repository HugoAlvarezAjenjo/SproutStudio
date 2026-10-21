package es.hugoalvarezajenjo.sproutstudio.core.os

/** Facts about the operating system that never change while the app runs. */
object Os {
    /** The desktops the app supports. Anything that is not macOS or Windows is treated as Linux. */
    enum class Family { MAC, WINDOWS, LINUX }

    val family: Family = familyOf(System.getProperty("os.name"))

    /**
     * macOS keyboard style (⌘, ⌥) versus the PC one (Ctrl, Alt) that Windows and Linux share.
     * The only OS question screens may ask: behaviour lives in Platform, texts in OsTexts.
     */
    val isMac: Boolean = family == Family.MAC

    internal fun familyOf(osName: String): Family {
        val name = osName.lowercase()
        return when {
            "mac" in name -> Family.MAC
            "windows" in name -> Family.WINDOWS
            else -> Family.LINUX
        }
    }
}
