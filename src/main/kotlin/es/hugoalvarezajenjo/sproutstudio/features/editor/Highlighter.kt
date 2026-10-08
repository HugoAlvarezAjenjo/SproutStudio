package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.SyntaxColors

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PlantUmlLanguage
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SyntaxScanner
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.TokenType

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

object Highlighter {

    fun highlight(
        text: String,
        colors: SyntaxColors,
        errorLines: Set<Int> = emptySet(),
        caret: Int = -1,
    ): AnnotatedString {
        val symbols = PlantUmlLanguage.symbols(text).map { it.name }.toSet()
        val tokens = SyntaxScanner.tokenize(text, symbols)
        return buildAnnotatedString {
            append(text)
            for (t in tokens) addStyle(styleFor(t.type, colors), t.start, t.end)

            for (line in errorLines) SyntaxScanner.lineRange(text, line)?.let { (s, e) ->
                if (e > s) addStyle(
                    SpanStyle(textDecoration = TextDecoration.Underline, background = colors.error.copy(alpha = 0.10f)),
                    s, e,
                )
            }

            SyntaxScanner.matchingBracket(text, caret)?.let { (a, b) ->
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
}
