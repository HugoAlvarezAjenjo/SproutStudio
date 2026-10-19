package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * The `!theme` line of one diagram block: which theme it uses and the edit that changes it.
 * The theme lives in the source, so exports and other PlantUML tools draw the same picture.
 */
object DiagramTheme {

    /** Replace [start]..[end] with [replacement]: one edit to the text. */
    data class Edit(val start: Int, val end: Int, val replacement: String) {
        fun applyTo(text: String) = text.substring(0, start) + replacement + text.substring(end)

        /** Where [offset] of the old text ends up in the new one (a caret inside the edit goes to its end). */
        fun shift(offset: Int): Int = when {
            offset <= start -> offset
            offset >= end -> offset + replacement.length - (end - start)
            else -> start + replacement.length
        }
    }

    /** The theme of block [block] (0-based), or null when it has none or doesn't exist. */
    fun current(text: String, block: Int): String? = find(text, block)?.theme?.name

    /**
     * The edit that makes block [block] use [theme] (null = PlantUML's default look), or null
     * when there's nothing to change. An existing `!theme` line is replaced or removed; otherwise
     * the line goes right after `@start…`.
     */
    fun set(text: String, block: Int, theme: String?): Edit? {
        val b = find(text, block) ?: return null
        val old = b.theme
        if (old != null && old.name == theme) return null
        // A closed block always has its `@end…` below, so both lines here end in a line break.
        return when {
            old != null && theme == null -> Edit(old.lineStart, old.lineEnd + 1, "") // with its break: no blank line left
            old != null -> Edit(old.contentStart, old.lineEnd, "!theme $theme")
            theme == null -> null
            else -> Edit(b.startLineEnd + 1, b.startLineEnd + 1, "${b.indent}!theme $theme\n")
        }
    }

    private class ThemeLine(val name: String, val lineStart: Int, val contentStart: Int, val lineEnd: Int)
    private class Block(val startLineEnd: Int, val indent: String, val theme: ThemeLine?)

    private val themeLine = Regex("""^(\s*)!theme\s+(\S+)""", RegexOption.IGNORE_CASE)

    private fun find(text: String, block: Int): Block? {
        val b = DiagramBlocks.of(text).getOrNull(block) ?: return null
        val starts = TextLines.starts(text)
        val startLine = text.substring(starts[b.startLine], TextLines.end(text, starts, b.startLine))
        // The first `!theme` before the `@end…` is the one PlantUML applies to the whole block.
        val theme = (b.startLine + 1 until b.endLine).firstNotNullOfOrNull { line ->
            val ls = starts[line]
            val le = TextLines.end(text, starts, line)
            themeLine.find(text.substring(ls, le))?.let { m -> ThemeLine(m.groupValues[2], ls, ls + m.groupValues[1].length, le) }
        }
        return Block(starts[b.startLine] + startLine.length, startLine.takeWhile { it == ' ' || it == '\t' }, theme)
    }
}
