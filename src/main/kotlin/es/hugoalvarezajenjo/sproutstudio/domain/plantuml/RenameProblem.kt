package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/** Why a new name can't replace a symbol's (see [SymbolRenaming.problem]); the view words it. */
enum class RenameProblem {
    BLANK,
    UNCHANGED,
    /** Not a name PlantUML reads as one word here (or, in quotes, holds a quote or line break). */
    INVALID,
    /** A `$name` must stay a `$name`, or it stops being a variable. */
    MISSING_DOLLAR,
    /** A plain name can't become a `$name`: PlantUML would read it as a variable. */
    UNEXPECTED_DOLLAR,
    /** A PlantUML keyword: it would stop being read as this symbol. */
    KEYWORD,
    /** Another symbol already has that name: the two would merge. */
    TAKEN,
    /** A file changed after the rename was planned. */
    STALE,
}
