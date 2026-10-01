package es.hugoalvarezajenjo.sproutstudio.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ProjectStateTest {

    @Test
    fun sidebarWidthIsClampedToSaneBounds() {
        val p = ProjectState()
        p.resizeSidebar(10f)
        assertEquals(ProjectState.SIDEBAR_MIN, p.sidebarWidth)
        p.resizeSidebar(5000f)
        assertEquals(ProjectState.SIDEBAR_MAX, p.sidebarWidth)
        p.resizeSidebar(333f)
        assertEquals(333f, p.sidebarWidth)
    }
}
