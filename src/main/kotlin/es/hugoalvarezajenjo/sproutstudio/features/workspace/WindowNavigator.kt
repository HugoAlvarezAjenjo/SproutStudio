package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.runtime.getValue

import java.io.File

/** Opening and showing windows: implemented by the app, which owns the window list. */
interface WindowNavigator {
    /** A diagram: the quick preview (a folder opens as a project). */
    fun openFile(file: File)
    fun openFolder(dir: File)
    fun newProjectWindow()
    /** Open [file]'s folder as a project, with [file] in a tab. */
    fun editFile(file: File)
    /** "Compare Diagram with HEAD" ([textFirst]: the text diff tab first); one window per file. */
    fun compareWithHead(project: WorkspaceViewModel, file: File, textFirst: Boolean = false)
    fun showHistory(project: WorkspaceViewModel, file: File)
}
