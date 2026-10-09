package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/** "Go to Declaration", "Find Usages" and the symbol under the caret, on plain text. */
object Navigation {

    /** Asked in order: the first one with an anchor at the offset decides. */
    val providers: List<DefinitionProvider> = listOf(IncludeDefinitions, SymbolDefinitions)

    /** Where ⌘B at [offset] goes; null when there is nothing to go to. */
    fun definitionAt(text: String, offset: Int): NavigationTarget? {
        val index = SymbolIndex.of(text)
        val p = providers.firstOrNull { it.anchorAt(index, offset) != null } ?: return null
        return p.targetAt(index, offset)
    }

    /** The span ⌘-click at [offset] would follow, for the hover underline; null if it leads nowhere. */
    fun linkAt(text: String, offset: Int): IntRange? {
        val index = SymbolIndex.of(text)
        val p = providers.firstOrNull { it.anchorAt(index, offset) != null } ?: return null
        return if (p.targetAt(index, offset) != null) p.anchorAt(index, offset) else null
    }

    /** Every occurrence of the symbol at [offset] (empty when it isn't on one). */
    fun occurrencesAt(text: String, offset: Int): List<Occurrence> {
        val index = SymbolIndex.of(text)
        return index.occurrenceAt(offset)?.let(index::usagesOf).orEmpty()
    }

    /** "Find Usages" at [offset]: the symbol's other occurrences, declaration included; null off a symbol. */
    fun usagesAt(text: String, offset: Int): NavigationTarget.Usages? {
        val index = SymbolIndex.of(text)
        val at = index.occurrenceAt(offset) ?: return null
        val others = index.usagesOf(at).filter { it.range != at.range }
        return if (others.isEmpty()) null else NavigationTarget.Usages(at.name, others)
    }
}

/** A name: a reference goes to its declaration; on the declaration, to its usages (as IntelliJ). */
private object SymbolDefinitions : DefinitionProvider {
    override fun anchorAt(index: SymbolIndex, offset: Int) = index.occurrenceAt(offset)?.range

    override fun targetAt(index: SymbolIndex, offset: Int): NavigationTarget? {
        val at = index.occurrenceAt(offset) ?: return null
        val decl = index.declarationOf(at)
        if (decl.range != at.range) return NavigationTarget.InText(decl.range)
        val others = index.usagesOf(at).filter { it.range != at.range }
        return when (others.size) {
            0 -> null
            1 -> NavigationTarget.InText(others.single().range)
            else -> NavigationTarget.Usages(at.name, others)
        }
    }
}

/** `!include file.puml`: the included file. Stdlib (`<C4/C4>`) and URLs have no file to open. */
private object IncludeDefinitions : DefinitionProvider {
    override fun anchorAt(index: SymbolIndex, offset: Int) =
        index.includeAt(offset)?.takeIf { !it.stdlib && !it.isUrl && it.path.isNotEmpty() }?.range

    override fun targetAt(index: SymbolIndex, offset: Int): NavigationTarget? =
        anchorAt(index, offset)?.let { NavigationTarget.IncludedFile(index.includeAt(offset)!!.path) }
}
