package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.float

/** How the code editor looks: for now, its font size (in sp). */
class EditorSettings(store: SettingsStore) {
    /** Read through [clampFontSize]: an old or hand-edited value may be out of range. */
    val fontSize = store.float("editorFontSize", default = DEFAULT_FONT_SIZE)

    val currentFontSize: Float get() = clampFontSize(fontSize.value)

    fun changeFontSize(delta: Float) { fontSize.value = clampFontSize(currentFontSize + delta) }

    companion object {
        const val DEFAULT_FONT_SIZE = 13f
        const val MIN_FONT_SIZE = 10f
        const val MAX_FONT_SIZE = 24f

        fun clampFontSize(sp: Float) = sp.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
    }
}
