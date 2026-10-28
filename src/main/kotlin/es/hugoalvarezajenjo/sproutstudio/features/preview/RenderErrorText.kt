package es.hugoalvarezajenjo.sproutstudio.features.preview

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.export_pdf
import es.hugoalvarezajenjo.sproutstudio.resources.export_png
import es.hugoalvarezajenjo.sproutstudio.resources.export_svg
import es.hugoalvarezajenjo.sproutstudio.resources.preview_remote_include
import es.hugoalvarezajenjo.sproutstudio.resources.render_failed
import es.hugoalvarezajenjo.sproutstudio.resources.render_no_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.render_offline
import es.hugoalvarezajenjo.sproutstudio.resources.render_syntax_error
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text

import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import es.hugoalvarezajenjo.sproutstudio.services.render.OfflineIncludeException
import es.hugoalvarezajenjo.sproutstudio.services.render.Problem
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderError

/*
 * The words for what the render service reports. The service only says WHAT happened, so all
 * user-facing text about rendering lives here, translated through strings.xml.
 */

private val OFFLINE_TEXT: String get() =
    text(Res.string.render_offline)

fun RenderError.describe(): String = when (kind) {
    RenderError.Kind.PLANTUML -> message?.takeIf { it.isNotBlank() } ?: text(Res.string.render_syntax_error)
    RenderError.Kind.OFFLINE_INCLUDE -> OFFLINE_TEXT
    RenderError.Kind.NO_DIAGRAM -> text(Res.string.render_no_diagram)
    RenderError.Kind.FAILED -> message ?: text(Res.string.render_failed)
}

fun Problem.describe(): String = when (kind) {
    RenderError.Kind.OFFLINE_INCLUDE -> OFFLINE_TEXT
    else -> message.ifBlank { text(Res.string.render_syntax_error) }
}

fun ExportFormat.label(): String = when (this) {
    ExportFormat.SVG -> text(Res.string.export_svg)
    ExportFormat.PNG -> text(Res.string.export_png)
    ExportFormat.PDF -> text(Res.string.export_pdf)
}

/** Why a copy or export failed, in the user's words. */
fun exportFailure(t: Throwable): String = when (t) {
    is OfflineIncludeException -> text(Res.string.preview_remote_include, t.line)
    else -> t.message ?: t.javaClass.simpleName
}
