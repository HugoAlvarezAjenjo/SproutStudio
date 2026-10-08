package es.hugoalvarezajenjo.sproutstudio.domain.text

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
