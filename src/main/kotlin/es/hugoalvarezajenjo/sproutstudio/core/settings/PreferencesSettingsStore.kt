package es.hugoalvarezajenjo.sproutstudio.core.settings

import java.util.prefs.Preferences

/** Settings in the user's Java preferences (on macOS, a plist in ~/Library/Preferences). */
class PreferencesSettingsStore(
    private val prefs: Preferences = Preferences.userRoot().node("es/hugoalvarezajenjo/sproutstudio"),
) : SettingsStore {
    // Preferences.putBoolean/putFloat store their value as a string, so values written by older
    // versions of the app read back fine through get().
    override fun getString(key: String): String? = prefs.get(key, null)
    override fun putString(key: String, value: String) = prefs.put(key, value)
}
