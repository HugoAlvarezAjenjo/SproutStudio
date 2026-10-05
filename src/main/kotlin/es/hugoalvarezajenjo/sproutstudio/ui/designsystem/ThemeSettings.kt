package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.boolean

/** The app theme choice. Dark by default, like the JetBrains IDEs. */
class ThemeSettings(store: SettingsStore) {
    val dark = store.boolean("darkTheme", default = true)

    fun toggle() { dark.value = !dark.value }
}
