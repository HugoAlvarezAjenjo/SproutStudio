package es.hugoalvarezajenjo.sproutstudio.features.settings

import es.hugoalvarezajenjo.sproutstudio.app.AppGraph
import es.hugoalvarezajenjo.sproutstudio.app.AppWindow
import es.hugoalvarezajenjo.sproutstudio.core.settings.InMemorySettingsStore
import es.hugoalvarezajenjo.sproutstudio.features.editor.EditorSettings
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsViewModelTest {
    private val graph = AppGraph.forTests()
    private val vm = SettingsViewModel(graph.deps)

    @Test
    fun changesReachTheSharedSettings() {
        vm.setDarkTheme(false)
        vm.setDarkDiagram(true)
        vm.setAutoSave(false)
        assertFalse(graph.theme.dark.value)
        assertTrue(graph.diagram.dark.value)
        assertFalse(graph.autoSaver.enabled)
        assertEquals("false", graph.store.getString("darkTheme"), "persisted, so the next launch keeps it")
    }

    @Test
    fun fontSizeStaysInRange() {
        repeat(40) { vm.changeFontSize(1f) }
        assertEquals(EditorSettings.MAX_FONT_SIZE, vm.fontSize)
        assertFalse(vm.canGrowFont)
        repeat(40) { vm.changeFontSize(-1f) }
        assertEquals(EditorSettings.MIN_FONT_SIZE, vm.fontSize)
        assertFalse(vm.canShrinkFont)
    }

    @Test
    fun outOfRangeStoredFontSizeIsClamped() {
        val editor = EditorSettings(InMemorySettingsStore(mapOf("editorFontSize" to "99")))
        assertEquals(EditorSettings.MAX_FONT_SIZE, editor.currentFontSize)
    }

    @Test
    fun restoreDefaultsUndoesEveryChangeButKeepsRecents() {
        val dir = Files.createTempDirectory("recent").toFile()
        graph.recents.add(dir)
        assertTrue(vm.allDefaults)
        vm.setDarkTheme(false); vm.setDarkDiagram(true); vm.changeFontSize(3f); vm.setAutoSave(false)
        vm.setRestoreSession(false); vm.setSoftWrap(true)
        assertFalse(vm.allDefaults)

        vm.resetToDefaults()

        assertTrue(vm.allDefaults)
        assertFalse(vm.softWrap, "soft wrap is off by default")
        assertTrue(graph.theme.dark.value)
        assertEquals(EditorSettings.DEFAULT_FONT_SIZE, vm.fontSize)
        assertTrue(graph.autoSaver.enabled)
        assertTrue(vm.restoreSession, "restoring the session is on by default")
        assertEquals(1, vm.recentCount, "recents are cleared on their own, not by Restore defaults")
    }

    @Test
    fun clearRecentsEmptiesTheList() {
        graph.recents.add(Files.createTempDirectory("a").toFile())
        graph.recents.add(Files.createTempDirectory("b").toFile())
        assertEquals(2, vm.recentCount)
        vm.clearRecents()
        assertEquals(0, vm.recentCount)
    }

    @Test
    fun onlyOneSettingsWindow() {
        graph.windows.openSettings()
        graph.windows.openSettings()
        assertEquals(1, graph.windows.windows.count { it is AppWindow.Settings })
    }

    @Test
    fun themeToggleFromThePaletteIsTheSameSetting() {
        // The command palette and the Settings window flip the same value.
        graph.theme.toggle()
        assertFalse(vm.darkTheme)
    }
}
