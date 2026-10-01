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

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_' || c == '@' || c == '!'

    fun prefixAt(text: String, caret: Int): Pair<Int, String> {
        var i = caret.coerceIn(0, text.length)
        while (i > 0 && isWordChar(text[i - 1])) i--
        // '@' and '!' are only valid as the first char of a word.
        val raw = text.substring(i, caret.coerceIn(0, text.length))
        val cut = raw.lastIndexOfAny(charArrayOf('@', '!'))
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
            trimmedBefore.equals("skinparam", ignoreCase = true) ->
                PlantUmlLanguage.skinparams.map { Completion(it, it, CompletionKind.SKINPARAM, "skinparam") }
            trimmedBefore.isNotEmpty() -> symbolsAndKeywords(text, caret, afterSomething = true)
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

    val snippets: List<Completion> = listOf(
        Completion("@startuml sequence", "@startuml\nactor User\nparticipant App\n\nUser -> App: $0hello\nApp --> User: hi!\n@enduml\n", CompletionKind.SNIPPET, "Sequence diagram"),
        Completion("@startuml class", "@startuml\nclass $0Animal {\n  +name: String\n  +speak()\n}\nclass Dog\nAnimal <|-- Dog\n@enduml\n", CompletionKind.SNIPPET, "Class diagram"),
        Completion("@startuml activity", "@startuml\nstart\n:$0Do something;\nif (Is it done?) then (yes)\n  :Celebrate;\nelse (no)\n  :Try again;\nendif\nstop\n@enduml\n", CompletionKind.SNIPPET, "Activity diagram"),
        Completion("@startuml state", "@startuml\n[*] --> $0Idle\nIdle --> Working : start\nWorking --> Idle : done\nWorking --> [*]\n@enduml\n", CompletionKind.SNIPPET, "State diagram"),
        Completion("@startuml component", "@startuml\ncomponent [$0Frontend]\ncomponent [Backend]\ndatabase DB\n[Frontend] --> [Backend]\n[Backend] --> DB\n@enduml\n", CompletionKind.SNIPPET, "Component diagram"),
        Completion("@startmindmap", "@startmindmap\n* $0Idea\n** Branch one\n** Branch two\n@endmindmap\n", CompletionKind.SNIPPET, "Mind map"),
    )
}
