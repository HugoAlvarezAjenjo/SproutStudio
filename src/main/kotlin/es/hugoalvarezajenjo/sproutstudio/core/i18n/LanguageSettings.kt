package es.hugoalvarezajenjo.sproutstudio.core.i18n

import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.enum

/** What the user picked in Settings: follow the system, or one language. */
enum class LanguageChoice(val language: Language?) {
    System(null),
    English(Language.English),
    Spanish(Language.Spanish),
}

/**
 * The interface language setting. Follows the operating system by default.
 *
 * [system] is read once, when the app starts: once a language is applied it becomes the JVM's
 * default locale, so asking the JVM again would only echo the user's own choice back.
 */
class LanguageSettings(store: SettingsStore, val system: Language = Language.system()) {
    val choice = store.enum("language", LanguageChoice.System)

    /** The language to show; reading it in composition follows [choice]. */
    val resolved: Language get() = choice.value.language ?: system
}
