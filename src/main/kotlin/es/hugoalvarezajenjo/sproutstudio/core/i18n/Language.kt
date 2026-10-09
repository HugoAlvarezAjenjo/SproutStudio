package es.hugoalvarezajenjo.sproutstudio.core.i18n

import java.util.Locale

/**
 * A language the app's interface is translated into. Its texts live in
 * `src/main/composeResources/values[-code]/strings.xml`. [nativeName] is how its speakers call it.
 */
enum class Language(val code: String, val nativeName: String) {
    English("en", "English"),
    Spanish("es", "Español");

    val locale: Locale get() = Locale.forLanguageTag(code)

    /**
     * Makes this the JVM's default locale, which is what Compose resources pick strings by.
     * Number and date formatting ([Locale.Category.FORMAT]) stay as the system has them: the
     * user chose the words, not how numbers look, and diagram exports must not change.
     */
    fun makeDefault() {
        if (Locale.getDefault(Locale.Category.DISPLAY) == locale) return
        val format = Locale.getDefault(Locale.Category.FORMAT)
        Locale.setDefault(locale)
        Locale.setDefault(Locale.Category.FORMAT, format)
    }

    companion object {
        /** The operating system's language when we have it, English otherwise. */
        fun system(locale: Locale = Locale.getDefault()): Language =
            entries.firstOrNull { it.code == locale.language } ?: English
    }
}
