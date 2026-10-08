package es.hugoalvarezajenjo.sproutstudio.services.render

/** One entry for the Problems panel: a single error, placed in the file. */
data class Problem(
    /** 1-based line in the editor text, or null when PlantUML could not pin it down. */
    val line: Int?,
    /** PlantUML's own message (empty for [RenderError.Kind.OFFLINE_INCLUDE]). */
    val message: String,
    /** 0-based index of the diagram block this error belongs to. */
    val diagramIndex: Int,
    val kind: RenderError.Kind = RenderError.Kind.PLANTUML,
)
