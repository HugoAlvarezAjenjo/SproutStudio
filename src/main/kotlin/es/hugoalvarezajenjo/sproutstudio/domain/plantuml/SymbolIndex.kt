package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/** Whether an occurrence declares its symbol or refers to it. */
enum class OccurrenceRole { DECLARATION, REFERENCE }

/**
 * One place a symbol's name appears. [range] covers the name alone (no quotes); [line] is 1-based.
 * [scope] is the diagram block it belongs to (see [SymbolIndex]), or [SymbolIndex.GLOBAL] for
 * preprocessor names, which every block sees.
 */
data class Occurrence(val name: String, val range: IntRange, val role: OccurrenceRole, val line: Int, val scope: Int) {
    /** Same symbol: same name in the same scope. */
    fun sameSymbol(other: Occurrence) = name == other.name && scope == other.scope
}

/** An `!include` line. [path] is as written, without quotes, `<>` or a `!part` suffix; [range] covers it. */
data class IncludeRef(val path: String, val range: IntRange, val line: Int, val stdlib: Boolean) {
    val isUrl: Boolean get() = path.startsWith("http://") || path.startsWith("https://")

    /** A file of the user's own (not the stdlib, not a URL) that can be opened and followed. */
    val isLocal: Boolean get() = !stdlib && !isUrl && path.isNotEmpty()
}

/**
 * What a PlantUML text declares and where each name is used: the one parser behind completion,
 * symbol highlighting and navigation.
 *
 * Names are scoped by diagram block: each `@start…` line opens a new scope (lines before the first
 * one are scope 0), so `A` in two diagrams of one file are two symbols. Procedures, functions,
 * macros and `$variables` are [GLOBAL]. A name used but never declared (PlantUML creates a
 * participant on first use) is declared, implicitly, by its first occurrence.
 */
class SymbolIndex private constructor(
    val text: String,
    /** Every occurrence, in text order. */
    val occurrences: List<Occurrence>,
    /** One per symbol (name + scope), at its declaration, in text order. */
    val definitions: List<Symbol>,
    val includes: List<IncludeRef>,
    /**
     * Names this text uses but declares nowhere, as [GLOBAL] references: where an included
     * file's procedures, functions, macros and `$variables` are used. Before a line's label any
     * word counts; inside it only `$names` and calls (`name(`).
     */
    val unknown: List<Occurrence>,
) {
    /** One per name, for completion and highlighting. */
    val symbols: List<Symbol> = definitions.distinctBy { it.name }

    /** The occurrence under [offset]; a caret right after a name counts as on it. */
    fun occurrenceAt(offset: Int): Occurrence? =
        occurrences.firstOrNull { offset in it.range } ?: occurrences.firstOrNull { offset == it.range.last + 1 }

    /** Where [o]'s symbol is declared: the explicit declaration, else its first occurrence. */
    fun declarationOf(o: Occurrence): Occurrence =
        occurrences.firstOrNull { it.sameSymbol(o) && it.role == OccurrenceRole.DECLARATION }
            ?: occurrences.first { it.sameSymbol(o) }

    /** Every occurrence of [o]'s symbol, [o] included, in text order. */
    fun usagesOf(o: Occurrence): List<Occurrence> = occurrences.filter { it.sameSymbol(o) }

    /** The `!include` whose path is under [offset] (or just after it). */
    fun includeAt(offset: Int): IncludeRef? = includes.firstOrNull { offset in it.range.first..it.range.last + 1 }

    /** The [unknown] name under [offset] (or just after it). */
    fun unknownAt(offset: Int): Occurrence? =
        unknown.firstOrNull { offset in it.range } ?: unknown.firstOrNull { offset == it.range.last + 1 }

    /** Where this text declares the preprocessor name [name], if it does. */
    fun globalDeclaration(name: String): Occurrence? =
        occurrences.firstOrNull { it.name == name && it.scope == GLOBAL && it.role == OccurrenceRole.DECLARATION }

    companion object {
        const val GLOBAL = -1

        /** [Symbol.kind] values that aren't a PlantUML keyword; every other kind is the keyword that declared it. */
        const val MACRO = "macro"
        const val VARIABLE = "variable"
        const val USED = "used"

        // Highlighting, completion and navigation all ask for the same text on a keystroke.
        @Volatile private var last: SymbolIndex? = null

        /** The index of the text being edited, reused while it doesn't change. */
        fun of(text: String): SymbolIndex {
            last?.let { if (it.text === text || it.text == text) return it }
            return parse(text).also { last = it }
        }

        /** A fresh index, leaving [of]'s cache alone (for other files). */
        fun parse(text: String): SymbolIndex = Parser(text).build()
    }

    private class Parser(private val text: String) {
        private val code = BooleanArray(text.length) { true } // false inside comments
        private val lineStarts = ArrayList<Int>().apply {
            add(0)
            text.forEachIndexed { i, ch -> if (ch == '\n') add(i + 1) }
        }

        private data class Line(val no: Int, val start: Int, val text: String, val scope: Int, val inNoteBody: Boolean)
        private data class Decl(val range: IntRange, val kind: String)

        /** (name, scope) -> its explicit declaration. */
        private val decls = LinkedHashMap<Pair<String, Int>, Decl>()
        private val names = HashMap<Int, MutableSet<String>>()
        private val globals = HashSet<String>()
        private val includes = ArrayList<IncludeRef>()

        fun build(): SymbolIndex {
            markComments()
            val lines = lines()
            for (l in lines) if (!l.inNoteBody) collectDeclarations(l)
            val occurrences = ArrayList<Occurrence>()
            val unknown = ArrayList<Occurrence>()
            for (l in lines) if (!l.inNoteBody) collectOccurrences(l, occurrences, unknown)
            occurrences.sortBy { it.range.first }

            val definitions = occurrences
                .groupBy { it.name to it.scope }
                .map { (key, occ) ->
                    val d = occ.firstOrNull { it.role == OccurrenceRole.DECLARATION } ?: occ.first()
                    Symbol(d.name, decls[key]?.kind ?: USED, d.line, d.range, d.scope)
                }
                .sortedBy { it.range.first }
            return SymbolIndex(text, occurrences, definitions, includes, unknown)
        }

        private fun markComments() {
            Regex("""/'[\s\S]*?'/""").findAll(text).forEach { m -> for (i in m.range) code[i] = false }
            Regex("""(?m)^[ \t]*'.*$""").findAll(text).forEach { m -> for (i in m.range) code[i] = false }
        }

        private fun lines(): List<Line> {
            val out = ArrayList<Line>()
            var scope = 0
            var blocks = 0
            var inNote = false
            var inLegend = false
            for ((i, start) in lineStarts.withIndex()) {
                val end = if (i + 1 < lineStarts.size) lineStarts[i + 1] - 1 else text.length
                val s = text.substring(start, end)
                val t = s.trim()
                if (startDirective.containsMatchIn(t)) scope = ++blocks
                // Multi-line note / legend bodies are prose: the opener line still names symbols.
                val body = inNote || inLegend
                when {
                    inNote && noteEnd.containsMatchIn(t) -> inNote = false
                    inLegend && legendEnd.containsMatchIn(t) -> inLegend = false
                    !body && noteOpen.containsMatchIn(t) -> inNote = true
                    !body && legendOpen.containsMatchIn(t) -> inLegend = true
                }
                out += Line(i + 1, start, s, scope, body)
            }
            return out
        }

        private fun isComment(l: Line): Boolean {
            val first = l.text.indexOfFirst { !it.isWhitespace() }
            return first < 0 || !code[l.start + first]
        }

        private fun declare(name: String, range: IntRange, scope: Int, kind: String) {
            if (name.isEmpty()) return
            if (scope == GLOBAL) globals += name else names.getOrPut(scope) { HashSet() } += name
            decls.putIfAbsent(name to scope, Decl(range, kind))
        }

        private fun collectDeclarations(l: Line) {
            if (isComment(l)) return
            val s = l.text
            if (s.trimStart().startsWith("!")) {
                preprocessorDecl.find(s)?.let { m ->
                    val g = m.groups[2]!!
                    declare(g.value, abs(l, g.range), GLOBAL, m.groupValues[1].lowercase())
                }
                defineDecl.find(s)?.let { m -> m.groups[1]!!.let { declare(it.value, abs(l, it.range), GLOBAL, MACRO) } }
                variableDecl.find(s)?.let { m -> m.groups[1]!!.let { declare(it.value, abs(l, it.range), GLOBAL, VARIABLE) } }
                include.find(s)?.let { m -> includes += includeRef(l, m) }
                return
            }
            declaration.find(s)?.let { m ->
                val kind = m.groupValues[1].lowercase().replace(Regex("\\s+"), " ")
                val g = m.groups[4] ?: m.groups[3] ?: m.groups[2] ?: return@let
                declare(g.value, abs(l, g.range), l.scope, kind)
                return
            }
            arrowEndpoints.find(s)?.let { m ->
                for (n in listOf(m.groupValues[1], m.groupValues[2])) {
                    if (n.isNotEmpty() && n !in PlantUmlLanguage.allKeywords && n !in globals) {
                        names.getOrPut(l.scope) { HashSet() } += n
                    }
                }
            }
        }

        private fun includeRef(l: Line, m: MatchResult): IncludeRef {
            val g = m.groups[1]!!
            var raw = g.value
            var from = g.range.first
            val stdlib = raw.startsWith("<")
            if (stdlib || raw.startsWith("\"")) { raw = raw.drop(1); from++ }
            raw = raw.trimEnd('>', '"')
            if (!stdlib) raw = raw.substringBefore('!') // `file.puml!PART`, `file.puml!2`
            return IncludeRef(raw, (l.start + from) until (l.start + from + raw.length), l.no, stdlib)
        }

        private fun collectOccurrences(l: Line, out: MutableList<Occurrence>, unknown: MutableList<Occurrence>) {
            if (isComment(l)) return
            val s = l.text
            // The name a declaration line introduces, and an include's path, are never "used here".
            val declares = include.containsMatchIn(s) || preprocessorDecl.containsMatchIn(s) || defineDecl.containsMatchIn(s)
            val scopeNames = names[l.scope].orEmpty()
            // `A -> B : text`, `A : field`, `:action;` — the label is prose.
            val strings = Regex(""""[^"\n]*"""").findAll(s).map { it.range }.toList()
            val label = labelStart(s, strings)

            fun emit(name: String, range: IntRange, scope: Int) {
                val decl = decls[name to scope]
                val role = if (decl?.range == range) OccurrenceRole.DECLARATION else OccurrenceRole.REFERENCE
                // A declared name that is also a keyword only counts where it is declared.
                if (role == OccurrenceRole.REFERENCE && name in PlantUmlLanguage.allKeywords) return
                out += Occurrence(name, range, role, l.no, scope)
            }

            for (r in strings) {
                if (r.first >= label || !code[l.start + r.first]) continue
                val content = s.substring(r.first + 1, r.last)
                if (content in scopeNames) emit(content, abs(l, (r.first + 1) until r.last), l.scope)
            }
            for (m in word.findAll(s)) {
                val a = m.range.first
                if (!code[l.start + a] || strings.any { a in it }) continue
                val n = m.value
                when {
                    n in globals -> emit(n, abs(l, m.range), GLOBAL)
                    a < label && n in scopeNames -> emit(n, abs(l, m.range), l.scope)
                    declares || n[0].isDigit() || n in PlantUmlLanguage.allKeywords -> {}
                    a < label || n.startsWith("$") || s.getOrNull(m.range.last + 1) == '(' ->
                        unknown += Occurrence(n, abs(l, m.range), OccurrenceRole.REFERENCE, l.no, GLOBAL)
                }
            }
        }

        /** Index where [s]'s label starts (its first single ':' outside [strings]), or its length. */
        private fun labelStart(s: String, strings: List<IntRange>): Int {
            for (i in s.indices) {
                if (s[i] != ':' || strings.any { i in it }) continue
                if (s.getOrNull(i + 1) == ':' || s.getOrNull(i - 1) == ':') continue
                return i
            }
            return s.length
        }

        private fun abs(l: Line, r: IntRange) = (l.start + r.first)..(l.start + r.last)
    }
}

private val startDirective = Regex("""^@start\w*""")
private val noteOpen = Regex("""^[rh]?note\b[^:]*$""", RegexOption.IGNORE_CASE)
private val noteEnd = Regex("""^end\s?[rh]?note\b""", RegexOption.IGNORE_CASE)
private val legendOpen = Regex("""^legend\b""", RegexOption.IGNORE_CASE)
private val legendEnd = Regex("""^end\s?legend\b""", RegexOption.IGNORE_CASE)

private val word = Regex("""[\w$]+(?:(?:\.|::)[\w$]+)*""")
private val preprocessorDecl = Regex("""^\s*!(?:unquoted\s+)?(procedure|function)\s+(\$?\w+)""", RegexOption.IGNORE_CASE)
private val defineDecl = Regex("""^\s*!define(?:long)?\s+(\w+)""", RegexOption.IGNORE_CASE)
private val variableDecl = Regex("""^\s*!(?:global\s+|local\s+)?(\$\w+)\s*\??=""", RegexOption.IGNORE_CASE)
private val include = Regex("""^\s*!include(?:_many|_once|url|sub)?\s+(\S(?:.*\S)?)\s*$""", RegexOption.IGNORE_CASE)

private val declaration = Regex(
    """^\s*(participant|actor|boundary|control|entity|database|collections|queue|""" +
        """abstract\s+class|class|interface|enum|annotation|struct|component|node|cloud|folder|frame|""" +
        """artifact|storage|usecase|state|object|rectangle|package|namespace)\s+""" +
        """(?:"([^"]+)"|([\w.:$]+))(?:\s+as\s+([\w.:$]+))?""",
    RegexOption.IGNORE_CASE,
)
private val arrowEndpoints = Regex("""^\s*([A-Za-z_][\w.]*)\s*[-.=<>ox*|\\/+#\[\]]*?(?:->|-->|<-|<--|->>|\.\.>|--|\.\.)[^\w]*\s*([A-Za-z_][\w.]*)""")
