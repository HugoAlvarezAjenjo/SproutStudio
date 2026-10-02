package es.hugoalvarezajenjo.sproutstudio.editor

import androidx.compose.material3.Surface
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import es.hugoalvarezajenjo.sproutstudio.model.Document
import es.hugoalvarezajenjo.sproutstudio.ui.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.ide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindReplaceTest {
    private val text = "@startuml\nUser -> App: login\nApp -> DB: user lookup\nuser -> App: ok\n@enduml\n"

    private fun hits(q: FindQuery, t: String = text) = FindReplace.find(t, q).matches.map { t.substring(it) }

    @Test
    fun `plain search ignores case by default and treats regex chars literally`() {
        assertEquals(listOf("User", "user", "user"), hits(FindQuery("user")))
        assertEquals(listOf("->", "->", "->"), hits(FindQuery("->")))
        assertEquals(emptyList(), hits(FindQuery(".*")), "'.*' is literal when Regex is off")
    }

    @Test
    fun `match case and whole words`() {
        assertEquals(listOf("user", "user"), hits(FindQuery("user", matchCase = true)))
        assertEquals(listOf("App", "App", "App"), hits(FindQuery("app", wholeWords = true)))
        assertEquals(emptyList(), hits(FindQuery("pp", wholeWords = true)))
    }

    @Test
    fun `regex mode and invalid patterns`() {
        assertEquals(listOf("App:", "DB:", "App:"), hits(FindQuery("\\w+:", regex = true)))
        val bad = FindReplace.find(text, FindQuery("(", regex = true))
        assertTrue(bad.matches.isEmpty())
        assertNotNull(bad.error)
        assertTrue(FindReplace.find(text, FindQuery("^", regex = true)).matches.isEmpty(), "zero-width hits are skipped")
    }

    @Test
    fun `next and previous wrap around`() {
        val v0 = TextFieldValue(text, TextRange(0))
        val q = FindQuery("App", wholeWords = true)
        val ms = FindReplace.find(text, q).matches
        val v1 = navigate(v0, q, forward = true)!!
        assertEquals(0, currentMatchIndex(v1, ms))
        val v3 = navigate(navigate(v1, q, true)!!, q, true)!!
        assertEquals(2, currentMatchIndex(v3, ms))
        assertEquals(0, currentMatchIndex(navigate(v3, q, true)!!, ms), "wraps to the first")
        assertEquals(2, currentMatchIndex(navigate(v1, q, false)!!, ms), "previous wraps to the last")
        assertNull(navigate(v0, FindQuery("nope"), true))
    }

    @Test
    fun `replace replaces the selected match and moves to the next`() {
        val q = FindQuery("App", wholeWords = true)
        val first = navigate(TextFieldValue(text), q, true)!!
        val after = replaceCurrent(first, q, "Api")!!
        assertTrue(after.text.startsWith("@startuml\nUser -> Api: login\nApp"))
        assertEquals("App", after.text.substring(after.selection.min, after.selection.max))
        // With nothing selected, the first press only finds.
        val found = replaceCurrent(TextFieldValue(text), q, "Api")!!
        assertEquals(text, found.text)
    }

    @Test
    fun `replace all, with regex groups`() {
        val (v, n) = replaceAllIn(TextFieldValue(text), FindQuery("App", wholeWords = true), "Api")
        assertEquals(3, n)
        assertFalse("App" in v.text)
        val (v2, n2) = replaceAllIn(TextFieldValue("A -> B\nC -> D\n"), FindQuery("(\\w) -> (\\w)", regex = true), "$2 <- $1")
        assertEquals(2, n2)
        assertEquals("B <- A\nD <- C\n", v2.text)
        // A bad group reference is inserted literally instead of failing.
        assertEquals("x \$9 x", replaceAllIn(TextFieldValue("x a x"), FindQuery("a", regex = true), "\$9").first.text)
    }

    @Test
    fun `open seeds the query with a single-line selection only`() {
        val f = FindState()
        f.open(replace = false, seed = "DB")
        assertEquals("DB", f.query.text)
        f.open(replace = true, seed = "two\nlines")
        assertEquals("DB", f.query.text)
        assertTrue(f.replaceVisible)
    }
}

/** Drives the real editor + find bar off-screen. */
@OptIn(ExperimentalTestApi::class)
class FindBarUiTest {
    private val text = "@startuml\nUser -> App: login\nApp -> DB: lookup\nUser -> App: ok\n@enduml\n"

    @Test
    fun `typing selects the first hit, Enter cycles, counter follows`() {
        val doc = Document(null, text)
        runComposeUiTest {
            setContent { PumlTheme(dark = true) { Surface(color = ide.panel) { CodeEditor(doc, errorLine = null) } } }
            waitForIdle()
            runOnIdle { doc.find.open(replace = false, seed = null) }
            waitForIdle()
            onNode(hasTestTag("find-field")).performTextInput("app")
            waitForIdle()
            onNode(hasTestTag("find-count")).assertTextEquals("1/3")
            assertEquals("App", doc.text.substring(doc.value.selection.min, doc.value.selection.max))
            onNode(hasTestTag("find-field")).performKeyInput { pressKey(Key.Enter) }
            waitForIdle()
            onNode(hasTestTag("find-count")).assertTextEquals("2/3")
            onNode(hasTestTag("find-field")).performKeyInput { pressKey(Key.Escape) }
            waitForIdle()
            assertFalse(doc.find.visible)
        }
    }

    @Test
    fun `replace all from the bar`() {
        val doc = Document(null, text)
        runComposeUiTest {
            setContent { PumlTheme(dark = true) { Surface(color = ide.panel) { CodeEditor(doc, errorLine = null) } } }
            waitForIdle()
            runOnIdle { doc.find.open(replace = true, seed = null) }
            waitForIdle()
            onNode(hasTestTag("find-field")).performTextInput("User")
            onNode(hasTestTag("replace-field")).performTextInput("Customer")
            waitForIdle()
            runOnIdle { doc.replaceAll() }
            waitForIdle()
            onNode(hasTestTag("find-count")).assertTextEquals("2 replaced")
        }
        assertFalse("User" in doc.text)
        assertEquals(2, Regex("Customer").findAll(doc.text).count())
    }
}
