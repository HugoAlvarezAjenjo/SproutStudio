package es.hugoalvarezajenjo.sproutstudio.features.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DragScrollTest {
    @Test fun `inside the viewport it does not scroll`() {
        assertEquals(0f, DragScroll.velocity(0f, 300f))
        assertEquals(0f, DragScroll.velocity(150f, 300f))
        assertEquals(0f, DragScroll.velocity(300f, 300f))
    }

    @Test fun `past an edge it scrolls towards it, faster the further out`() {
        val near = DragScroll.velocity(310f, 300f)
        val far = DragScroll.velocity(400f, 300f)
        assertTrue(near > 0f && far > near, "near $near, far $far")
        assertTrue(DragScroll.velocity(-10f, 300f) < 0f)
        assertEquals(-DragScroll.velocity(310f, 300f), DragScroll.velocity(-10f, 300f))
    }

    @Test fun `just past an edge it still moves, and never faster than the cap`() {
        assertEquals(1f, DragScroll.velocity(300.1f, 300f))
        assertEquals(DragScroll.MAX_PX_PER_FRAME, DragScroll.velocity(10_000f, 300f))
    }

    @Test fun `without a viewport it does not scroll`() {
        assertEquals(0f, DragScroll.velocity(-50f, 0f))
    }
}
