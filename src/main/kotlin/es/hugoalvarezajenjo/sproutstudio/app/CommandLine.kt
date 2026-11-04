package es.hugoalvarezajenjo.sproutstudio.app

import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import java.io.File

/**
 * What the program arguments ask for. Plain file and folder paths open the app, as a double-click
 * does; an option (`--export`, `--check`, `--help`) runs from the terminal instead, with no window.
 */
sealed interface CommandLine {

    /** Open the app, with these files or folders (they may not exist: main skips those). */
    data class Gui(val paths: List<File>) : CommandLine

    /** `--export`: write every diagram in [inputs] as [format], next to it or under [out]. */
    data class Export(val inputs: List<File>, val format: ExportFormat, val out: File?) : CommandLine

    /** `--check`: validate every diagram in [inputs] and write nothing (pre-commit, CI). */
    data class Check(val inputs: List<File>) : CommandLine

    data object Help : CommandLine

    /** Arguments that don't make sense; [message] says why, for the terminal. */
    data class Invalid(val message: String) : CommandLine

    companion object {
        const val USAGE = """Usage:
  sproutstudio [file or folder]...
      Open the app.
  sproutstudio --export <file or folder>... [--format svg|png|pdf] [--out <folder>]
      Export every diagram, without opening a window.
      --format   svg (default), png or pdf
      --out      write here instead of next to each diagram; subfolders are kept
      Exit code: 0 all exported, 1 a diagram failed or none was found, 2 wrong arguments.
  sproutstudio --check <file or folder>...
      Report every diagram with errors, without writing anything.
      Exit code: 0 all valid, 1 a diagram has errors or none was found, 2 wrong arguments.
  sproutstudio --help"""

        fun parse(args: List<String>): CommandLine {
            if (args.none { it.startsWith("--") }) return Gui(args.map(::File))
            if ("--help" in args || "-h" in args) return Help
            return when (args.first()) {
                "--export" -> parseExport(args.drop(1))
                "--check" -> parseCheck(args.drop(1))
                else -> Invalid("unknown option: ${args.first { it.startsWith("--") }}")
            }
        }

        private fun parseCheck(args: List<String>): CommandLine {
            args.firstOrNull { it.startsWith("--") }?.let { return Invalid("unknown option for --check: $it") }
            if (args.isEmpty()) return Invalid("--check needs at least one file or folder")
            return Check(args.map(::File))
        }

        private fun parseExport(args: List<String>): CommandLine {
            val inputs = ArrayList<File>()
            var format = ExportFormat.SVG
            var out: File? = null
            var i = 0
            while (i < args.size) {
                val a = args[i]
                when {
                    a == "--format" || a == "--out" -> {
                        val value = args.getOrNull(i + 1)?.takeUnless { it.startsWith("--") }
                            ?: return Invalid("$a needs a value")
                        if (a == "--out") {
                            out = File(value)
                        } else {
                            format = ExportFormat.entries.firstOrNull { it.extension == value.lowercase() }
                                ?: return Invalid("unknown format: $value (svg, png or pdf)")
                        }
                        i += 2
                    }
                    a.startsWith("--") -> return Invalid("unknown option: $a")
                    else -> { inputs += File(a); i++ }
                }
            }
            if (inputs.isEmpty()) return Invalid("--export needs at least one file or folder")
            return Export(inputs, format, out)
        }
    }
}
