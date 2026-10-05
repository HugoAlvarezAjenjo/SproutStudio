package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import es.hugoalvarezajenjo.sproutstudio.domain.text.FindQuery
import es.hugoalvarezajenjo.sproutstudio.domain.text.FindReplace
import es.hugoalvarezajenjo.sproutstudio.features.document.Document

// ── Editor-level actions on a TextFieldValue (selection = the current match) ──────────────────

/** Index of the match the selection sits exactly on, or -1. */
fun currentMatchIndex(value: TextFieldValue, matches: List<IntRange>): Int {
    val s = value.selection
    if (s.collapsed) return -1
    return matches.indexOfFirst { it.first == s.min && it.last + 1 == s.max }
}

/** Select the next (or previous) match, wrapping around; null when there is nothing to find. */
fun navigate(value: TextFieldValue, q: FindQuery, forward: Boolean): TextFieldValue? {
    val matches = FindReplace.find(value.text, q).matches
    val cur = currentMatchIndex(value, matches)
    val i = when {
        forward && cur >= 0 -> (cur + 1) % matches.size
        forward -> FindReplace.nextIndex(matches, value.selection.min)
        cur >= 0 -> (cur - 1 + matches.size) % matches.size
        else -> FindReplace.prevIndex(matches, value.selection.min)
    }
    if (i < 0) return null
    return value.copy(selection = matches[i].toSelection())
}

/** While typing in the find field: stay on the match under the selection, else the next one. */
fun findIncremental(value: TextFieldValue, q: FindQuery): TextFieldValue? {
    val matches = FindReplace.find(value.text, q).matches
    val i = FindReplace.nextIndex(matches, value.selection.min)
    if (i < 0) return null
    return value.copy(selection = matches[i].toSelection())
}

/** IntelliJ "Replace": replace the selected match, then select the next one. */
fun replaceCurrent(value: TextFieldValue, q: FindQuery, replacement: String): TextFieldValue? {
    val matches = FindReplace.find(value.text, q).matches
    val cur = currentMatchIndex(value, matches)
    if (cur < 0) return navigate(value, q, forward = true) // first press just finds
    val r = FindReplace.replaceOne(value.text, matches[cur], q, replacement)
    val after = TextFieldValue(r.text, TextRange(r.caret))
    return findIncremental(after, q) ?: after
}

fun replaceAllIn(value: TextFieldValue, q: FindQuery, replacement: String): Pair<TextFieldValue, Int> {
    val r = FindReplace.replaceAll(value.text, q, replacement)
    if (r.count == 0) return value to 0
    return TextFieldValue(r.text, TextRange(value.selection.min.coerceAtMost(r.text.length))) to r.count
}

private fun IntRange.toSelection() = TextRange(first, last + 1)

// ── Document helpers used by the menu and the bar ───────────────────────────────────────────

fun Document.findNext(forward: Boolean = true) {
    if (find.query.text.isEmpty()) { find.open(replace = false, seed = selectedText()); return }
    navigate(value, find.query, forward)?.let { value = it; find.requestReveal() }
}

fun Document.replaceNext() {
    replaceCurrent(value, find.query, find.replacement)?.let { value = it; find.requestReveal() }
}

fun Document.replaceAll() {
    val (v, n) = replaceAllIn(value, find.query, find.replacement)
    value = v
    find.reportReplaced(n)
}

fun Document.selectedText(): String = value.selection.let { text.substring(it.min, it.max) }
