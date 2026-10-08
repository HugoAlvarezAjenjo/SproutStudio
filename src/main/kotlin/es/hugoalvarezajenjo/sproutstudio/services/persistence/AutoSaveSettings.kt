package es.hugoalvarezajenjo.sproutstudio.services.persistence

import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.boolean

/** "File → Save Automatically", on by default like IntelliJ. */
class AutoSaveSettings(store: SettingsStore) {
    val enabled = store.boolean("autoSave", default = true)
}
