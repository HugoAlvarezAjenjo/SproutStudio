package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * The `@start…`/`@end…` blocks of a file, found the way PlantUML finds them, so a block number
 * here is the same diagram the preview shows as "1 of 2". PlantUML's rules (checked against the
 * bundled jar in `DiagramBlocksAgreeWithPlantUmlTest`):
 * - `@start` and `@end` are lower case and need a word after them (`@startuml`, `@endmindmap`);
 *   leading spaces are fine.
 * - Inside an open block another `@start…` is plain text, and any `@end…` closes it.
 * - A block that is never closed is not a diagram.
 */
object DiagramBlocks {

    /** One closed block: [kind] is what follows `@start` (`uml`, `mindmap`); lines are 0-based. */
    data class Block(val kind: String, val startLine: Int, val endLine: Int)

    /** The closed blocks of [text], in order. */
    fun of(text: String): List<Block> = scan(text).blocks

    /** The kind of the block still open at the end of [text] (it needs an `@end…`), or null. */
    fun unclosed(text: String): String? = scan(text).open?.first

    private class Scan(val blocks: List<Block>, val open: Pair<String, Int>?)

    private val start = Regex("""^\s*@start(\w+)""")
    private val end = Regex("""^\s*@end\w+""")

    private fun scan(text: String): Scan {
        val starts = TextLines.starts(text)
        val blocks = ArrayList<Block>()
        var open: Pair<String, Int>? = null
        for (line in starts.indices) {
            val l = text.substring(starts[line], TextLines.end(text, starts, line))
            val o = open
            if (o == null) {
                start.find(l)?.let { open = it.groupValues[1] to line }
            } else if (end.containsMatchIn(l)) {
                blocks += Block(o.first, o.second, line)
                open = null
            }
        }
        return Scan(blocks, open)
    }
}
