package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.layout_editor_only
import es.hugoalvarezajenjo.sproutstudio.resources.layout_preview_only
import es.hugoalvarezajenjo.sproutstudio.resources.layout_split

/** How the active tab is shown. */
enum class EditorLayout(val label: org.jetbrains.compose.resources.StringResource) {
    EDITOR(Res.string.layout_editor_only),
    SPLIT(Res.string.layout_split),
    PREVIEW(Res.string.layout_preview_only),
}
