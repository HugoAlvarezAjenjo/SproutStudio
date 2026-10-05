package es.hugoalvarezajenjo.sproutstudio.features.preview

import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import es.hugoalvarezajenjo.sproutstudio.services.render.Problem
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderError

/*
 * The words for what the render service reports. The service only says WHAT happened, so all
 * user-facing text about rendering lives here, ready to move to string resources.
 */

private const val OFFLINE_TEXT =
    "SproutStudio works fully offline: remote includes are not allowed. Save the file locally and use !include with a local path."

fun RenderError.describe(): String = when (kind) {
    RenderError.Kind.PLANTUML -> message?.takeIf { it.isNotBlank() } ?: "Syntax error"
    RenderError.Kind.OFFLINE_INCLUDE -> OFFLINE_TEXT
    RenderError.Kind.NO_DIAGRAM -> "No diagram found yet. Start with @startuml and end with @enduml."
    RenderError.Kind.FAILED -> message ?: "Couldn't draw the diagram"
}

fun Problem.describe(): String = when (kind) {
    RenderError.Kind.OFFLINE_INCLUDE -> OFFLINE_TEXT
    else -> message.ifBlank { "Syntax error" }
}

fun ExportFormat.label(): String = when (this) {
    ExportFormat.SVG -> "SVG (vector)"
    ExportFormat.PNG -> "PNG (image)"
    ExportFormat.PDF -> "PDF (vector)"
}
