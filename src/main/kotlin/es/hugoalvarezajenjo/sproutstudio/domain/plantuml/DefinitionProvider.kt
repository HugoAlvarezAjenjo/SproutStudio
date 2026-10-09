package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * What a provider gets to work with: the [index] of the text being edited, the absolute path of
 * its [file] (null while unsaved) and an [includes] reader to look into the files it includes.
 */
data class NavigationContext(val index: SymbolIndex, val file: String?, val includes: IncludeReader)

/**
 * One kind of thing "Go to Declaration" understands (symbols, `!include` paths...). [Navigation]
 * asks its providers in order; teaching ⌘B something new is adding one.
 */
interface DefinitionProvider {
    /** The span at [offset] this provider navigates from (what ⌘-hover underlines), or null. */
    fun anchorAt(ctx: NavigationContext, offset: Int): IntRange?

    /** Where the anchor at [offset] leads, or null when there is nowhere to go. */
    fun targetAt(ctx: NavigationContext, offset: Int): NavigationTarget?
}
