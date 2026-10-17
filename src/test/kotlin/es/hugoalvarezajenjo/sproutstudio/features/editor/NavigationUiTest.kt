package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.material3.Surface
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Navigation in the real editor: the usages list driven by the keyboard, and ⌘-click. */
@OptIn(ExperimentalTestApi::class)
class NavigationUiTest {

    private val text = "@startuml\nactor User\nparticipant App\nUser -> App\nApp -> User\nUser -> App\n@enduml\n"

    @Test
    fun usagesListIsDrivenFromTheEditorKeyboard() = runComposeUiTest {
        val doc = TestGraph.document(null, text)
        setContent { PumlTheme(dark = true) { Surface(color = ide.panel) { CodeEditor(doc, errorLine = null) } } }
        waitForIdle()
        doc.findUsages(text.indexOf("App"))
        waitUntil(timeoutMillis = 15_000) { onAllNodes(androidx.compose.ui.test.hasTestTag("usages-popup")).fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("usages-popup").assertExists()

        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.Enter) }
        waitForIdle()
        assertNull(doc.nav.usages)
        val second = text.indexOf("App ->")
        assertEquals(TextRange(second, second + 3), doc.value.selection)
    }

    @Test
    fun cmdClickOnANameGoesToItsDeclaration() = runComposeUiTest {
        val doc = TestGraph.document(null, text)
        setContent { PumlTheme(dark = true) { Surface(color = ide.panel) { CodeEditor(doc, errorLine = null) } } }
        waitForIdle()
        val field = onNode(hasSetTextAction())
        val layouts = mutableListOf<TextLayoutResult>()
        field.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        val l = layouts.first()
        val ref = text.indexOf("User -> App\nApp") + 1
        val box = l.getBoundingBox(ref)
        // The text field's semantics node is the laid-out text itself (inside the editor's padding).
        val pos = box.center

        val mod = if (Os.isMac) Key.MetaLeft else Key.CtrlLeft
        field.performKeyInput { keyDown(mod) }
        field.performMouseInput { moveTo(pos); press(); release() }
        field.performKeyInput { keyUp(mod) }
        waitForIdle()
        val decl = text.indexOf("User")
        assertEquals(TextRange(decl, decl + 4), doc.value.selection)
    }
}
