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

    @Test
    fun closingAnUnsavedTabAsksFirst() {
        val p = TestGraph.workspace()
        val doc = p.newDocument() // untitled: autosave can't write it
        doc.value = androidx.compose.ui.text.input.TextFieldValue("@startuml\nA -> B\n@enduml")
        kotlinx.coroutines.runBlocking { p.requestCloseTab(doc).join() }
        assertEquals(listOf(doc), p.pendingClose?.docs)
        assertEquals(1, p.docs.size, "nothing closes before the answer")

        p.cancelClose()
        assertEquals(null, p.pendingClose)
        kotlinx.coroutines.runBlocking { p.requestCloseTab(doc).join() }
        p.discardAndClose()
        assertEquals(0, p.docs.size)
    }

    @Test
    fun aCleanTabClosesWithoutAsking() {
        val p = TestGraph.workspace()
        val doc = p.newDocument()
        kotlinx.coroutines.runBlocking { p.requestCloseTab(doc).join() }
        assertEquals(null, p.pendingClose)
        assertEquals(0, p.docs.size)
    }
}
