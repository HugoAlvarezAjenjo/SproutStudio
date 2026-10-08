package es.hugoalvarezajenjo.sproutstudio.services.render

/**
 * Why a diagram can't be drawn. The service says WHAT went wrong; the UI decides the words
 * (so they can be translated). Only [message] for [Kind.PLANTUML] is shown as is: it's
 * PlantUML's own text.
 */
data class RenderError(
    /** 1-based line in the editor text, or null when it couldn't be pinned down. */
    val line: Int?,
    val kind: Kind,
    /** PlantUML's message ([Kind.PLANTUML]) or the failure's detail ([Kind.FAILED]); else null. */
    val message: String? = null,
) {
    enum class Kind {
        /** PlantUML reported a syntax error. */
        PLANTUML,
        /** A remote `!include` was refused: the app works fully offline. */
        OFFLINE_INCLUDE,
        /** There's text, but no `@startxxx … @endxxx` block yet. */
        NO_DIAGRAM,
        /** Drawing failed for another reason (an exception inside PlantUML). */
        FAILED,
    }
}
