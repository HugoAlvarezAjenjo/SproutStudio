package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.TestGraph

import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.runBlocking
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
    fun movingATabKeepsTheSameTabActive() {
        val p = TestGraph.workspace()
        val (a, b, c) = List(3) { p.newDocument() }
        p.selectTab(1) // b
        p.moveTab(0, 2)
        assertEquals(listOf(b, c, a), p.docs.toList())
        assertEquals(b, p.active)
        p.moveTab(0, 1)
        assertEquals(listOf(c, b, a), p.docs.toList())
        assertEquals(b, p.active)
        p.moveTab(0, 7) // out of range: nothing moves
        assertEquals(listOf(c, b, a), p.docs.toList())
    }

    @Test
    fun closeOthersLeftAndRightCloseTheRightTabs() {
        val p = TestGraph.workspace()
        val (a, b, c) = List(3) { p.newDocument() }
        p.newDocument()
        runBlocking { p.closeTabsToTheRight(c).join() }
        assertEquals(listOf(a, b, c), p.docs.toList())
        runBlocking { p.closeTabsToTheLeft(b).join() }
        assertEquals(listOf(b, c), p.docs.toList())
        runBlocking { p.closeOtherTabs(c).join() }
        assertEquals(listOf(c), p.docs.toList())
        assertEquals(c, p.active)
        runBlocking { p.closeAllTabs().join() }
        assertEquals(0, p.docs.size)
    }

    @Test
    fun closingSeveralTabsAsksOnceAboutTheUnsavedOnes() {
        val p = TestGraph.workspace()
        val (a, b, _) = List(3) { p.newDocument() }
        b.value = TextFieldValue("edited")
        runBlocking { p.closeOtherTabs(a).join() }
        assertEquals(listOf(b), p.pendingClose?.docs)
        assertEquals(3, p.docs.size, "nothing closes before the answer")
        p.discardAndClose()
        assertEquals(listOf(a), p.docs.toList())
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
