package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * A planned "Rename Symbol": the symbol's current [name] and every place to change it ([edits],
 * the text being edited first). [taken] are the names that would collide with it.
 */
class SymbolRenaming internal constructor(
    val name: String,
    val edits: List<RenameEdit>,
    private val taken: Set<String>,
) {
    /** Occurrences that change, over all files. */
    val usages: Int get() = edits.sumOf { it.ranges.size }

    /** Files that change, the edited one included. */
    val files: Int get() = edits.size

    /** Every occurrence is the inside of a `"quoted"` name, so the new one may hold spaces. */
    val quoted: Boolean = edits.all { e -> e.ranges.all { e.text.getOrNull(it.first - 1) == '"' && e.text.getOrNull(it.last + 1) == '"' } }

    /** Why [newName] can't be used, or null when it can. */
    fun problem(newName: String): RenameProblem? = when {
        newName.isBlank() -> RenameProblem.BLANK
        newName == name -> RenameProblem.UNCHANGED
        name.startsWith('$') && !newName.startsWith('$') -> RenameProblem.MISSING_DOLLAR
        !name.startsWith('$') && newName.startsWith('$') -> RenameProblem.UNEXPECTED_DOLLAR
        !validName(newName) -> RenameProblem.INVALID
        newName in PlantUmlLanguage.allKeywords -> RenameProblem.KEYWORD
        newName in taken -> RenameProblem.TAKEN
        else -> null
    }

    private fun validName(n: String) =
        if (quoted) n == n.trim() && '"' !in n && '\n' !in n else identifier.matches(n)

    private companion object {
        /** What [SymbolIndex] reads as one name outside quotes. */
        val identifier = Regex("""\$?[A-Za-z_]\w*(?:(?:\.|::)\w+)*""")
    }
}
