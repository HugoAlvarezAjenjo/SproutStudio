package es.hugoalvarezajenjo.sproutstudio.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemplatesTest {
    @Test fun everyTemplateHasUniqueIdAndNonEmptyBody() {
        val ids = Templates.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "duplicate template id")
        Templates.all.forEach { assertTrue(it.body.isNotBlank(), "${it.id} empty") }
    }

    @Test fun wholeDiagramsAreBalancedStartEnd() {
        Templates.wholeDiagrams.forEach { t ->
            val starts = Regex("""@start\w+""").findAll(t.body).count()
            val ends = Regex("""@end\w+""").findAll(t.body).count()
            assertEquals(starts, ends, "${t.id} unbalanced @start/@end")
            assertTrue(starts >= 1, "${t.id} has no diagram")
        }
    }

    @Test fun previewSourceStripsTheCaretMarker() {
        Templates.all.forEach { assertTrue("\$0" !in it.previewSource, "${it.id} kept marker") }
    }

    @Test fun galleryGroupsAreOrderedUmlFirst() {
        val cats = Templates.byCategory.map { it.first }
        assertEquals("UML", cats.first())
        assertTrue(cats.toSet() == Templates.all.map { it.category }.toSet())
    }

    @Test fun blockTemplatesAreNotWholeDiagrams() {
        assertTrue(Templates.all.any { !it.whole })
        Templates.all.filter { !it.whole }.forEach { assertTrue("@start" !in it.body, "${it.id} should be a block") }
    }
}
