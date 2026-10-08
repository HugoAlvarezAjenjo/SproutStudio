package es.hugoalvarezajenjo.sproutstudio.core.settings

import java.util.concurrent.ConcurrentHashMap

/** Settings kept in memory only: for tests, which must not change the user's real preferences. */
class InMemorySettingsStore(initial: Map<String, String> = emptyMap()) : SettingsStore {
    private val values = ConcurrentHashMap(initial)
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String) { values[key] = value }
}
