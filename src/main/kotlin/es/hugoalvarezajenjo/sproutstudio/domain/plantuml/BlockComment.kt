package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * ⌥⌘/ : PlantUML block comments `/' … '/`. Line comments (`'`, ⌘/) are [EditOps.toggleComment].
 */
object BlockComment {

    private const val OPEN = "/'"
    private const val CLOSE = "'/"

    /**
     * Inside an existing block comment, removes it. Otherwise wraps the selection: inline when it
     * sits on one line, with the markers on lines of their own when it spans several. With no
     * selection, inserts an empty `/'  '/` around the caret.
     */
    fun toggle(v: TextFieldValue): TextFieldValue {
        val text = v.text
        val min = v.selection.min
        val max = v.selection.max
        enclosing(text, min, max)?.let { (open, close) -> return remove(v, open, close) }
        if (v.selection.collapsed) {
            return TextFieldValue(text.substring(0, min) + "$OPEN  $CLOSE" + text.substring(min), TextRange(min + OPEN.length + 1))
        }
        val endPos = if (text.getOrNull(max - 1) == '\n') max - 1 else max
        if ('\n' !in text.substring(min, endPos)) {
            val wrapped = "$OPEN ${text.substring(min, max)} $CLOSE"
            return TextFieldValue(text.substring(0, min) + wrapped + text.substring(max), TextRange(min, min + wrapped.length))
        }
        val s = TextLines.startAt(text, min)
        val e = TextLines.endAt(text, endPos)
        val ind = text.substring(s).takeWhile { it == ' ' || it == '\t' }
        val wrapped = "$ind$OPEN\n${text.substring(s, e)}\n$ind$CLOSE"
        return TextFieldValue(text.substring(0, s) + wrapped + text.substring(e), TextRange(s, s + wrapped.length))
    }

    /** The offsets of the `/'` and `'/` around [min]..[max] (blank edges aside), if any. */
    private fun enclosing(text: String, min: Int, max: Int): Pair<Int, Int>? {
        val sel = text.substring(min, max)
        val from = min + (sel.length - sel.trimStart().length)
        val to = maxOf(from, max - (sel.length - sel.trimEnd().length))
        val open = text.lastIndexOf(OPEN, from)
        if (open < 0) return null
        val close = text.indexOf(CLOSE, open + OPEN.length)
        if (close < 0 || close + CLOSE.length < to) return null
        return open to close
    }

    private fun remove(v: TextFieldValue, open: Int, close: Int): TextFieldValue {
        val text = v.text
        val openCut = openerSpan(text, open)
        val closeCut = closerSpan(text, close)
        fun map(p: Int) = p - listOf(openCut, closeCut).sumOf { r -> (minOf(p, r.last + 1) - r.first).coerceAtLeast(0) }
        val newText = text.removeRange(closeCut).removeRange(openCut)
        if (v.selection.collapsed) return TextFieldValue(newText, TextRange(map(v.selection.start)))
        // Select what was inside, without the newline that ended its last line.
        val start = openCut.last + 1
        val end = closeCut.first.let { if (it > start && text[it - 1] == '\n') it - 1 else it }
        return TextFieldValue(newText, TextRange(map(start), map(end)))
    }

    /** What to delete for the `/'` at [at]: its whole line when it stands alone, else it and the space after. */
    private fun openerSpan(text: String, at: Int): IntRange =
        wholeLine(text, at) ?: (at until at + OPEN.length + if (text.getOrNull(at + OPEN.length) == ' ') 1 else 0)

    /** What to delete for the `'/` at [at]: its whole line when it stands alone, else it and the space before. */
    private fun closerSpan(text: String, at: Int): IntRange =
        wholeLine(text, at) ?: ((if (at > TextLines.startAt(text, at) && text[at - 1] == ' ') at - 1 else at) until at + CLOSE.length)

    /** The line holding the 2-char marker at [at], with one of its newlines, when nothing else is on it. */
    private fun wholeLine(text: String, at: Int): IntRange? {
        val ls = TextLines.startAt(text, at)
        val le = TextLines.endAt(text, at)
        if (text.substring(ls, at).isNotBlank() || text.substring(at + 2, le).isNotBlank()) return null
        return when {
            le < text.length -> ls..le
            ls > 0 -> (ls - 1) until le
            else -> null
        }
    }
}
