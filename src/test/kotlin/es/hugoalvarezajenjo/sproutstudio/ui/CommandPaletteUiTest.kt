package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Drives the real palette: typing filters, arrows + Enter run, Esc and clicks. */
@OptIn(ExperimentalTestApi::class)
class CommandPaletteUiTest {

    private val ran = ArrayList<String>()
    private var dismissed = 0
    private val commands = listOf("Find…", "Fold All", "Switch to Light Theme", "Preview Only").map { t ->
        Command(t, t, "View") { ran += t }
    }

    private fun test(block: androidx.compose.ui.test.ComposeUiTest.() -> Unit) = runComposeUiTest {
        var open by androidx.compose.runtime.mutableStateOf(true)
        setContent {
            PumlTheme(dark = true) {
                Surface(Modifier.size(900.dp, 600.dp), color = ide.panel) {
                    if (open) CommandPalette(
                        commands,
                        onDismiss = { dismissed++; open = false },
                        onRun = { open = false; it.run() },
                        recentIds = emptyList(),
                    )
                }
            }
        }
        waitForIdle()
        block()
    }

    @Test fun typeThenEnterRunsTheBestMatch() = test {
        onNode(hasSetTextAction()).performTextInput("theme")
        waitForIdle()
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf("Switch to Light Theme"), ran)
        assertEquals(0, dismissed)
    }

    @Test fun arrowsMoveTheSelection() = test {
        onNode(hasSetTextAction()).performTextInput("f") // Find…, Fold All
        waitForIdle()
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionDown) }
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf("Fold All"), ran)
    }

    @Test fun upFromTheTopWrapsToTheBottom() = test {
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.DirectionUp) }
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(listOf("Preview Only"), ran)
    }

    @Test fun escapeClosesWithoutRunning() = test {
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(emptyList(), ran)
    }

    @Test fun clickingARowRunsIt() = test {
        onNodeWithText("Preview Only").performClick()
        waitForIdle()
        assertEquals(listOf("Preview Only"), ran)
    }

    @Test fun noMatchSaysSo() = test {
        onNode(hasSetTextAction()).performTextInput("zzzz")
        waitForIdle()
        onNode(hasText("Nothing matches", substring = true)).assertExists()
        onNode(hasSetTextAction()).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(emptyList(), ran)
    }
}
