package es.hugoalvarezajenjo.sproutstudio.features.preview

import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.boolean

/**
 * Whether the PREVIEW recolours diagrams dark. Independent of the app theme: a dark editor with
 * a light diagram is the default. Exports and copied images always keep the original colours.
 */
class DiagramSettings(store: SettingsStore) {
    val dark = store.boolean("darkDiagram", default = false)

    fun toggle() { dark.value = !dark.value }
}
