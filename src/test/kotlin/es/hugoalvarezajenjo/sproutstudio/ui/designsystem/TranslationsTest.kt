package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import es.hugoalvarezajenjo.sproutstudio.core.i18n.Language
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.branch_delete_title
import es.hugoalvarezajenjo.sproutstudio.resources.dialog_dont_save
import es.hugoalvarezajenjo.sproutstudio.resources.settings_points
import es.hugoalvarezajenjo.sproutstudio.resources.settings_recent_count
import es.hugoalvarezajenjo.sproutstudio.resources.settings_theme
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class TranslationsTest {
    @AfterTest
    fun backToEnglish() = Language.English.makeDefault()

    /** Reads nothing but its text, so only tr's own subscription can relabel it. */
    @Composable
    private fun Label() = Text(tr(Res.string.settings_theme), Modifier.testTag("label"))

    @Test
    fun textsSwitchLanguageLiveWithoutAnythingElseChanging() = runComposeUiTest {
        val language = mutableStateOf(Language.English)
        setContent { ProvideLanguage(language.value) { Label() } }
        onNodeWithTag("label").assertTextEquals("Theme")

        language.value = Language.Spanish
        onNodeWithTag("label").assertTextEquals("Tema")

        language.value = Language.English
        onNodeWithTag("label").assertTextEquals("Theme")
    }

    @Test
    fun placeholdersAndPlurals() = runComposeUiTest {
        val language = mutableStateOf(Language.English)
        setContent {
            ProvideLanguage(language.value) {
                Text(tr(Res.string.settings_points, 14), Modifier.testTag("points"))
                Text(tr(Res.plurals.settings_recent_count, 1, 1), Modifier.testTag("one"))
                Text(tr(Res.plurals.settings_recent_count, 3, 3), Modifier.testTag("many"))
            }
        }
        onNodeWithTag("points").assertTextEquals("14 pt")
        onNodeWithTag("one").assertTextEquals("1 folder on the welcome screen.")
        onNodeWithTag("many").assertTextEquals("3 folders on the welcome screen.")

        language.value = Language.Spanish
        onNodeWithTag("one").assertTextEquals("1 carpeta en la pantalla de inicio.")
        onNodeWithTag("many").assertTextEquals("3 carpetas en la pantalla de inicio.")
    }

    @Test
    fun plainCodeLookupFollowsTheLanguageToo() {
        assertEquals("Theme", text(Res.string.settings_theme))
        // Quotes and apostrophes survive the XML round trip.
        assertEquals("Don't Save", text(Res.string.dialog_dont_save))
        assertEquals("Delete branch \"x\"?", text(Res.string.branch_delete_title, "x"))
        Language.Spanish.makeDefault()
        assertEquals("Tema", text(Res.string.settings_theme))
        assertEquals("3 carpetas en la pantalla de inicio.", text(Res.plurals.settings_recent_count, 3, 3))
        Language.English.makeDefault()
        assertEquals("Theme", text(Res.string.settings_theme))
    }

    private fun names(dir: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/composeResources/$dir/strings.xml"))
        return listOf("string", "plurals").flatMap { tag ->
            val nodes = doc.getElementsByTagName(tag)
            (0 until nodes.length).map { "$tag:" + nodes.item(it).attributes.getNamedItem("name").nodeValue }
        }.toSet()
    }

    @Test
    fun everyTextIsTranslated() {
        val english = names("values")
        val spanish = names("values-es")
        assertTrue(english.isNotEmpty())
        assertEquals(emptySet(), english - spanish, "missing in values-es/strings.xml")
        assertEquals(emptySet(), spanish - english, "in values-es but not in values (renamed or removed?)")
    }
}
