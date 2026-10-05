package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.boolean
import es.hugoalvarezajenjo.sproutstudio.core.settings.enum
import es.hugoalvarezajenjo.sproutstudio.core.settings.float
import es.hugoalvarezajenjo.sproutstudio.model.EditorLayout

/** The window layout the user tuned, remembered across launches. */
class LayoutSettings(store: SettingsStore) {
    val sidebarVisible = store.boolean("sidebarVisible", default = true)
    /** Project panel width in dp. Read through [clampWidth]: an old value may be out of range. */
    val sidebarWidth = store.float("sidebarWidth", default = 240f)
    val editorLayout = store.enum("editorLayout", default = EditorLayout.SPLIT)
    /** Whether the Problems panel at the bottom is expanded. The header bar always shows. */
    val problemsExpanded = store.boolean("problemsExpanded", default = false)

    companion object {
        const val SIDEBAR_MIN = 160f
        const val SIDEBAR_MAX = 560f

        fun clampWidth(dp: Float) = dp.coerceIn(SIDEBAR_MIN, SIDEBAR_MAX)
    }
}
