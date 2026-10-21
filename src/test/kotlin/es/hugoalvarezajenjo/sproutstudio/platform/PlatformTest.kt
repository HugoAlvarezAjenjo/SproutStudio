package es.hugoalvarezajenjo.sproutstudio.platform

import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PlatformTest {

    @Test
    fun `os name maps to its family, and anything unknown is Linux`() {
        assertEquals(Os.Family.MAC, Os.familyOf("Mac OS X"))
        assertEquals(Os.Family.WINDOWS, Os.familyOf("Windows 11"))
        assertEquals(Os.Family.LINUX, Os.familyOf("Linux"))
        assertEquals(Os.Family.LINUX, Os.familyOf("FreeBSD"))
    }

    @Test
    fun `each family gets its own platform`() {
        assertIs<MacPlatform>(Platform.forOs(Os.Family.MAC))
        assertIs<WindowsPlatform>(Platform.forOs(Os.Family.WINDOWS))
        assertIs<LinuxPlatform>(Platform.forOs(Os.Family.LINUX))
    }
}
