package es.hugoalvarezajenjo.sproutstudio.features.workspace

import kotlin.test.Test
import kotlin.test.assertEquals

class TabDragTest {

    private val tabs = mutableListOf("a", "b", "c")
    private val drag = TabDrag({ tabs }) { from, to -> tabs.add(to, tabs.removeAt(from)) }
        .apply { tabs.forEach { measured(it, 100) } }

    @Test
    fun aShortPullDoesNotMoveTheTab() {
        drag.start("a")
        drag.dragBy(49f)
        assertEquals(listOf("a", "b", "c"), tabs)
        assertEquals(49f, drag.offset)
    }

    @Test
    fun pastHalfTheNeighbourTheTabsSwapAndTheOffsetFollows() {
        drag.start("a")
        drag.dragBy(60f)
        assertEquals(listOf("b", "a", "c"), tabs)
        assertEquals(-40f, drag.offset) // still under the pointer, now from its new slot
    }

    @Test
    fun oneLongPullCrossesSeveralTabs() {
        drag.start("c")
        drag.dragBy(-260f)
        assertEquals(listOf("c", "a", "b"), tabs)
        assertEquals(-60f, drag.offset)
    }

    @Test
    fun theEndsStopTheTab() {
        drag.start("c")
        drag.dragBy(500f)
        assertEquals(listOf("a", "b", "c"), tabs)
    }

    @Test
    fun endingResets() {
        drag.start("a")
        drag.dragBy(30f)
        drag.end()
        assertEquals(null, drag.dragged)
        assertEquals(0f, drag.offset)
        drag.dragBy(80f) // nothing dragged: nothing moves
        assertEquals(listOf("a", "b", "c"), tabs)
    }
}
