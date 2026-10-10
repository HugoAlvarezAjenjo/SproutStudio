package es.hugoalvarezajenjo.sproutstudio.domain.text

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectSearchTest {

    private fun files(vararg pairs: Pair<String, String>) = pairs.asSequence().map { (p, t) -> SourceFile(p, t) }
    private fun SearchHit.shown() = preview.substring(previewMatch.first, previewMatch.last + 1)

    @Test
    fun groupsHitsByFileWithLinesAndRangesInTheirText() {
        val a = "@startuml\nA -> Order\n  Order -> B\n@enduml\n"
        val r = ProjectSearch.search(files("/p/a.puml" to a, "/p/b.puml" to "nothing here", "/p/c.puml" to "order"), FindQuery("order"))
        assertEquals(listOf("/p/a.puml", "/p/c.puml"), r.files.map { it.path })
        assertEquals(3, r.hitCount)
        val hits = r.files[0].hits
        assertEquals(listOf(2, 3), hits.map { it.line })
        assertEquals("Order", a.substring(hits[1].range))
    }

    @Test
    fun previewDropsIndentationAndPointsAtTheMatch() {
        val hit = ProjectSearch.search(files("/a" to "x\n    Order -> B\n"), FindQuery("B")).files.single().hits.single()
        assertEquals("Order -> B", hit.preview)
        assertEquals("B", hit.shown())
    }

    @Test
    fun aLongLineIsCutAroundTheMatch() {
        val line = "a".repeat(300) + "NEEDLE" + "b".repeat(300)
        val hit = ProjectSearch.search(files("/a" to line), FindQuery("NEEDLE", matchCase = true)).files.single().hits.single()
        assertTrue(hit.preview.startsWith("…") && hit.preview.endsWith("…"))
        assertTrue(hit.preview.length <= ProjectSearch.PREVIEW_MAX + 2)
        assertEquals("NEEDLE", hit.shown())
    }

    @Test
    fun usesTheEditorsToggles() {
        val text = "Order order orders"
        assertEquals(1, ProjectSearch.search(files("/a" to text), FindQuery("Order", matchCase = true)).hitCount)
        assertEquals(2, ProjectSearch.search(files("/a" to text), FindQuery("order", wholeWords = true)).hitCount)
        assertEquals(3, ProjectSearch.search(files("/a" to text), FindQuery("or\\w+", regex = true)).hitCount)
    }

    @Test
    fun aMatchAcrossLinesIsHighlightedUpToItsFirstLine() {
        val hit = ProjectSearch.search(files("/a" to "alt ok\nelse\n"), FindQuery("ok\\nelse", regex = true)).files.single().hits.single()
        assertEquals("alt ok", hit.preview)
        assertEquals("ok", hit.shown())
    }

    @Test
    fun anInvalidRegexIsReportedNotThrown() {
        val r = ProjectSearch.search(files("/a" to "x"), FindQuery("(", regex = true))
        assertNotNull(r.error)
        assertTrue(r.files.isEmpty())
    }

    @Test
    fun stopsAtTheLimitAndSaysSo() {
        val r = ProjectSearch.search(files("/a" to "x x x", "/b" to "x x"), FindQuery("x"), maxHits = 4)
        assertEquals(4, r.hitCount)
        assertTrue(r.truncated)
        assertFalse(ProjectSearch.search(files("/a" to "x"), FindQuery("x")).truncated)
    }

    @Test
    fun anEmptyQueryFindsNothing() {
        assertEquals(ProjectSearchResult.Empty, ProjectSearch.search(files("/a" to "x"), FindQuery("")))
    }
}
