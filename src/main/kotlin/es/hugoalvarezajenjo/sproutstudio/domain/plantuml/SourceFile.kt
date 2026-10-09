package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/** Another PlantUML file, as an [IncludeReader] found it. [path] is absolute and identifies it. */
class SourceFile(val path: String, val text: String) {
    /** Parsed on first use; doesn't touch [SymbolIndex.of]'s cache of the text being edited. */
    val index: SymbolIndex by lazy { SymbolIndex.parse(text) }
}
