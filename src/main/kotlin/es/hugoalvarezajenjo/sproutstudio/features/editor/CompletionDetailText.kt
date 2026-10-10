package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.runtime.Composable
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.CompletionDetail
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.CompletionDetail.Tag
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_arrow
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_close_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_color
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_file
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_folder
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_library
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_macro
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_preprocessor
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_skinparam
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_snippet
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_theme
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_used
import es.hugoalvarezajenjo.sproutstudio.resources.cdetail_variable
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr
import org.jetbrains.compose.resources.StringResource

/** The muted words beside a completion, translated through strings.xml. */
@Composable
internal fun CompletionDetail.describe(): String = when (this) {
    is Tag -> tr(resource(this))
    is CompletionDetail.Keyword -> diagram.describe()
    is CompletionDetail.Snippet -> tr(Res.string.cdetail_snippet, name)
    is CompletionDetail.Verbatim -> text
}

private fun resource(tag: Tag): StringResource = when (tag) {
    Tag.PREPROCESSOR -> Res.string.cdetail_preprocessor
    Tag.THEME -> Res.string.cdetail_theme
    Tag.COLOR -> Res.string.cdetail_color
    Tag.SKINPARAM -> Res.string.cdetail_skinparam
    Tag.LIBRARY -> Res.string.cdetail_library
    Tag.FOLDER -> Res.string.cdetail_folder
    Tag.FILE -> Res.string.cdetail_file
    Tag.ARROW -> Res.string.cdetail_arrow
    Tag.CLOSE_DIAGRAM -> Res.string.cdetail_close_diagram
    Tag.MACRO -> Res.string.cdetail_macro
    Tag.VARIABLE -> Res.string.cdetail_variable
    Tag.USED -> Res.string.cdetail_used
}

