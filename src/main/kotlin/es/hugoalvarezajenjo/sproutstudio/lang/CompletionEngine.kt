package es.hugoalvarezajenjo.sproutstudio.lang

enum class CompletionKind(val badge: String) { KEYWORD("kw"), SYMBOL("sym"), SNIPPET("✨"), SKINPARAM("sp"), DIRECTIVE("@") }

/**
 * One suggestion.
 * [insert] replaces the typed prefix. A `$0` marker in [insert] says where the caret lands.
 */
data class Completion(val label: String, val insert: String, val kind: CompletionKind, val detail: String = "")

data class CompletionRequest(val prefixStart: Int, val prefix: String, val items: List<Completion>)

/** Result of applying a completion: new text and caret position. */
data class Applied(val text: String, val caret: Int)

object CompletionEngine {

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '@' || c == '!' || c == '#'

    fun prefixAt(text: String, caret: Int): Pair<Int, String> {
        var i = caret.coerceIn(0, text.length)
        while (i > 0 && isWordChar(text[i - 1])) i--
        // '@', '!' and '#' are only valid as the first char of a word.
        val raw = text.substring(i, caret.coerceIn(0, text.length))
        val cut = raw.lastIndexOfAny(charArrayOf('@', '!', '#'))
        return if (cut > 0) (i + cut) to raw.substring(cut) else i to raw
    }

    /**
     * Suggestions at [caret]. When [force] is false we only answer once the user has typed
     * something (or a trigger char), so the popup never nags.
     */
    fun complete(text: String, caret: Int, force: Boolean = false): CompletionRequest? {
        val (start, prefix) = prefixAt(text, caret)
        if (!force && prefix.isEmpty()) return null

        val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (start == 0) 0 else it + 1 }
        val beforeOnLine = text.substring(lineStart.coerceAtMost(start), start)
        val trimmedBefore = beforeOnLine.trim()

        val candidates: List<Completion> = when {
            prefix.startsWith("@") -> directives(text)
            prefix.startsWith("!") -> PlantUmlLanguage.preprocessor.map { Completion(it, it, CompletionKind.KEYWORD, "preprocessor") }
            // `!theme <name>` -> the bundled theme names.
            trimmedBefore.equals("!theme", ignoreCase = true) ->
                PlantUmlLanguage.themes.map { Completion(it, it, CompletionKind.DIRECTIVE, "theme") }
            // A colour: after a bare '#', or after a colour-valued skinparam.
            prefix.startsWith("#") || isColorContext(trimmedBefore) ->
                PlantUmlLanguage.colors.map { Completion(if (prefix.startsWith("#")) "#$it" else it, if (prefix.startsWith("#")) "#$it" else it, CompletionKind.SKINPARAM, "color") }
            trimmedBefore.equals("skinparam", ignoreCase = true) ->
                PlantUmlLanguage.skinparams.map { Completion(it, it, CompletionKind.SKINPARAM, "skinparam") }
            trimmedBefore.isNotEmpty() -> symbolsAndKeywords(text, caret, afterSomething = true) + arrowsAfterSymbol(text, caret, beforeOnLine)
            else -> symbolsAndKeywords(text, caret, afterSomething = false) + snippetsIfEmpty(text)
        }

        val p = prefix.lowercase()
        val ranked = candidates
            .filter { it.label.lowercase() != p }
            .filter { p.isEmpty() || it.label.lowercase().startsWith(p) || (p.length >= 2 && it.label.lowercase().contains(p)) }
            .sortedWith(compareBy<Completion>({ !it.label.lowercase().startsWith(p) }, { it.kind != CompletionKind.SYMBOL }, { it.label.length }))
            .distinctBy { it.label }
            .take(40)
        return if (ranked.isEmpty()) null else CompletionRequest(start, prefix, ranked)
    }

    /** True right after a skinparam whose value is a colour (so we offer colour names). */
    private fun isColorContext(before: String): Boolean {
        val last = before.substringAfterLast(' ').substringAfterLast('\t')
        return last.endsWith("Color", ignoreCase = true) || last.equals("backgroundColor", ignoreCase = true)
    }

    /** After a declared symbol with nothing but spaces since, suggest arrow styles. */
    private fun arrowsAfterSymbol(text: String, caret: Int, beforeOnLine: String): List<Completion> {
        val b = beforeOnLine.trimEnd()
        if (b.isEmpty() || !b.last().isLetterOrDigit()) return emptyList()
        val lastWord = b.takeLastWhile { it.isLetterOrDigit() || it == '_' || it == '.' }
        val known = PlantUmlLanguage.symbols(text).any { it.name == lastWord }
        return if (known) PlantUmlLanguage.arrows.map { Completion(it, "$it ", CompletionKind.SYMBOL, "arrow") } else emptyList()
    }

    fun apply(text: String, request: CompletionRequest, item: Completion, caret: Int): Applied {
        val marker = item.insert.indexOf("$0")
        val insert = item.insert.replace("$0", "")
        val newText = text.substring(0, request.prefixStart) + insert + text.substring(caret.coerceIn(request.prefixStart, text.length))
        val newCaret = request.prefixStart + if (marker >= 0) marker else insert.length
        return Applied(newText, newCaret)
    }

    private fun symbolsAndKeywords(text: String, caret: Int, afterSomething: Boolean): List<Completion> {
        val kind = PlantUmlLanguage.kindAt(text, caret)
        val symbols = PlantUmlLanguage.symbols(text).map {
            Completion(it.name, it.name, CompletionKind.SYMBOL, it.kind)
        }
        // After an arrow / word on the same line, symbols are what you most likely want.
        if (afterSomething) return symbols + PlantUmlLanguage.keywordsFor(kind).map { Completion(it, it, CompletionKind.KEYWORD, kind.label) }
        return PlantUmlLanguage.keywordsFor(kind).map { Completion(it, it, CompletionKind.KEYWORD, kind.label) } + symbols
    }

    private fun directives(text: String): List<Completion> {
        val open = Regex("""^\s*@start(\w+)""", RegexOption.MULTILINE).findAll(text).count()
        val close = Regex("""^\s*@end(\w+)""", RegexOption.MULTILINE).findAll(text).count()
        val ends = if (open > close) {
            val last = Regex("""^\s*@start(\w+)""", RegexOption.MULTILINE).findAll(text).last().groupValues[1]
            listOf(Completion("@end$last", "@end$last", CompletionKind.DIRECTIVE, "close diagram"))
        } else emptyList()
        return ends + snippets + PlantUmlLanguage.diagramStarts.map { Completion(it, it, CompletionKind.DIRECTIVE) }
    }

    private fun snippetsIfEmpty(text: String) = if (!text.contains("@start")) snippets else emptyList()

    /** Whole-diagram templates, as completion snippets offered on an empty file. */
    val snippets: List<Completion> = Templates.wholeDiagrams.map {
        Completion("@start… ${it.name.lowercase()}", it.body, CompletionKind.SNIPPET, "${it.name} diagram")
    }
}
