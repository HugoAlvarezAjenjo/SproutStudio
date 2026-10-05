package es.hugoalvarezajenjo.sproutstudio.app

import es.hugoalvarezajenjo.sproutstudio.core.concurrency.AppDispatchers
import es.hugoalvarezajenjo.sproutstudio.core.settings.InMemorySettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.PreferencesSettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.features.palette.PaletteHistory
import es.hugoalvarezajenjo.sproutstudio.features.preview.DiagramSettings
import es.hugoalvarezajenjo.sproutstudio.features.workspace.LayoutSettings
import es.hugoalvarezajenjo.sproutstudio.features.workspace.RecentProjects
import es.hugoalvarezajenjo.sproutstudio.services.persistence.AutoSaveSettings
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ThemeSettings

/**
 * Builds and wires every long-lived object of the app, by hand (no DI framework).
 * Main creates one; tests create their own with [forTests].
 */
class AppGraph(
    val store: SettingsStore,
    val dispatchers: AppDispatchers = AppDispatchers.Production,
) {
    val theme = ThemeSettings(store)
    val diagram = DiagramSettings(store)
    val layout = LayoutSettings(store)
    val autoSave = AutoSaveSettings(store)
    val recents = RecentProjects(store)
    val paletteHistory = PaletteHistory(store)

    companion object {
        /**
         * The app's graph. Under tests (`-Dsproutstudio.settings=memory`, set by the Gradle test
         * task) it keeps settings in memory, so no test changes the user's preferences.
         */
        val default: AppGraph by lazy {
            AppGraph(if (System.getProperty("sproutstudio.settings") == "memory") InMemorySettingsStore() else PreferencesSettingsStore())
        }

        fun forTests(dispatchers: AppDispatchers = AppDispatchers.Production) = AppGraph(InMemorySettingsStore(), dispatchers)
    }
}
