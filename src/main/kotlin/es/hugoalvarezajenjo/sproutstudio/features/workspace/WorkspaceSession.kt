package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.domain.session.OpenTab
import es.hugoalvarezajenjo.sproutstudio.domain.session.ProjectSession
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.features.document.isDiagram
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.io.File

/**
 * One window's session: records its tabs as they change and reopens them with the project.
 * Untitled diagrams aren't recorded (there's no file to reopen).
 */
class WorkspaceSession(private val w: WorkspaceViewModel, private val settings: SessionSettings) {

    /** Tabs to open for [root] and which one shows, or null when there's nothing to reopen. */
    fun reopen(root: File): Pair<List<Document>, Int>? {
        val session = settings.sessionToReopen(root) ?: return null
        val wanted = session.tabs.getOrNull(session.active)
        val opened = session.tabs.mapNotNull { t ->
            resolve(root, t.path).takeIf { it.isFile && it.isDiagram() }?.let { f -> t to open(f, t) }
        }
        if (opened.isEmpty()) return null
        val active = opened.indexOfFirst { it.first == wanted }.coerceAtLeast(0)
        return opened.map { it.second } to active
    }

    /** Record the tabs now (the window is closing, the app quitting). */
    fun save() { current()?.let { (root, s) -> settings.save(root, s) } }

    /** Records the tabs shortly after they settle; runs until cancelled, once per window. */
    @OptIn(FlowPreview::class)
    suspend fun track(idleMs: Long = IDLE_MS) {
        snapshotFlow { current() }.debounce(idleMs).collect { it?.let { (root, s) -> settings.save(root, s) } }
    }

    private fun current(): Pair<File, ProjectSession>? {
        val root = w.root ?: return null
        // A newline in a file name would break the one-tab-per-line format: such a tab isn't kept.
        val saved = w.docs.mapNotNull { d -> d.file?.takeIf { '\n' !in it.path }?.let { d to it } }
        val tabs = saved.map { (d, f) -> OpenTab(relative(root, f), d.value.selection.start, d.value.selection.end) }
        val active = saved.indexOfFirst { it.first === w.active }.coerceAtLeast(0)
        return root to ProjectSession(tabs, active)
    }

    private fun open(f: File, t: OpenTab): Document {
        val d = Document.open(f, w.deps.documents)
        val n = d.text.length
        val sel = TextRange(t.selectionStart.coerceIn(0, n), t.selectionEnd.coerceIn(0, n))
        d.value = d.value.copy(selection = sel) // a caret move: not an undo step
        d.jumpTo(sel) // scrolled into view once the editor shows the tab
        return d
    }

    private companion object {
        const val IDLE_MS = 500L

        /** A file outside the project (an `!include ../x`) is kept by its absolute path. */
        fun relative(root: File, f: File): String =
            runCatching { f.absoluteFile.normalize().toRelativeString(root.absoluteFile.normalize()) }.getOrNull()
                ?: f.absolutePath

        fun resolve(root: File, path: String): File =
            File(path).let { if (it.isAbsolute) it else File(root, path) }.absoluteFile.normalize()
    }
}
