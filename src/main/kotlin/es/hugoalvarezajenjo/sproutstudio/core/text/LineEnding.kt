package es.hugoalvarezajenjo.sproutstudio.core.text

/**
 * Line endings at the edge of the app. Inside, text always uses `\n`: the editor, the PlantUML
 * analysis, folding, the formatter and the git line diffs all split on it. Files written on
 * Windows often use `\r\n` (and old Mac files a lone `\r`), so every read from disk or from git
 * goes through [normalize], and a save writes the file back with the ending it already had.
 */
enum class LineEnding(val separator: String) {
    LF("\n"),
    CRLF("\r\n");

    /** [text] (which uses `\n`) with this ending. */
    fun apply(text: String): String = if (this == LF) text else text.replace("\n", separator)

    companion object {
        /** [text] with every `\r\n` and lone `\r` turned into `\n`. */
        fun normalize(text: String): String =
            if ('\r' !in text) text else text.replace("\r\n", "\n").replace('\r', '\n')

        /** The ending [text] uses: [CRLF] when its first line break is `\r\n`, else [LF]. */
        fun of(text: CharSequence): LineEnding {
            val i = text.indexOf('\n')
            return if (i > 0 && text[i - 1] == '\r') CRLF else LF
        }
    }
}
