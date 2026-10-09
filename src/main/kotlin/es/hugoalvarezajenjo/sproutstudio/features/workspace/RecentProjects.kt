package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.stringList
import java.io.File

/** Recently opened project folders, most recent first. */
class RecentProjects(store: SettingsStore) {
    private val paths = store.stringList("recentFolders")

    /** Only folders that still exist. */
    fun folders(): List<File> = paths.value.map(::File).filter { it.isDirectory }

    fun add(dir: File) {
        paths.value = (listOf(dir.absolutePath) + paths.value).distinct().take(MAX)
    }

    /** Drop a folder from the list (e.g. the user removed it from the welcome screen). */
    fun remove(dir: File) {
        paths.value = paths.value.filter { it != dir.absolutePath }
    }

    private companion object { const val MAX = 8 }
}
