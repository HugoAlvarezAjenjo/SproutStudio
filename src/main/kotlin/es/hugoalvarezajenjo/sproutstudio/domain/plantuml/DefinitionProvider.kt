package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * One kind of thing "Go to Declaration" understands (symbols, `!include` paths...). [Navigation]
 * asks its providers in order; teaching ⌘B something new is adding one.
 */
interface DefinitionProvider {
    /** The span at [offset] this provider navigates from (what ⌘-hover underlines), or null. */
    fun anchorAt(index: SymbolIndex, offset: Int): IntRange?

    /** Where the anchor at [offset] leads, or null when there is nowhere to go. */
    fun targetAt(index: SymbolIndex, offset: Int): NavigationTarget?
}
