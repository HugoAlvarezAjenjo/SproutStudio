package es.hugoalvarezajenjo.sproutstudio.platform

import java.io.File

/** Windows: Explorer can select the file. Everything else is plain AWT. */
class WindowsPlatform : AwtPlatform() {

    override fun reveal(f: File) {
        // One argument: explorer parses "/select,<path>" itself.
        runCatching { ProcessBuilder("explorer.exe", "/select,${f.absolutePath}").start() }
    }
}
