package es.hugoalvarezajenjo.sproutstudio.platform

import java.io.File
import java.nio.file.Path

/**
 * Windows: Explorer can select the file. Everything else is plain AWT.
 * [env] and [home] say where [instanceDir] goes; parameters so tests can set them.
 */
class WindowsPlatform(
    private val env: (String) -> String? = System::getenv,
    private val home: String = System.getProperty("user.home"),
) : AwtPlatform() {

    /** The per-user, non-roaming AppData, which only the user can open. */
    override fun instanceDir(): Path =
        env("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let { Path.of(it, "SproutStudio") }
            ?: Path.of(home, "AppData", "Local", "SproutStudio")

    override fun integrate(hooks: AppHooks) = activeWindows.install()

    override fun reveal(f: File) {
        // One argument: explorer parses "/select,<path>" itself.
        runCatching { ProcessBuilder("explorer.exe", "/select,${f.absolutePath}").start() }
    }

    /** A new console window ("start" opens one) with the folder as its working directory. */
    override fun openTerminal(dir: File) {
        runCatching { ProcessBuilder("cmd.exe", "/c", "start", "cmd.exe").directory(dir).start() }
    }
}
