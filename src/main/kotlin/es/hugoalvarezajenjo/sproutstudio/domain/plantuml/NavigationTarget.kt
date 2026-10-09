package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/** Where "Go to Declaration" leads. */
sealed interface NavigationTarget {
    /** A place in the same text: select [range]. */
    data class InText(val range: IntRange) : NavigationTarget

    /** Several places: let the user pick one of [occurrences] of [symbol]. */
    data class Usages(val symbol: String, val occurrences: List<Occurrence>) : NavigationTarget

    /** Another file, [path] as written in the `!include` (relative to the including file). */
    data class IncludedFile(val path: String) : NavigationTarget
}
