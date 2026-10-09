package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/** The kinds of block PlantUML (and its preprocessor) can open. */
enum class BlockKind {
    DIAGRAM, BRACE, END, BOX, NOTE, REF, LEGEND, HEADER, FOOTER, TITLE, STYLE,
    IF, WHILE, REPEAT, FORK, SPLIT, SWITCH, COMMENT,
    PRE_IF, PRE_DEF, PRE_WHILE, PRE_FOREACH, PRE_SUB,
}

/** One source line with its [role]. [start] and [end] are offsets into the text (end excludes the `\n`). */
data class BlockLine(val index: Int, val start: Int, val end: Int, val raw: String, val role: LineRole)

/**
 * Reads the block structure of PlantUML text line by line. Folding and the formatter both build
 * on it, so "what opens a block" is decided in one place.
 *
 * Inside a note, legend, title, header, footer, ref or block comment only that block's closer
 * counts; every other line is [LineRole.Body]. So is a multi-line activity label (`:first line`
 * up to the line ending in `;`).
 */
object BlockSyntax {

    /** Blocks whose content is free text rather than diagram lines. */
    val textBodies = setOf(
        BlockKind.NOTE, BlockKind.REF, BlockKind.LEGEND, BlockKind.HEADER,
        BlockKind.FOOTER, BlockKind.TITLE, BlockKind.COMMENT,
    )

    private val endBlocks = setOf("alt", "opt", "loop", "par", "par2", "break", "critical", "group")
    private val actionEnds = ";|<>/]}"
    private val usecaseActor = Regex("^:[^:]+:")
    private val singleLineNote = Regex("""^[hr]?note\s+"""")
    private val headerAlone = Regex("""^((left|right|center)\s+)?header$""")
    private val footerAlone = Regex("""^((left|right|center)\s+)?footer$""")
    private val noteEnd = Regex("""^end\s*[hr]?note""")
    private val ws = Regex("\\s+")
    private val classOpener = Regex(
        """^(?:abstract\s+(?:class\s+)?|class|interface|enum|annotation|entity|protocol|struct|exception|metaclass|stereotype|dataclass|record)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** True when [opener], a [BlockKind.BRACE] opener, starts a body of class members (`class X {`, `enum E {`...). */
    fun opensMembers(opener: String): Boolean = classOpener.containsMatchIn(opener.trim())

    fun scan(text: String): List<BlockLine> {
        val starts = TextLines.starts(text)
        val out = ArrayList<BlockLine>(starts.size)
        var body: BlockKind? = null // inside a free-text block
        var action = false // inside a multi-line activity label
        for (ln in starts.indices) {
            val s = starts[ln]
            val e = TextLines.end(text, starts, ln)
            val raw = text.substring(s, e)
            val t = raw.trim()
            val lower = t.lowercase()
            val role: LineRole = when {
                (body != null || action) && lower.startsWith("@end") -> {
                    body = null; action = false; LineRole.Close(BlockKind.DIAGRAM)
                }
                body != null -> if (closesBody(body, t, lower)) LineRole.Close(body).also { body = null } else LineRole.Body
                action -> { if (endsAction(t)) action = false; LineRole.Body }
                else -> classify(t, lower).also { r ->
                    if (r is LineRole.Open && r.kind in textBodies) body = r.kind
                    if (r === LineRole.Plain && opensAction(t)) action = true
                }
            }
            out += BlockLine(ln, s, e, raw, role)
        }
        return out
    }

    private fun closesBody(kind: BlockKind, t: String, lower: String) = when (kind) {
        BlockKind.COMMENT -> t.endsWith("'/")
        BlockKind.NOTE -> noteEnd.containsMatchIn(lower)
        BlockKind.REF -> lower.startsWith("end ref") || lower == "endref"
        BlockKind.LEGEND -> lower.startsWith("endlegend") || lower.startsWith("end legend")
        BlockKind.HEADER -> lower.startsWith("endheader") || lower.startsWith("end header")
        BlockKind.FOOTER -> lower.startsWith("endfooter") || lower.startsWith("end footer")
        BlockKind.TITLE -> lower.startsWith("endtitle") || lower.startsWith("end title")
        else -> false
    }

    private fun endsAction(t: String) = t.isNotEmpty() && t.last() in actionEnds
    private fun opensAction(t: String) = t.startsWith(":") && !endsAction(t) && !usecaseActor.containsMatchIn(t)

    private fun classify(t: String, lower: String): LineRole {
        if (t.isEmpty() || t.startsWith("'")) return LineRole.Plain
        if (t.startsWith("/'")) {
            return if (t.length >= 4 && t.endsWith("'/")) LineRole.Plain else LineRole.Open(BlockKind.COMMENT)
        }
        if (t.startsWith("!")) return preprocessor(lower)
        val first = lower.takeWhile { !it.isWhitespace() && it != '(' && it != '{' && it != ':' }
        // `floating note left` is a note too.
        val noteLine = if (first == "floating") lower.removePrefix("floating").trimStart() else lower
        val word = if (first == "floating") noteLine.takeWhile { !it.isWhitespace() && it != ':' } else first
        return when {
            lower.startsWith("@start") -> LineRole.Open(BlockKind.DIAGRAM)
            lower.startsWith("@end") -> LineRole.Close(BlockKind.DIAGRAM)
            t.startsWith("}") -> LineRole.Close(BlockKind.BRACE)
            lower.startsWith("</style>") -> LineRole.Close(BlockKind.STYLE)
            lower == "end" || lower == "end group" -> LineRole.Close(BlockKind.END)
            lower.startsWith("end box") || lower == "endbox" -> LineRole.Close(BlockKind.BOX)
            lower.startsWith("endif") || lower.startsWith("end if") -> LineRole.Close(BlockKind.IF)
            lower.startsWith("endwhile") || lower.startsWith("end while") -> LineRole.Close(BlockKind.WHILE)
            lower.startsWith("repeat while") || lower.startsWith("repeatwhile") -> LineRole.Close(BlockKind.REPEAT)
            lower.startsWith("end fork") || lower.startsWith("endfork") || lower.startsWith("end merge") -> LineRole.Close(BlockKind.FORK)
            lower.startsWith("end split") || lower.startsWith("endsplit") -> LineRole.Close(BlockKind.SPLIT)
            lower.startsWith("endswitch") || lower.startsWith("end switch") -> LineRole.Close(BlockKind.SWITCH)
            // ── middles ──
            word == "else" -> LineRole.Middle(setOf(BlockKind.END, BlockKind.IF))
            word == "elseif" -> LineRole.Middle(setOf(BlockKind.IF))
            word == "case" -> LineRole.Middle(setOf(BlockKind.SWITCH))
            lower.startsWith("fork again") -> LineRole.Middle(setOf(BlockKind.FORK))
            lower.startsWith("split again") -> LineRole.Middle(setOf(BlockKind.SPLIT))
            word == "backward" -> LineRole.Middle(setOf(BlockKind.REPEAT))
            // ── openers ──
            t.endsWith("{") -> LineRole.Open(BlockKind.BRACE)
            lower.startsWith("<style>") && "</style>" !in lower -> LineRole.Open(BlockKind.STYLE)
            // A bare `break` is the activity statement; the sequence block always has a label.
            word == "break" && lower == "break" -> LineRole.Plain
            word in endBlocks -> LineRole.Open(BlockKind.END)
            word == "box" -> LineRole.Open(BlockKind.BOX)
            (word == "note" || word == "hnote" || word == "rnote") && ':' !in t && !singleLineNote.containsMatchIn(noteLine) ->
                LineRole.Open(BlockKind.NOTE)
            word == "ref" && ':' !in t -> LineRole.Open(BlockKind.REF)
            word == "legend" && ':' !in t && '"' !in t && lower.split(ws).size <= 3 -> LineRole.Open(BlockKind.LEGEND)
            headerAlone.matches(lower) -> LineRole.Open(BlockKind.HEADER)
            footerAlone.matches(lower) -> LineRole.Open(BlockKind.FOOTER)
            lower == "title" -> LineRole.Open(BlockKind.TITLE)
            word == "if" -> LineRole.Open(BlockKind.IF)
            word == "while" -> LineRole.Open(BlockKind.WHILE)
            word == "repeat" -> LineRole.Open(BlockKind.REPEAT)
            word == "fork" -> LineRole.Open(BlockKind.FORK)
            word == "split" -> LineRole.Open(BlockKind.SPLIT)
            word == "switch" -> LineRole.Open(BlockKind.SWITCH)
            else -> LineRole.Plain
        }
    }

    private fun preprocessor(lower: String): LineRole {
        val d = lower.removePrefix("!").trimStart().removePrefix("unquoted").trimStart()
        val word = d.takeWhile { it.isLetter() }
        return when (word) {
            "if", "ifdef", "ifndef" -> LineRole.Open(BlockKind.PRE_IF)
            "else", "elseif" -> LineRole.Middle(setOf(BlockKind.PRE_IF))
            "endif" -> LineRole.Close(BlockKind.PRE_IF)
            // `!function $f($a) !return $a` is a one-liner.
            "procedure", "function" -> if ("!return" in d) LineRole.Plain else LineRole.Open(BlockKind.PRE_DEF)
            "endprocedure", "endfunction" -> LineRole.Close(BlockKind.PRE_DEF)
            "while" -> LineRole.Open(BlockKind.PRE_WHILE)
            "endwhile" -> LineRole.Close(BlockKind.PRE_WHILE)
            "foreach" -> LineRole.Open(BlockKind.PRE_FOREACH)
            "endfor" -> LineRole.Close(BlockKind.PRE_FOREACH)
            "startsub" -> LineRole.Open(BlockKind.PRE_SUB)
            "endsub" -> LineRole.Close(BlockKind.PRE_SUB)
            else -> LineRole.Plain
        }
    }
}
