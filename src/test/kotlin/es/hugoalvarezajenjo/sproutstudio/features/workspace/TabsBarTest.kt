package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The tab bar's mouse handling: middle-click closes, dragging reorders, right-click has a menu. */
@OptIn(ExperimentalTestApi::class)
class TabsBarTest {

    private val names = listOf("pizza.puml", "morning.puml", "kitchen.puml")

    private fun project() = TestGraph.workspace().apply { names.forEach { open(File("samples/$it")) } }

    private fun WorkspaceViewModel.tabNames() = docs.map { it.name }

    private fun ComposeUiTest.show(p: WorkspaceViewModel, width: Int = 900) {
        setContent {
            PumlTheme(dark = true) {
                Surface(Modifier.size(width.dp, 60.dp), color = ide.panel) { TabsBar(p, onCloseTab = { p.requestCloseTab(it) }) }
            }
        }
        waitForIdle()
    }

    @Test
    fun middleClickClosesTheTab() {
        val p = project()
        runComposeUiTest {
            show(p)
            onNodeWithTag("tab-morning.puml").performMouseInput { moveTo(center); press(MouseButton.Tertiary); release(MouseButton.Tertiary) }
            waitForIdle()
        }
        assertEquals(listOf("pizza.puml", "kitchen.puml"), p.tabNames())
    }

    @Test
    fun aLeftClickDoesNotClose() {
        val p = project()
        runComposeUiTest {
            show(p)
            onNodeWithTag("tab-morning.puml").performClick()
            waitForIdle()
        }
        assertEquals(names, p.tabNames())
        assertEquals("morning.puml", p.active?.name)
    }

    @Test
    fun draggingATabPastItsNeighbourReordersAndSelectsIt() {
        val p = project()
        runComposeUiTest {
            show(p)
            val width = onNodeWithTag("tab-morning.puml").fetchSemanticsNode().size.width.toFloat()
            onNodeWithTag("tab-pizza.puml").performMouseInput {
                moveTo(center); press(MouseButton.Primary)
                repeat(10) { moveBy(Offset(width / 10 + 2f, 0f)) }
                release(MouseButton.Primary)
            }
            waitForIdle()
        }
        assertEquals(listOf("morning.puml", "pizza.puml", "kitchen.puml"), p.tabNames())
        assertEquals("pizza.puml", p.active?.name)
    }

    @Test
    fun holdingADraggedTabAtTheEdgeScrollsTheBarToTabsOutOfSight() {
        val all = names + listOf("architecture.puml", "common/cast.iuml", "common/style.iuml", "common/pizzeria.iuml")
        val p = TestGraph.workspace().apply { all.forEach { open(File("samples/$it")) } }
        runComposeUiTest {
            show(p, width = 420)
            val stripEnd = onNodeWithTag("tab-strip").fetchSemanticsNode().boundsInRoot.right
            val pizza = onNodeWithTag("tab-pizza.puml").fetchSemanticsNode().boundsInRoot.center.x
            // The bar scrolls every frame while the tab is held at the edge, so the test never
            // goes idle on its own: frames are advanced by hand.
            mainClock.autoAdvance = false
            onNodeWithTag("tab-pizza.puml").performMouseInput {
                moveTo(center); press(MouseButton.Primary)
                val steps = 20
                repeat(steps) { moveBy(Offset((stripEnd - 8f - pizza) / steps, 0f)) }
            }
            mainClock.advanceTimeBy(3_000) // held at the edge: the bar keeps scrolling
            onNodeWithTag("tab-pizza.puml").performMouseInput { release(MouseButton.Primary) }
            mainClock.autoAdvance = true
            waitForIdle()
        }
        assertEquals("pizza.puml", p.tabNames().last())
    }

    @Test
    fun rightClickCloseOtherTabs() {
        val p = project()
        runComposeUiTest {
            show(p)
            onNodeWithTag("tab-morning.puml").performMouseInput { moveTo(center); press(MouseButton.Secondary); release(MouseButton.Secondary) }
            waitForIdle()
            onNodeWithText("Close Other Tabs").performClick()
            waitForIdle()
        }
        assertEquals(listOf("morning.puml"), p.tabNames())
    }
}
