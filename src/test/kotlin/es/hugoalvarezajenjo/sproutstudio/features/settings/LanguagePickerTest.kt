package es.hugoalvarezajenjo.sproutstudio.features.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import es.hugoalvarezajenjo.sproutstudio.app.AppGraph
import es.hugoalvarezajenjo.sproutstudio.core.i18n.Language
import es.hugoalvarezajenjo.sproutstudio.core.i18n.LanguageChoice
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ProvideLanguage
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LanguagePickerTest {
    private val graph = AppGraph.forTests()

    @AfterTest
    fun backToEnglish() = Language.English.makeDefault()

    @Test
    fun pickingALanguageRelabelsTheWindowAtOnce() = runComposeUiTest {
        graph.language.choice.value = LanguageChoice.English
        val vm = SettingsViewModel(graph.deps)
        setContent { ProvideLanguage(graph.language.resolved) { PumlTheme { SettingsContent(vm) } } }

        onNodeWithTag("section:APPEARANCE").assertTextEquals("Appearance")
        onNodeWithTag("language:Spanish").performClick()

        onNodeWithTag("section:APPEARANCE").assertTextEquals("Apariencia")
        onNodeWithText("Idioma").assertExists()
        assertEquals("Spanish", graph.store.getString("language"))

        onNodeWithTag("language:English").performClick()
        onNodeWithTag("section:APPEARANCE").assertTextEquals("Appearance")
    }
}
