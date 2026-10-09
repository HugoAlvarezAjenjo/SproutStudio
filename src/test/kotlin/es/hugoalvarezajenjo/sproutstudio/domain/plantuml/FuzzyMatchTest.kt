package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuzzyMatchTest {

    @Test
    fun prefixBeatsEverythingAndHighlightsTheTypedPart() {
        val m = assertNotNull(FuzzyMatch.match("skinparam", "skin"))
        assertEquals(FuzzyMatch.PREFIX, m.tier)
        assertEquals(listOf(0, 1, 2, 3), m.indices)
    }

    @Test
    fun initialsMatchCamelCaseWords() {
        val m = assertNotNull(FuzzyMatch.match("backgroundColor", "bc"))
        assertEquals(FuzzyMatch.INITIALS, m.tier)
        assertEquals(listOf(0, 10), m.indices)
    }

    @Test
    fun aChunkMayRunOnInsideItsWord() {
        val m = assertNotNull(FuzzyMatch.match("backgroundColor", "bacol"))
        assertEquals(FuzzyMatch.INITIALS, m.tier)
        assertEquals(listOf(0, 1, 10, 11, 12), m.indices)
    }

    @Test
    fun initialsMatchAcrossSeparatorsAndDigits() {
        assertEquals(FuzzyMatch.INITIALS, FuzzyMatch.match("linetype ortho", "lo")?.tier)
        assertEquals(FuzzyMatch.INITIALS, FuzzyMatch.match("C4/C4_Container", "c4c")?.tier)
        assertEquals(FuzzyMatch.INITIALS, FuzzyMatch.match("defaultFontSize", "dfs")?.tier)
    }

    @Test
    fun initialsNeedTheRightWords() {
        // "s" would have to start a word of defaultFontName, and no word there starts with "s".
        assertNull(FuzzyMatch.match("defaultFontName", "dfs"))
    }

    @Test
    fun aSharedSigilIsSkipped() {
        val m = assertNotNull(FuzzyMatch.match("@startMindmap", "@sm"))
        assertEquals(FuzzyMatch.INITIALS, m.tier)
        assertEquals(listOf(0, 1, 6), m.indices)
    }

    @Test
    fun substringIsTheLastResort() {
        val m = assertNotNull(FuzzyMatch.match("participant", "cip"))
        assertEquals(FuzzyMatch.CONTAINS, m.tier)
        assertEquals(listOf(5, 6, 7), m.indices)
    }

    @Test
    fun oneCharacterOnlyMatchesAsPrefix() {
        assertNull(FuzzyMatch.match("ArrowColor", "c"))
    }

    @Test
    fun completionOffersSkinparamsByInitials() {
        val text = "@startuml\nskinparam bc"
        val labels = assertNotNull(CompletionEngine.complete(text, text.length)).items.map { it.label }
        assertTrue("backgroundColor" in labels, labels.toString())
        assertTrue("BorderColor" in labels, labels.toString())
    }

    @Test
    fun completionRanksPrefixMatchesBeforeInitials() {
        // "bc": BcastHub starts with it, BillingCore only matches by initials.
        val text = "@startuml\nparticipant BillingCore\nparticipant BcastHub\nbc"
        val req = assertNotNull(CompletionEngine.complete(text, text.length))
        val labels = req.items.map { it.label }
        assertTrue(labels.indexOf("BcastHub") in 0 until labels.indexOf("BillingCore"), labels.toString())
        assertEquals(listOf(0, 7), req.items.first { it.label == "BillingCore" }.matched)
    }

    @Test
    fun completionMatchesDeclaredSymbolsByInitials() {
        val text = "@startuml\nparticipant OrderService\nparticipant PaymentGateway\nos"
        val labels = assertNotNull(CompletionEngine.complete(text, text.length)).items.map { it.label }
        assertTrue("OrderService" in labels, labels.toString())
    }
}
