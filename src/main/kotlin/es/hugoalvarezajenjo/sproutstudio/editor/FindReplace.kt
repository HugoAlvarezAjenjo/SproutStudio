package es.hugoalvarezajenjo.sproutstudio.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import es.hugoalvarezajenjo.sproutstudio.model.Document

/** What the user is looking for, with the three IntelliJ toggles. */
data class FindQuery(
    val text: String,
    val matchCase: Boolean = false,
    val wholeWords: Boolean = false,
    val regex: Boolean = false,
)

/** Result of a search: the matches, or why the pattern is invalid. */
data class FindResult(val matches: List<IntRange>, val error: String? = null) {
    companion object { val Empty = FindResult(emptyList()) }
}

data class Replaced(val text: String, val caret: Int, val count: Int)

/** Pure find & replace over a String; no UI, fully unit-tested. */
object FindReplace {
    /** Stop counting past this; a 1-char search in a huge file shouldn't freeze the editor. */
    const val MAX_MATCHES = 5000

    fun toRegex(q: FindQuery): Regex {
        var pattern = if (q.regex) q.text else Regex.escape(q.text)
        if (q.wholeWords) pattern = "(?<![\\w])(?:$pattern)(?![\\w])"
        val options = buildSet {
            add(RegexOption.MULTILINE)
            if (!q.matchCase) add(RegexOption.IGNORE_CASE) // Kotlin/JVM: already Unicode-aware
        }
        return Regex(pattern, options)
    }

    fun find(text: String, q: FindQuery): FindResult {
        if (q.text.isEmpty()) return FindResult.Empty
        val re = try { toRegex(q) } catch (e: Exception) {
            return FindResult(emptyList(), e.message?.lineSequence()?.firstOrNull() ?: "Invalid regex")
        }
        val out = ArrayList<IntRange>()
        for (m in re.findAll(text)) {
            if (m.range.isEmpty()) continue // e.g. "^" or "a*": zero-width hits aren't useful targets
            out += m.range
            if (out.size >= MAX_MATCHES) break
        }
        return FindResult(out)
    }

    /** Index of the first match at/after [offset], wrapping to the first one (or -1 if none). */
    fun nextIndex(matches: List<IntRange>, offset: Int): Int {
        if (matches.isEmpty()) return -1
        val i = matches.indexOfFirst { it.first >= offset }
        return if (i >= 0) i else 0
    }

    /** Index of the last match that starts before [offset], wrapping to the last one. */
    fun prevIndex(matches: List<IntRange>, offset: Int): Int {
        if (matches.isEmpty()) return -1
        val i = matches.indexOfLast { it.first < offset }
        return if (i >= 0) i else matches.lastIndex
    }

    /** Text that replaces [match]: literal, or with `$1` groups expanded in regex mode. */
    fun replacementFor(text: String, match: IntRange, q: FindQuery, replacement: String): String {
        if (!q.regex) return replacement
        return try {
            val m = toRegex(q).toPattern().matcher(text)
            if (!m.find(match.first) || m.start() != match.first) return replacement
            // appendReplacement copies text[0, start) then the expanded replacement: keep the tail.
            val sb = StringBuilder()
            m.appendReplacement(sb, replacement)
            sb.substring(match.first)
        } catch (_: Exception) {
            replacement // e.g. "$9" with no 9th group: insert it literally rather than fail
        }
    }

    fun replaceOne(text: String, match: IntRange, q: FindQuery, replacement: String): Replaced {
        val with = replacementFor(text, match, q, replacement)
        val newText = text.replaceRange(match, with)
        return Replaced(newText, match.first + with.length, 1)
    }

    fun replaceAll(text: String, q: FindQuery, replacement: String): Replaced {
        val matches = find(text, q).matches
        if (matches.isEmpty()) return Replaced(text, 0, 0)
        val sb = StringBuilder()
        var last = 0
        for (m in matches) {
            sb.append(text, last, m.first).append(replacementFor(text, m, q, replacement))
            last = m.last + 1
        }
        sb.append(text, last, text.length)
        return Replaced(sb.toString(), 0, matches.size)
    }
}

/** Per-document state of the find bar (kept on the Document so the menu can drive it). */
@Stable
class FindState {
    var visible by mutableStateOf(false)
    var replaceVisible by mutableStateOf(false)
    var query by mutableStateOf(FindQuery(""))
    var replacement by mutableStateOf("")
    /** Bumped to ask the find field to take focus (and select its text). */
    var focusTick by mutableStateOf(0)
    /** Bumped whenever a find action moved the selection: the editor scrolls it into view. */
    var revealTick by mutableStateOf(0)
    /** Status for the bar after Replace All ("3 replaced"); cleared on the next edit of the query. */
    var notice by mutableStateOf<String?>(null)

    fun open(replace: Boolean, seed: String?) {
        visible = true
        if (replace) replaceVisible = true
        // Seed with the editor selection, like IntelliJ, but not with multi-line blocks.
        if (!seed.isNullOrEmpty() && '\n' !in seed) query = query.copy(text = seed)
        focusTick++
    }

    fun close() {
        visible = false
        replaceVisible = false
        notice = null
    }
}

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
    navigate(value, find.query, forward)?.let { value = it; find.revealTick++ }
}

fun Document.replaceNext() {
    replaceCurrent(value, find.query, find.replacement)?.let { value = it; find.revealTick++ }
}

fun Document.replaceAll() {
    val (v, n) = replaceAllIn(value, find.query, find.replacement)
    value = v
    find.notice = if (n == 1) "1 replaced" else "$n replaced"
}

fun Document.selectedText(): String = value.selection.let { text.substring(it.min, it.max) }
