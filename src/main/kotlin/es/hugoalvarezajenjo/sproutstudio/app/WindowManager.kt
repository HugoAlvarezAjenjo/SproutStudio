package es.hugoalvarezajenjo.sproutstudio.app

import androidx.compose.runtime.mutableStateListOf
import es.hugoalvarezajenjo.sproutstudio.domain.git.DiffSide
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WindowNavigator
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import java.io.File

/** The app's windows. Main composes one window per entry; screens open more through [WindowNavigator]. */
class WindowManager(private val newWorkspace: () -> WorkspaceViewModel) : WindowNavigator {
    val windows = mutableStateListOf<AppWindow>()

    /**
     * Welcome window for a plain launch. Compose quits if its first composition has no window,
     * so it is composed at once but kept HIDDEN: a Finder double-click on a cold start reaches us
     * as an Apple "open file" event that can arrive seconds after main(). If it does, the hidden
     * welcome is discarded and only the preview ever appears; otherwise [revealBootWelcome] shows it.
     */
    private var bootWelcome: AppWindow.Project? = null

    fun openBootWelcome() {
        val w = AppWindow.Project(newWorkspace(), revealed = false)
        bootWelcome = w
        windows += w
    }

    /** Called once the app has settled with no file to open: show the welcome after all. */
    fun revealBootWelcome() {
        bootWelcome?.revealed = true
    }

    /** A file arrived: the boot welcome isn't wanted, unless the user already started using it. */
    private fun dropBootWelcome() {
        val w = bootWelcome ?: return
        bootWelcome = null
        val untouched = w.state.root == null && w.state.docs.isEmpty()
        if (untouched) close(w)
    }

    /** Double-click / single file: open the lightweight preview, or focus an open tab. */
    override fun openFile(file: File) {
        val f = file.absoluteFile
        when {
            f.isDirectory -> openFolder(f)
            windows.any { it is AppWindow.QuickPreview && it.file == f } -> Unit
            else -> { windows += AppWindow.QuickPreview(f); dropBootWelcome() }
        }
    }

    override fun openFolder(dir: File) {
        val d = dir.absoluteFile
        windows.filterIsInstance<AppWindow.Project>().firstOrNull { it.state.root == d }?.let { return }
        // Reuse an empty welcome window if there is one.
        val empty = windows.filterIsInstance<AppWindow.Project>().firstOrNull { it.state.root == null && it.state.docs.isEmpty() }
        if (empty != null) {
            empty.state.openRoot(d)
            empty.revealed = true
        } else {
            windows += AppWindow.Project(newWorkspace().apply { openRoot(d) })
        }
    }

    override fun newProjectWindow() {
        windows += AppWindow.Project(newWorkspace())
    }

    override fun editFile(file: File) {
        val dir = file.absoluteFile.parentFile
        val existing = windows.filterIsInstance<AppWindow.Project>().firstOrNull { it.state.root == dir }
        val target = existing?.state ?: newWorkspace().apply { openRoot(dir) }.also { windows += AppWindow.Project(it) }
        target.open(file)
        // The quick preview of that file isn't needed any more.
        windows.removeAll { it is AppWindow.QuickPreview && it.file == file.absoluteFile }
    }

    override fun compareWithHead(project: WorkspaceViewModel, file: File, textFirst: Boolean) {
        val f = file.absoluteFile
        if (windows.any { it is AppWindow.DiagramDiff && it.file == f && it.left == DiffSide.Head }) return
        windows += AppWindow.DiagramDiff(project, f, textFirst = textFirst)
    }

    override fun showHistory(project: WorkspaceViewModel, file: File) {
        val f = file.absoluteFile
        if (windows.any { it is AppWindow.History && it.file == f }) return
        windows += AppWindow.History(project, f)
    }

    override fun openSettings() {
        if (windows.none { it is AppWindow.Settings }) windows += AppWindow.Settings()
    }

    /** Closing a project window also closes its ViewModel (git, running work). */
    fun close(w: AppWindow) {
        windows.remove(w)
        if (w is AppWindow.Project) w.state.close()
    }
}
