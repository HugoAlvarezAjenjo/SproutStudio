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
 * - Typing a closer steps over the one it added, but not over one that was already in the text.
 * - Backspace between a pair it added deletes both halves.
 * - Typing an opener over a selection wraps the selection.
 *
 * Like VS Code, it remembers the pairs it added only while the caret stays inside them and the
 * text changes by typing alone: moving out, or a command (Enter, undo, a completion...) that
 * rewrites the text, forgets them. One per editor; `'` is not paired, it starts a PlantUML comment.
 */
class AutoPairs {

    /** A pair this added, as offsets of its opener and closer in [known]. */
    private data class Added(val open: Int, val close: Int)

    private var added = listOf<Added>()

    /** The text [added] refers to: anything else means the text changed behind our back. */
    private var known: String? = null

    /** Every value the editor shows that didn't go through [adjust]: caret moves and commands. */
    fun observe(value: TextFieldValue) {
        if (value.text != known) added = emptyList()
        known = value.text
        prune(value)
    }

    /** The value to use instead of [new], or null to keep [new] as the field produced it. */
    fun adjust(old: TextFieldValue, new: TextFieldValue): TextFieldValue? {
        if (old.text != known) added = emptyList()
        val o = old.text
        val n = new.text
        val s = old.selection.min
        val e = old.selection.max
        var opened: Added? = null
        val result = when {
            // One character typed over the selection (or at the caret).
            n.length == o.length - (e - s) + 1 && new.selection == TextRange(s + 1) &&
                n.regionMatches(0, o, 0, s) && n.regionMatches(s + 1, o, e, o.length - e) ->
                typed(o, s, e, n[s])?.let { (value, pair) -> opened = pair; value }
            // Backspace at a caret.
            s == e && s > 0 && n.length == o.length - 1 && new.selection == TextRange(s - 1) &&
                n.regionMatches(0, o, 0, s - 1) && n.regionMatches(s - 1, o, s, o.length - s) ->
                backspace(o, s)
            else -> null
        }
        val final = result ?: new
        added = remap(added, o, final.text) + listOfNotNull(opened)
        known = final.text
        prune(final)
        return result
    }

    /** The adjusted value for [ch] typed over `o[s, e)`, and the pair it opened, if any. */
    private fun typed(o: String, s: Int, e: Int, ch: Char): Pair<TextFieldValue, Added?>? {
        if (s != e) {
            val close = PAIRS[ch] ?: return null
            val wrapped = o.substring(0, s) + ch + o.substring(s, e) + close + o.substring(e)
            return TextFieldValue(wrapped, TextRange(s + 1, e + 1)) to null
        }
        val next = o.getOrNull(s)
        if (ch in CLOSERS && next == ch && added.any { it.close == s }) return TextFieldValue(o, TextRange(s + 1)) to null
        val close = PAIRS[ch] ?: return null
        if (next != null && !next.isWhitespace() && next !in CLOSERS) return null
        if (ch == '"' && quotesBefore(o, s) % 2 == 1) return null // this quote ends an open string
        return TextFieldValue(o.substring(0, s) + ch + close + o.substring(s), TextRange(s + 1)) to Added(s, s + 1)
    }

    private fun backspace(o: String, s: Int): TextFieldValue? {
        if (added.none { it.open == s - 1 && it.close == s }) return null
        return TextFieldValue(o.removeRange(s - 1, s + 1), TextRange(s - 1))
    }

    /** Keeps the pairs the caret is still inside of, whose characters are still there. */
    private fun prune(value: TextFieldValue) {
        val t = value.text
        val caret = value.selection.start
        added = if (!value.selection.collapsed) emptyList() else added.filter {
            caret > it.open && caret <= it.close && PAIRS[t.getOrNull(it.open)] == t.getOrNull(it.close)
        }
    }

    private fun quotesBefore(text: String, at: Int): Int =
        text.substring(TextLines.startAt(text, at), at).count { it == '"' }

    private companion object {
        val PAIRS = mapOf('{' to '}', '(' to ')', '[' to ']', '"' to '"')
        val CLOSERS = PAIRS.values.toSet()

        /**
         * Moves [pairs] from [before] to [after]: the edit replaced one span (found as the longest
         * common prefix and suffix), so offsets before it stay, offsets after it shift, and a pair
         * with a half inside it is dropped.
         */
        fun remap(pairs: List<Added>, before: String, after: String): List<Added> {
            if (pairs.isEmpty() || before == after) return pairs
            val prefix = before.commonPrefixWith(after).length
            val room = minOf(before.length, after.length) - prefix
            var suffix = 0
            while (suffix < room && before[before.length - 1 - suffix] == after[after.length - 1 - suffix]) suffix++
            val end = before.length - suffix
            val shift = after.length - before.length
            fun move(x: Int): Int? = when {
                x < prefix -> x
                x >= end -> x + shift
                else -> null
            }
            return pairs.mapNotNull { p -> Added(move(p.open) ?: return@mapNotNull null, move(p.close) ?: return@mapNotNull null) }
        }
    }
}
