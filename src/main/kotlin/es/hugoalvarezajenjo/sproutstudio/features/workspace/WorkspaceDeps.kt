package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.runtime.getValue

import es.hugoalvarezajenjo.sproutstudio.core.concurrency.AppDispatchers
import es.hugoalvarezajenjo.sproutstudio.features.palette.PaletteHistory
import es.hugoalvarezajenjo.sproutstudio.features.preview.DiagramSettings
import es.hugoalvarezajenjo.sproutstudio.platform.Platform
import es.hugoalvarezajenjo.sproutstudio.services.files.ProjectFiles
import es.hugoalvarezajenjo.sproutstudio.services.git.GitService
import es.hugoalvarezajenjo.sproutstudio.services.persistence.DocumentStore
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderService
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ThemeSettings

/**
 * What a window needs from the rest of the app, handed in by the app's graph. Screens reach
 * services and settings only through this (via their ViewModel), never through a global.
 */
class WorkspaceDeps(
    val dispatchers: AppDispatchers,
    val platform: Platform,
    val render: RenderService,
    val git: GitService,
    val documents: DocumentStore,
    val files: ProjectFiles,
    val autoSaver: AutoSaver,
    val theme: ThemeSettings,
    val diagram: DiagramSettings,
    val layout: LayoutSettings,
    val recents: RecentProjects,
    val paletteHistory: PaletteHistory,
    val windows: WindowNavigator,
) {
    /** Template thumbnails for the gallery, the sidebar and the New Diagram dialog. */
    val thumbnails = es.hugoalvarezajenjo.sproutstudio.features.templates.TemplateThumbnails(render, diagram, dispatchers.default)
}
