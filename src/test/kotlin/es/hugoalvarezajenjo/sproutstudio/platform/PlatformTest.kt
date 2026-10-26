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

    @Test
    fun `full screen on macOS leaves the menu bar strip empty only when the window starts at the top`() {
        val mac = MacPlatform()
        assertEquals(24, mac.fullScreenTopInset(windowTop = 0, screenTop = 0)) // external monitor
        assertEquals(24, mac.fullScreenTopInset(windowTop = -1080, screenTop = -1080)) // monitor above the main one
        assertEquals(0, mac.fullScreenTopInset(windowTop = 38, screenTop = 0)) // notch: macOS already moved it down
    }

    @Test
    fun `other desktops keep nothing at the top in full screen`() {
        assertEquals(0, WindowsPlatform().fullScreenTopInset(0, 0))
        assertEquals(0, LinuxPlatform().fullScreenTopInset(0, 0))
    }
}
