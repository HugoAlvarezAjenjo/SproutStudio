package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.features.preview.themeMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DropdownMenuTest {

    private fun place(anchor: IntRect, menu: IntSize, window: IntSize = IntSize(800, 600)) =
        BelowAnchor.calculatePosition(anchor, window, LayoutDirection.Ltr, menu)

    @Test fun `opens under the button, left aligned`() {
        assertEquals(IntOffset(100, 42), place(IntRect(100, 10, 130, 40), IntSize(200, 300)))
    }

    @Test fun `opens above the button when there is no room below`() {
        assertEquals(IntOffset(100, 248), place(IntRect(100, 550, 130, 580), IntSize(200, 300)))
    }

    @Test fun `stays inside the window`() {
        // Too wide for the right edge, and too tall for either side.
        assertEquals(IntOffset(600, 0), place(IntRect(700, 300, 730, 330), IntSize(200, 900)))
    }

    @Test fun `the theme in use is ticked, and Default when there is none`() {
        fun checked(current: String?) = themeMenu(current) {}.filterIsInstance<MenuItem>().filter { it.checked }.map { it.label }
        assertEquals(listOf("cerulean"), checked("Cerulean"))
        assertEquals(listOf("Default"), checked(null))
    }

    @Test fun `a long menu scrolls, and picking closes it`() {
        var picked: String? = "none"
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) {
                    Surface(Modifier.size(400.dp, 300.dp)) {
                        var open by remember { mutableStateOf(true) }
                        Box(Modifier.testTag("anchor")) {
                            IdeDropdownMenu(open, onDismissRequest = { open = false }) { themeMenu(null) { picked = it } }
                        }
                    }
                }
            }
            // "vibrant" is the last theme, far below a 300dp window.
            onNodeWithTag("menu-item:vibrant").performScrollTo().performClick()
            waitForIdle()
            assertTrue(onAllNodesWithTag("context-menu").fetchSemanticsNodes().isEmpty())
        }
        assertEquals("vibrant", picked)
    }
}
