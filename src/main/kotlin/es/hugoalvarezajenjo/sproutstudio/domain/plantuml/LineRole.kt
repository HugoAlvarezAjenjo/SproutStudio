package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/** What a line does to the block structure, as [BlockSyntax] reads it. */
sealed interface LineRole {
    /** Starts a block: `alt ok`, `class A {`, `note over A`, `!procedure $f()`... */
    data class Open(val kind: BlockKind) : LineRole

    /** Ends the innermost open block of [kind]: `end`, `}`, `end note`, `!endprocedure`... */
    data class Close(val kind: BlockKind) : LineRole

    /** Splits a block of one of [of] in two: `else`, `case (x)`, `fork again`, `!else`... */
    data class Middle(val of: Set<BlockKind>) : LineRole

    /** Free text inside a note, legend, block comment or multi-line activity label. */
    object Body : LineRole

    /** Anything else, blank lines included. */
    object Plain : LineRole
}
