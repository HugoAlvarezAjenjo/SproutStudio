package es.hugoalvarezajenjo.sproutstudio.core.i18n

import es.hugoalvarezajenjo.sproutstudio.core.settings.InMemorySettingsStore
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageTest {
    private val original = Locale.getDefault()
    private val originalFormat = Locale.getDefault(Locale.Category.FORMAT)

    @AfterTest
    fun restoreLocale() {
        Locale.setDefault(original)
        Locale.setDefault(Locale.Category.FORMAT, originalFormat)
    }

    @Test
    fun systemLanguageFallsBackToEnglish() {
        assertEquals(Language.Spanish, Language.system(Locale.forLanguageTag("es-ES")))
        assertEquals(Language.Spanish, Language.system(Locale.forLanguageTag("es-MX")))
        assertEquals(Language.English, Language.system(Locale.forLanguageTag("en-GB")))
        assertEquals(Language.English, Language.system(Locale.forLanguageTag("fr-FR")), "untranslated languages get English")
    }

    @Test
    fun settingFollowsTheSystemUntilTheUserPicks() {
        val store = InMemorySettingsStore()
        val settings = LanguageSettings(store, system = Language.Spanish)
        assertEquals(LanguageChoice.System, settings.choice.value)
        assertEquals(Language.Spanish, settings.resolved)

        settings.choice.value = LanguageChoice.English
        assertEquals(Language.English, settings.resolved)
        assertEquals("English", store.getString("language"), "persisted for the next launch")
        assertEquals(Language.English, LanguageSettings(store, system = Language.Spanish).resolved)
    }

    @Test
    fun makingALanguageDefaultKeepsTheNumberFormat() {
        Locale.setDefault(Locale.forLanguageTag("de-DE"))
        Language.Spanish.makeDefault()
        assertEquals("es", Locale.getDefault().language)
        assertEquals("es", Locale.getDefault(Locale.Category.DISPLAY).language)
        assertEquals(Locale.forLanguageTag("de-DE"), Locale.getDefault(Locale.Category.FORMAT), "numbers and dates as the system has them")
    }
}
