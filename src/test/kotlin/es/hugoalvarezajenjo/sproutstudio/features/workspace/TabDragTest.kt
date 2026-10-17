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

    // Edge scrolling. Five 100 px tabs (500 px of strip) seen through a 250 px window.

    private val strip = mutableListOf("a", "b", "c", "d", "e")
    private val long = TabDrag({ strip }) { from, to -> strip.add(to, strip.removeAt(from)) }
        .apply { strip.forEach { measured(it, 100) } }

    private fun TabDrag<String>.step(scroll: Int = 0) = edgeScroll(scroll, viewport = 250, edge = 40f, maxStep = 10f)

    @Test
    fun aTabClearOfBothEdgesDoesNotScroll() {
        long.start("b") // drawn at 100..200, the end zone starts at 210
        long.dragBy(5f)
        assertEquals(0f, long.step())
    }

    @Test
    fun pullingIntoTheEndZoneScrollsForwardFasterTheDeeper() {
        long.start("b")
        long.dragBy(30f) // right edge at 230: half way into the zone
        assertEquals(5f, long.step())
        long.dragBy(15f) // 245, but b and c have not swapped yet (45 < 50)
        assertEquals(8.75f, long.step())
    }

    @Test
    fun pastTheEdgeTheSpeedIsCapped() {
        long.start("d") // 300..400, beyond the window
        long.dragBy(10f)
        assertEquals(10f, long.step())
    }

    @Test
    fun pullingIntoTheStartZoneScrollsBack() {
        long.start("d") // with the strip scrolled by 200, d is drawn at 100..200
        long.dragBy(-80f) // swaps with c and is drawn 20 px into the window
        assertEquals(-5f, long.step(scroll = 200))
    }

    @Test
    fun onlyTheEdgeItIsHeadingForScrolls() {
        long.start("c") // 200..300: its right half is already past the window
        assertEquals(0f, long.step()) // not moved yet
        long.dragBy(-5f) // pulled away from that edge
        assertEquals(0f, long.step())
        long.dragBy(10f) // and back towards it
        assertEquals(10f, long.step())
    }

    @Test
    fun nothingDraggedNothingScrolls() {
        assertEquals(0f, long.step())
    }
}
