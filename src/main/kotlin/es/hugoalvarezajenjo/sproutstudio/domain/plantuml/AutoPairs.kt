package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Brackets and quotes that close themselves, as in VS Code. Works on the edit the text field
 * reports (old value -> new value) rather than on key codes, so it doesn't depend on the
 * keyboard layout (`{` is ⌥ç on a Spanish Mac).
 *
 * - Typing an opener adds its closer after the caret, when the caret is at the end of the line
 *   or before whitespace or another closer (never in front of a word).
 * - Typing a closer that is already right after the caret steps over it.
 * - Backspace between an empty pair deletes both halves.
 * - Typing an opener over a selection wraps the selection.
 *
 * `'` is not paired: it starts a PlantUML comment.
 */
object AutoPairs {

    private val pairs = mapOf('{' to '}', '(' to ')', '[' to ']', '"' to '"')
    private val closers = pairs.values.toSet()

    /** The value to use instead of [new], or null to keep [new] as the field produced it. */
    fun adjust(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
        val o = old.text
        val n = new.text
        val s = old.selection.min
        val e = old.selection.max
        // One character typed over the selection (or at the caret).
        if (n.length == o.length - (e - s) + 1 && new.selection == TextRange(s + 1) &&
            n.regionMatches(0, o, 0, s) && n.regionMatches(s + 1, o, e, o.length - e)
        ) return typed(o, s, e, n[s])
        // Backspace at a caret.
        if (s == e && s > 0 && n.length == o.length - 1 && new.selection == TextRange(s - 1) &&
            n.regionMatches(0, o, 0, s - 1) && n.regionMatches(s - 1, o, s, o.length - s)
        ) return backspace(o, s)
        return null
    }

    private fun typed(o: String, s: Int, e: Int, ch: Char): TextFieldValue? {
        if (s != e) {
            val close = pairs[ch] ?: return null
            val wrapped = o.substring(0, s) + ch + o.substring(s, e) + close + o.substring(e)
            return TextFieldValue(wrapped, TextRange(s + 1, e + 1))
        }
        val next = o.getOrNull(s)
        if (ch in closers && next == ch) return TextFieldValue(o, TextRange(s + 1))
        val close = pairs[ch] ?: return null
        if (next != null && !next.isWhitespace() && next !in closers) return null
        if (ch == '"' && quotesBefore(o, s) % 2 == 1) return null // this quote ends an open string
        return TextFieldValue(o.substring(0, s) + ch + close + o.substring(s), TextRange(s + 1))
    }

    private fun backspace(o: String, s: Int): TextFieldValue? {
        val close = pairs[o[s - 1]] ?: return null
        if (o.getOrNull(s) != close) return null
        return TextFieldValue(o.removeRange(s - 1, s + 1), TextRange(s - 1))
    }

    private fun quotesBefore(text: String, at: Int): Int =
        text.substring(TextLines.startAt(text, at), at).count { it == '"' }
}
