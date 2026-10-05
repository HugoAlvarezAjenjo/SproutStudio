package es.hugoalvarezajenjo.sproutstudio.features.palette

import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.stringList

/** Recently run palette commands, most recent first. */
class PaletteHistory(store: SettingsStore) {
    private val list = store.stringList("paletteRecent")

    fun ids(): List<String> = list.value

    fun add(id: String) {
        list.value = (listOf(id) + list.value).distinct().take(MAX)
    }

    fun replace(ids: List<String>) { list.value = ids }

    private companion object { const val MAX = 8 }
}
