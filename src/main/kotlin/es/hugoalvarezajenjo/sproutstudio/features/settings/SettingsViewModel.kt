package es.hugoalvarezajenjo.sproutstudio.features.settings

import es.hugoalvarezajenjo.sproutstudio.core.i18n.Language
import es.hugoalvarezajenjo.sproutstudio.core.i18n.LanguageChoice
import es.hugoalvarezajenjo.sproutstudio.features.editor.EditorSettings
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceDeps

/**
 * The Settings window's state and actions. It edits the app-wide settings directly, so every open
 * window follows a change at once (they all read the same observable [Setting]s).
 */
class SettingsViewModel(private val deps: WorkspaceDeps) {
    val darkTheme: Boolean get() = deps.theme.dark.value
    fun setDarkTheme(on: Boolean) { deps.theme.dark.value = on }

    val language: LanguageChoice get() = deps.language.choice.value
    fun setLanguage(choice: LanguageChoice) { deps.language.choice.value = choice }
    /** What "System" means on this machine. */
    val systemLanguage: Language get() = deps.language.system

    val darkDiagram: Boolean get() = deps.diagram.dark.value
    fun setDarkDiagram(on: Boolean) { deps.diagram.dark.value = on }

    val fontSize: Float get() = deps.editor.currentFontSize
    val canShrinkFont: Boolean get() = fontSize > EditorSettings.MIN_FONT_SIZE
    val canGrowFont: Boolean get() = fontSize < EditorSettings.MAX_FONT_SIZE
    fun changeFontSize(delta: Float) = deps.editor.changeFontSize(delta)

    val autoSave: Boolean get() = deps.autoSaver.enabled
    /** Open windows flush their own edits when they next lose focus or switch tab. */
    fun setAutoSave(on: Boolean) = deps.autoSaver.setEnabled(on)

    val recentCount: Int get() = deps.recents.folders().size
    fun clearRecents() = deps.recents.clear()

    /** Appearance and editor settings all at their defaults (recents and window layout don't count). */
    val allDefaults: Boolean
        get() = deps.theme.dark.isDefault && deps.language.choice.isDefault && deps.diagram.dark.isDefault &&
            deps.editor.fontSize.isDefault && deps.autoSaver.isDefault

    fun resetToDefaults() {
        deps.theme.dark.reset()
        deps.language.choice.reset()
        deps.diagram.dark.reset()
        deps.editor.fontSize.reset()
        deps.autoSaver.resetToDefault()
    }
}
