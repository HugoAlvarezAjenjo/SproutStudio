package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ContextMenuTest {

    @Test fun `groups are separated, and empty groups leave no line`() {
        val a = MenuItem("a"); val b = MenuItem("b"); val c = MenuItem("c")
        val items = menu {
            group { add(a) }
            group { }
            group { add(b); add(c) }
        }
        assertEquals(listOf(a, MenuSeparator, b, c), items)
    }

    @Test fun `tidy drops leading, trailing and doubled separators`() {
        val a = MenuItem("a"); val b = MenuItem("b")
        assertEquals(listOf(a, MenuSeparator, b), listOf(MenuSeparator, a, MenuSeparator, MenuSeparator, b, MenuSeparator).tidy())
    }

    private fun ComposeUiTest.area(items: List<ContextMenuItem>) = setContent {
        PumlTheme(dark = true) {
            Surface {
                Box(Modifier.size(600.dp, 400.dp)) {
                    ContextMenuArea(items = { items }) { Box(Modifier.size(100.dp).testTag("target")) }
                }
            }
        }
    }

    private fun ComposeUiTest.rightClick() {
        onNodeWithTag("target").performMouseInput { moveTo(center); press(MouseButton.Secondary); release(MouseButton.Secondary) }
        waitForIdle()
    }

    @Test fun `picking an entry runs it and closes the menu`() {
        var picked = ""
        runComposeUiTest {
            area(listOf(MenuItem("Rename") { picked += "rename" }, ContextMenuItem("Copy") { picked += "copy" }))
            rightClick()
            onNodeWithTag("menu-item:Copy").performClick()
            waitForIdle()
            assertTrue(onAllNodesWithTag("context-menu").fetchSemanticsNodes().isEmpty())
        }
        assertEquals("copy", picked)
    }

    @Test fun `a disabled entry does nothing`() {
        var picked = false
        runComposeUiTest {
            area(listOf(MenuItem("Rollback", isEnabled = false) { picked = true }))
            rightClick()
            onNodeWithTag("menu-item:Rollback").performClick()
            waitForIdle()
        }
        assertEquals(false, picked)
    }

    @Test fun `hovering an entry with children opens its submenu`() {
        var picked = ""
        runComposeUiTest {
            area(listOf(MenuItem("Export", children = listOf(MenuItem("PNG") { picked = "png" }, MenuItem("SVG") { picked = "svg" }))))
            rightClick()
            onNodeWithTag("menu-item:Export").performMouseInput { moveTo(center) }
            waitForIdle()
            onNodeWithTag("menu-item:SVG").performClick()
            waitForIdle()
            assertTrue(onAllNodesWithTag("context-menu").fetchSemanticsNodes().isEmpty())
        }
        assertEquals("svg", picked)
    }
}
