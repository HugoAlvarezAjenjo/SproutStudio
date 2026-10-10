package es.hugoalvarezajenjo.sproutstudio.domain.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectSessionTest {

    private val session = ProjectSession(
        listOf(OpenTab("a.puml", 3, 3), OpenTab("sub/b c.puml", 0, 10), OpenTab("odd\tname.puml", 1, 2)),
        active = 1,
    )

    @Test
    fun roundTrips() = assertEquals(session, ProjectSession.decode(session.encode()))

    @Test
    fun noTabsRoundTrips() {
        val empty = ProjectSession(emptyList(), 0)
        assertEquals(empty, ProjectSession.decode(empty.encode()))
    }

    @Test
    fun anUnknownOrDamagedValueIsDropped() {
        assertNull(ProjectSession.decode(""), "nothing stored")
        assertNull(ProjectSession.decode("v2\nactive\t0"), "a newer format")
        assertNull(ProjectSession.decode("v1\ntab\tx\t0\ta.puml"), "not a number")
        assertNull(ProjectSession.decode("v1\ntab\t0\t0"), "no path")
        assertNull(ProjectSession.decode("v1\nwhat\t0"), "unknown line")
    }

    @Test
    fun anOutOfRangeActiveTabIsClamped() {
        assertEquals(0, ProjectSession.decode("v1\nactive\t7\ntab\t0\t0\ta.puml")?.active)
        assertEquals(0, ProjectSession.decode("v1\nactive\t-2\ntab\t0\t0\ta.puml")?.active)
    }

    @Test
    fun droppingTheLastTabKeepsTheActiveOneInRange() {
        val shorter = ProjectSession(session.tabs, active = 2).dropLast()
        assertEquals(session.tabs.take(2), shorter.tabs)
        assertEquals(1, shorter.active)
        assertEquals(0, ProjectSession(listOf(OpenTab("a", 0, 0)), 0).dropLast().active)
    }
}
