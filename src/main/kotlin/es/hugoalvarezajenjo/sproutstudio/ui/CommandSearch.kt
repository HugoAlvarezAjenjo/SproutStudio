package es.hugoalvarezajenjo.sproutstudio.ui

import java.util.prefs.Preferences

/** One entry of the command palette: an action, a file to open, a template to insert… */
data class Command(
    /** Stable id, used to remember recently run commands. */
    val id: String,
    val title: String,
    /** Where it lives ("View", "File", a folder path…), shown muted and also searchable. */
    val category: String,
    val shortcut: String? = null,
    val enabled: Boolean = true,
    /** Whether the editor gets the keyboard back after running (false when the command focuses something itself). */
    val restoresFocus: Boolean = true,
    /** Files and recent projects only show up once you type, so the empty palette lists actions. */
    val searchOnly: Boolean = false,
    val run: () -> Unit,
)

/** A command that matched the query, with the title characters to highlight. */
data class Hit(val command: Command, val score: Int, val positions: Set<Int>)

/**
 * IntelliJ / VS Code-style fuzzy matching. Every word of the query must match the title
 * (or, more weakly, the category): as a prefix, at a word start, as a substring, as an
 * acronym ("tdt" → "Toggle Dark Theme") or as a scattered subsequence, in that order of preference.
 */
object CommandSearch {

    fun search(query: String, commands: List<Command>, recentIds: List<String> = emptyList()): List<Hit> {
        val available = commands.filter { it.enabled }
        val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val recentRank = recentIds.withIndex().associate { (i, id) -> id to i }
        if (tokens.isEmpty()) {
            val browsable = available.filter { !it.searchOnly }
            val recent = browsable.filter { it.id in recentRank }.sortedBy { recentRank[it.id] }
            return (recent + (browsable - recent.toSet())).map { Hit(it, 0, emptySet()) }
        }
        val order = available.withIndex().associate { (i, c) -> c.id to i }
        return available.mapNotNull { c -> match(tokens, c)?.let { (score, pos) -> Hit(c, score, pos) } }
            .sortedWith(
                compareByDescending<Hit> { it.score }
                    .thenBy { recentRank[it.command.id] ?: Int.MAX_VALUE }
                    .thenBy { order[it.command.id] ?: Int.MAX_VALUE },
            )
    }

    /** Score + highlighted title positions, or null when some query word doesn't match. */
    fun match(tokens: List<String>, c: Command): Pair<Int, Set<Int>>? {
        val title = c.title
        val positions = HashSet<Int>()
        var score = 0
        for (t in tokens) {
            val m = matchToken(t, title)
            if (m != null) {
                score += m.first; positions += m.second
            } else {
                // "view dark" should find "Dark Theme" in View: weaker, nothing to highlight.
                val inCategory = matchToken(t, c.category) ?: return null
                score += inCategory.first / 3
            }
        }
        if (tokens.joinToString(" ") == title.lowercase()) score += 50
        return score to positions
    }

    private fun matchToken(token: String, text: String): Pair<Int, List<Int>>? {
        val lower = text.lowercase()
        val starts = text.indices.filter { isWordStart(text, it) }
        // Contiguous occurrences, best first: at the very start, at a word start, anywhere.
        if (lower.startsWith(token)) return 100 to (token.indices).toList()
        starts.firstOrNull { lower.startsWith(token, it) }?.let { s -> return 80 to (s until s + token.length).toList() }
        lower.indexOf(token).takeIf { it >= 0 }?.let { s -> return 50 to (s until s + token.length).toList() }
        // Acronym: each character opens a word ("tdt", "ep" for "Export as PNG" via E…P).
        acronym(token, lower, starts)?.let { return 60 to it }
        // Scattered subsequence, penalised by how spread out it is.
        val pos = ArrayList<Int>()
        var i = 0
        for (ch in token) {
            while (i < lower.length && lower[i] != ch) i++
            if (i == lower.length) return null
            pos += i; i++
        }
        val spread = pos.last() - pos.first() + 1 - token.length
        return (30 - spread).coerceAtLeast(5) to pos
    }

    private fun acronym(token: String, lower: String, starts: List<Int>): List<Int>? {
        if (token.length < 2) return null
        val pos = ArrayList<Int>()
        var si = 0
        for (ch in token) {
            while (si < starts.size && lower[starts[si]] != ch) si++
            if (si == starts.size) return null
            pos += starts[si]; si++
        }
        return pos
    }

    private fun isWordStart(s: String, i: Int): Boolean {
        if (!s[i].isLetterOrDigit()) return false
        if (i == 0) return true
        val prev = s[i - 1]
        return !prev.isLetterOrDigit() || (s[i].isUpperCase() && prev.isLowerCase())
    }
}

/** Recently run palette commands, most recent first, kept across launches. */
object PaletteRecents {
    private val prefs = Preferences.userRoot().node("es/hugoalvarezajenjo/sproutstudio")
    private const val KEY = "paletteRecent"
    private const val MAX = 8

    fun ids(): List<String> = prefs.get(KEY, "").split('\n').filter { it.isNotEmpty() }

    fun add(id: String) {
        prefs.put(KEY, (listOf(id) + ids()).distinct().take(MAX).joinToString("\n"))
    }

    /** Tests use this to leave the user's history untouched. */
    internal fun replace(ids: List<String>) {
        prefs.put(KEY, ids.joinToString("\n"))
    }
}

/**
 * Double-tap Shift, as in IntelliJ's Search Everywhere: two clean Shift presses (no other key
 * in between, so typing capitals never triggers it) within [windowMs].
 */
class DoubleShiftDetector(private val windowMs: Long = 400, private val clock: () -> Long = System::currentTimeMillis) {
    private var lastTap = 0L
    private var clean = false

    /** Feed every key event; returns true on the second tap. */
    fun onKey(isShift: Boolean, down: Boolean): Boolean {
        if (!isShift) { clean = false; lastTap = 0L; return false }
        if (down) { clean = true; return false }
        if (!clean) return false
        clean = false
        val now = clock()
        return if (lastTap != 0L && now - lastTap <= windowMs) {
            lastTap = 0L; true
        } else {
            lastTap = now; false
        }
    }
}
