package es.hugoalvarezajenjo.sproutstudio.features.document

import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.features.document.PreviewAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

/** Requests to a document's editor are events: each one arrives once, in order, even if sent before anyone listens. */
class DocumentEventsTest {

    @Test
    fun jumpsSentBeforeTheEditorShowsAllArrive() = runBlocking {
        val d = Document(null, "a\nb\nc\nd")
        d.jumpTo(2) // e.g. clicked in the Problems panel while in preview-only layout
        d.jumpTo(4)
        assertEquals(listOf(2, 4), d.jumpRequests.take(2).toList())
    }

    @Test
    fun theSameLineTwiceIsTwoJumps() = runBlocking {
        val d = Document(null, "a\nb")
        d.jumpTo(2); d.jumpTo(2)
        assertEquals(listOf(2, 2), d.jumpRequests.take(2).toList())
    }

    @Test
    fun aPreviewCommandIsTakenOnce() = runBlocking {
        val d = Document(null, "")
        d.requestPreview(PreviewAction.EXPORT_SVG)
        assertEquals(PreviewAction.EXPORT_SVG, d.previewCommandFlow.first())
        assertNull(d.takePreviewCommand(), "already handled")
    }
}
