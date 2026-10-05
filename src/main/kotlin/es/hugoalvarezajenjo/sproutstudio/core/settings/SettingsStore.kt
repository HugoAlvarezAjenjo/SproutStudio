package es.hugoalvarezajenjo.sproutstudio.core.settings

/**
 * Where the user's settings live. Values are strings; [Setting] does the typing.
 * The app uses [PreferencesSettingsStore]; tests use [InMemorySettingsStore] so they never
 * touch the real preferences.
 */
interface SettingsStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}
