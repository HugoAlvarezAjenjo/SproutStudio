package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.TestGraph

import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkspaceViewModelTest {

    @Test
    fun sidebarWidthIsClampedToSaneBounds() {
        val p = TestGraph.workspace()
        p.resizeSidebar(10f)
        assertEquals(WorkspaceViewModel.SIDEBAR_MIN, p.sidebarWidth)
        p.resizeSidebar(5000f)
        assertEquals(WorkspaceViewModel.SIDEBAR_MAX, p.sidebarWidth)
        p.resizeSidebar(333f)
        assertEquals(333f, p.sidebarWidth)
    }
}
