package es.hugoalvarezajenjo.sproutstudio.core.settings

import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingTest {

    @Test
    fun defaultsUntilSetThenPersists() {
        val store = InMemorySettingsStore()
        val s = store.boolean("x", default = true)
        assertEquals(true, s.value)
        s.value = false
        assertEquals("false", store.getString("x"))
        assertEquals(false, store.boolean("x", default = true).value, "a new reader sees the stored value")
    }

    @Test
    fun badStoredValueFallsBackToDefault() {
        val store = InMemorySettingsStore(mapOf("f" to "wide", "e" to "NOPE"))
        assertEquals(1.5f, store.float("f", 1.5f).value)
        assertEquals(Mode.A, store.enum("e", Mode.A).value)
    }

    @Test
    fun listsRoundTrip() {
        val store = InMemorySettingsStore()
        val l = store.stringList("l")
        l.value = listOf("a", "b")
        assertEquals(listOf("a", "b"), store.stringList("l").value)
    }

    private enum class Mode { A }

    // ── Values written by older versions (Preferences.putBoolean/putFloat) still read back ──

    private val node = Preferences.userRoot().node("es/hugoalvarezajenjo/sproutstudio-test-${System.nanoTime()}")

    @AfterTest fun cleanup() = node.removeNode()

    @Test
    fun readsOldTypedPreferences() {
        node.putBoolean("b", false)
        node.putFloat("w", 321f)
        val store = PreferencesSettingsStore(node)
        assertEquals(false, store.boolean("b", true).value)
        assertEquals(321f, store.float("w", 240f).value)
    }
}
