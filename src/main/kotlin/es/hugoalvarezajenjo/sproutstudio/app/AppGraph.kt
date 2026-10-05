package es.hugoalvarezajenjo.sproutstudio.app

import es.hugoalvarezajenjo.sproutstudio.core.concurrency.AppDispatchers
import es.hugoalvarezajenjo.sproutstudio.core.settings.InMemorySettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.PreferencesSettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.features.palette.PaletteHistory
import es.hugoalvarezajenjo.sproutstudio.features.preview.DiagramSettings
import es.hugoalvarezajenjo.sproutstudio.features.workspace.LayoutSettings
import es.hugoalvarezajenjo.sproutstudio.features.workspace.RecentProjects
import es.hugoalvarezajenjo.sproutstudio.features.workspace.AutoSaver
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceDeps
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.platform.DesktopPlatform
import es.hugoalvarezajenjo.sproutstudio.platform.Platform
import es.hugoalvarezajenjo.sproutstudio.services.files.ProjectFiles
import es.hugoalvarezajenjo.sproutstudio.services.git.GitService
import es.hugoalvarezajenjo.sproutstudio.services.persistence.DocumentStore
import es.hugoalvarezajenjo.sproutstudio.services.git.JGitService
import es.hugoalvarezajenjo.sproutstudio.services.persistence.AutoSaveSettings
import es.hugoalvarezajenjo.sproutstudio.services.render.PlantUmlRenderService
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderService
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ThemeSettings

/**
 * Builds and wires every long-lived object of the app, by hand (no DI framework).
 * Main creates one; tests create their own with [forTests].
 */
class AppGraph(
    val store: SettingsStore,
    val dispatchers: AppDispatchers = AppDispatchers.Production,
    val platform: Platform = DesktopPlatform(),
) {
    val theme = ThemeSettings(store)
    val diagram = DiagramSettings(store)
    val layout = LayoutSettings(store)
    val autoSave = AutoSaveSettings(store)
    val recents = RecentProjects(store)
    val paletteHistory = PaletteHistory(store)

    val render: RenderService = PlantUmlRenderService()
    val git: GitService = JGitService()
    val documents = DocumentStore(dispatchers.io)
    /** Moves a file to the Trash. A var so tests can watch it instead of trashing for real. */
    var trash: (java.io.File) -> Boolean = { platform.moveToTrash(it) }
    val files = ProjectFiles(trash = { trash(it) })
    val autoSaver = AutoSaver(autoSave)

    val windows = WindowManager { newWorkspace() }

    /** Everything a window needs, in one bundle (see [WorkspaceDeps]). */
    val deps: WorkspaceDeps by lazy {
        WorkspaceDeps(
            dispatchers, platform, render, git, documents, files, autoSaver,
            theme, diagram, layout, recents, paletteHistory, windows,
        )
    }

    fun newWorkspace() = WorkspaceViewModel(deps)

    companion object {
        /**
         * The app's graph. Under tests (`-Dsproutstudio.settings=memory`, set by the Gradle test
         * task) it keeps settings in memory, so no test changes the user's preferences.
         */
        val default: AppGraph by lazy {
            AppGraph(if (System.getProperty("sproutstudio.settings") == "memory") InMemorySettingsStore() else PreferencesSettingsStore())
        }

        fun forTests(dispatchers: AppDispatchers = AppDispatchers.Production, platform: Platform = DesktopPlatform()) =
            AppGraph(InMemorySettingsStore(), dispatchers, platform)
    }
}
