package es.hugoalvarezajenjo.sproutstudio.lang

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** Colors for each token category. */
data class SyntaxColors(
    val keyword: Color,
    val directive: Color,
    val preprocessor: Color,
    val string: Color,
    val comment: Color,
    val arrow: Color,
    val color: Color,
    val stereotype: Color,
    val symbol: Color,
    val error: Color,
    val bracketMatch: Color,
)

enum class TokenType { KEYWORD, DIRECTIVE, PREPROCESSOR, STRING, COMMENT, ARROW, COLOR, STEREOTYPE, SYMBOL }

data class Token(val start: Int, val end: Int, val type: TokenType)

object Highlighter {

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
                    TokenType.KEYWORD -> when {
                        m.value in PlantUmlLanguage.allKeywords -> claim(start, end, TokenType.KEYWORD)
                        m.value in symbols -> claim(start, end, TokenType.SYMBOL)
                    }
                    else -> claim(start, end, type)
                }
            }
        }
        return out.sortedBy { it.start }
    }

    fun highlight(
        text: String,
        colors: SyntaxColors,
        errorLines: Set<Int> = emptySet(),
        caret: Int = -1,
    ): AnnotatedString {
        val symbols = PlantUmlLanguage.symbols(text).map { it.name }.toSet()
        val tokens = tokenize(text, symbols)
        return buildAnnotatedString {
            append(text)
            for (t in tokens) addStyle(styleFor(t.type, colors), t.start, t.end)

            for (line in errorLines) lineRange(text, line)?.let { (s, e) ->
                if (e > s) addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline, background = colors.error.copy(alpha = 0.10f)),
                    s, e,
                )
            }

            matchingBracket(text, caret)?.let { (a, b) ->
                val st = SpanStyle(background = colors.bracketMatch, fontWeight = FontWeight.Bold)
                addStyle(st, a, a + 1); addStyle(st, b, b + 1)
            }
        }
    }

    private fun styleFor(type: TokenType, c: SyntaxColors): SpanStyle = when (type) {
        TokenType.KEYWORD -> SpanStyle(color = c.keyword, fontWeight = FontWeight.SemiBold)
        TokenType.DIRECTIVE -> SpanStyle(color = c.directive, fontWeight = FontWeight.Bold)
        TokenType.PREPROCESSOR -> SpanStyle(color = c.preprocessor, fontWeight = FontWeight.SemiBold)
        TokenType.STRING -> SpanStyle(color = c.string)
        TokenType.COMMENT -> SpanStyle(color = c.comment, fontStyle = FontStyle.Italic)
        TokenType.ARROW -> SpanStyle(color = c.arrow, fontWeight = FontWeight.Bold)
        TokenType.COLOR -> SpanStyle(color = c.color)
        TokenType.STEREOTYPE -> SpanStyle(color = c.stereotype, fontStyle = FontStyle.Italic)
        TokenType.SYMBOL -> SpanStyle(color = c.symbol)
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
