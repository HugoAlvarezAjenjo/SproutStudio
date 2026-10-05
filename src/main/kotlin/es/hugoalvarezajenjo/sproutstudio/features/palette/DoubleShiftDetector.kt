package es.hugoalvarezajenjo.sproutstudio.features.palette

/**
 * Double-tap Shift, as in IntelliJ's Search Everywhere: two clean Shift presses (no other key
 * in between, so typing capitals never triggers it) within [windowMs].
 */
class DoubleShiftDetector(private val windowMs: Long = 400, private val clock: () -> Long = System::currentTimeMillis) {
    private var lastTap = 0L
    private var clean = false

    /** Feed every key event; returns true on the second tap. */
    fun onKey(isShift: Boolean, down: Boolean): Boolean {
        if (!isShift) { clean = false; lastTap = 0L; return false }
        if (down) { clean = true; return false }
        if (!clean) return false
        clean = false
        val now = clock()
        return if (lastTap != 0L && now - lastTap <= windowMs) {
            lastTap = 0L; true
        } else {
            lastTap = now; false
        }
    }
}
