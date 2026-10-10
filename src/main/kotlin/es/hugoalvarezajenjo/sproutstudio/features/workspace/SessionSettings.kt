package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.core.settings.SettingsStore
import es.hugoalvarezajenjo.sproutstudio.core.settings.boolean
import es.hugoalvarezajenjo.sproutstudio.core.settings.stringList
import es.hugoalvarezajenjo.sproutstudio.domain.session.ProjectSession
import java.io.File
import java.security.MessageDigest

/**
 * "Restore session" (Settings → General): the projects open when the app last quit, and each
 * project's tabs. Sessions are always recorded; [restore] only decides whether they're reopened,
 * so switching it back on picks up where you are now, not an old session.
 */
class SessionSettings(private val store: SettingsStore) {
    val restore = store.boolean("restoreSession", default = true)
    private val openProjects = store.stringList("openProjects")

    /** The folders to reopen on a plain launch: none when [restore] is off, only ones that still exist. */
    fun projectsToReopen(): List<File> =
        if (!restore.value) emptyList() else openProjects.value.map(::File).filter { it.isDirectory }

    fun rememberOpenProjects(roots: List<File>) {
        openProjects.value = roots.map { it.absolutePath }.distinct()
    }

    /** [root]'s tabs from last time; null when [restore] is off or there's nothing usable. */
    fun sessionToReopen(root: File): ProjectSession? =
        if (!restore.value) null else store.getString(key(root))?.let(ProjectSession::decode)

    fun save(root: File, session: ProjectSession) {
        var s = session
        var text = s.encode()
        // Java preferences cap a value's length; lose the last tabs rather than the whole session.
        while (text.length > MAX_VALUE && s.tabs.isNotEmpty()) { s = s.dropLast(); text = s.encode() }
        store.putString(key(root), text)
    }

    private companion object {
        /** Under java.util.prefs.Preferences.MAX_VALUE_LENGTH (8192). */
        const val MAX_VALUE = 8000

        /** Preferences keys are capped at 80 characters, so a path is hashed, not used as is. */
        fun key(root: File): String {
            val path = root.absoluteFile.normalize().path
            val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray())
            return "session." + digest.take(16).joinToString("") { "%02x".format(it) }
        }
    }
}
