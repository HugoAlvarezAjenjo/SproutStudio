package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

enum class TokenType { KEYWORD, DIRECTIVE, PREPROCESSOR, STRING, COMMENT, ARROW, COLOR, STEREOTYPE, SYMBOL }

data class Token(val start: Int, val end: Int, val type: TokenType)

/** Splits PlantUML source into coloured tokens and finds bracket pairs; the editor styles them. */
object SyntaxScanner {

    private val patterns: List<Pair<TokenType, Regex>> = listOf(
        TokenType.STRING to Regex(""""[^"\n]*""""),
        TokenType.DIRECTIVE to Regex("""(?m)^\s*@(start|end)\w+"""),
        TokenType.PREPROCESSOR to Regex("""(?m)^\s*!\w+"""),
        TokenType.STEREOTYPE to Regex("""<<[^>\n]+>>"""),
        TokenType.COLOR to Regex("""#[0-9A-Fa-f]{6}\b|#[0-9A-Fa-f]{3}\b|#[A-Za-z]+\b"""),
        TokenType.ARROW to Regex("""<\|--|--\|>|<\|\.\.|\.\.\|>|[*o]--|--[*o]|<<?-+>?>?|-+>>?|<-+|\.+>|<\.+|-\[[^\]\n]*]->|--+|\.\.+|->x|x<-"""),
        TokenType.KEYWORD to Regex("""\b[A-Za-z]+\b"""),
    )

    /** Tokenize, with comments taking precedence over everything on their span. */
    fun tokenize(text: String, symbols: Set<String> = emptySet()): List<Token> {
        val taken = BooleanArray(text.length)
        val out = ArrayList<Token>()
        fun claim(start: Int, end: Int, type: TokenType) {
            if (start >= end) return
            for (i in start until end) if (taken[i]) return
            for (i in start until end) taken[i] = true
            out += Token(start, end, type)
        }

        // Block comments /' ... '/ and line comments starting with '
        Regex("""/'[\s\S]*?'/""").findAll(text).forEach { claim(it.range.first, it.range.last + 1, TokenType.COMMENT) }
        Regex("""(?m)^\s*'.*$""").findAll(text).forEach { m ->
            val s = m.range.first + m.value.indexOf('\'')
            claim(s, m.range.last + 1, TokenType.COMMENT)
        }

        for ((type, re) in patterns) {
            re.findAll(text).forEach { m ->
                val start = m.range.first + (m.value.length - m.value.trimStart().length)
                val end = m.range.last + 1
                when (type) {
                    // PlantUML keywords are case-insensitive, but a name the user declared
                    // (`participant Database`) wins over a keyword spelled in another case.
                    TokenType.KEYWORD -> when {
                        m.value in PlantUmlLanguage.allKeywords -> claim(start, end, TokenType.KEYWORD)
                        m.value in symbols -> claim(start, end, TokenType.SYMBOL)
                        m.value.lowercase() in PlantUmlLanguage.allKeywords -> claim(start, end, TokenType.KEYWORD)
                    }
                    else -> claim(start, end, type)
                }
            }
        }
        return out.sortedBy { it.start }
    }

    /** [start, end) of 1-based [line], excluding the newline. */
    fun lineRange(text: String, line: Int): Pair<Int, Int>? {
        if (line < 1) return null
        var start = 0
        repeat(line - 1) {
            val nl = text.indexOf('\n', start)
            if (nl < 0) return null
            start = nl + 1
        }
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        return start to end
    }

    private const val OPEN = "([{"
    private const val CLOSE = ")]}"

    /** If the caret touches a bracket, return it and its partner. */
    fun matchingBracket(text: String, caret: Int): Pair<Int, Int>? {
        for (pos in listOf(caret - 1, caret)) {
            if (pos !in text.indices) continue
            val ch = text[pos]
            val o = OPEN.indexOf(ch); val c = CLOSE.indexOf(ch)
            if (o >= 0) scan(text, pos, 1, ch, CLOSE[o])?.let { return pos to it }
            if (c >= 0) scan(text, pos, -1, ch, OPEN[c])?.let { return it to pos }
        }
        return null
    }

    private fun scan(text: String, from: Int, dir: Int, self: Char, partner: Char): Int? {
        var depth = 0; var i = from
        while (i in text.indices) {
            when (text[i]) { self -> depth++; partner -> depth-- }
            if (depth == 0) return i
            i += dir
        }
        return null
    }
}
