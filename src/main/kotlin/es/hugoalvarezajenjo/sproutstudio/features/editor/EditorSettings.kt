package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.boolean
import es.hugoalvarezajenjo.sproutstudio.core.settings.float

/** How the code editor looks: its font size (in sp) and whether long lines wrap. */
class EditorSettings(store: SettingsStore) {
    /** Read through [clampFontSize]: an old or hand-edited value may be out of range. */
    val fontSize = store.float("editorFontSize", default = DEFAULT_FONT_SIZE)

    /** Soft wrap: long lines continue on the next row instead of scrolling sideways. Only the view changes, never the file. */
    val softWrap = store.boolean("editorSoftWrap", default = false)

    val currentFontSize: Float get() = clampFontSize(fontSize.value)

    fun changeFontSize(delta: Float) { fontSize.value = clampFontSize(currentFontSize + delta) }

    fun toggleSoftWrap() { softWrap.value = !softWrap.value }

    companion object {
        const val DEFAULT_FONT_SIZE = 13f
        const val MIN_FONT_SIZE = 10f
        const val MAX_FONT_SIZE = 24f

        fun clampFontSize(sp: Float) = sp.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
    }
}
