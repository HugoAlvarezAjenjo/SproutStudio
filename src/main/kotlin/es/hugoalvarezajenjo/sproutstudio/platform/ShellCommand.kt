package es.hugoalvarezajenjo.sproutstudio.platform

import java.nio.file.Path

/**
 * The `sproutstudio` terminal command on macOS: a small shell script in a folder on the PATH
 * that calls the app bundle. (The Linux package installs its own; see packaging/linux.)
 *
 * - With any option (`--export`, `--check`, `--help`, or a wrong one): runs the app's binary,
 *   so the output and the exit code reach the terminal.
 * - Otherwise: `open -a`, so files and folders go to the running app, as a double-click does.
 */
object ShellCommand {
    const val NAME = "sproutstudio"

    /** First comment line of every script we write: an existing file without it isn't ours. */
    const val MARKER = "# Installed by SproutStudio"

    fun isOurs(script: String): Boolean = MARKER in script

    fun macScript(app: Path): String {
        val bundle = shellQuote(app.toString())
        return """
            |#!/bin/sh
            |$MARKER (Help > Install 'sproutstudio' Command). Reinstall it if the app moves.
            |APP=$bundle
            |for arg in "$@"; do
            |  case "${'$'}arg" in -*) exec "${'$'}APP/Contents/MacOS/SproutStudio" "$@" ;; esac
            |done
            |exec open -a "${'$'}APP" "$@"
            |""".trimMargin()
    }

    /** One POSIX shell word for [s]: single quotes, each ' written as '\''. */
    internal fun shellQuote(s: String): String = "'" + s.replace("'", "'\\''") + "'"
}
