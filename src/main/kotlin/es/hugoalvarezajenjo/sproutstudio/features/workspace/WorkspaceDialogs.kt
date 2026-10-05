package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.services.files.ProjectFiles
import java.io.File

/*
 * The native dialogs a window opens. They live with the views (they carry the dialog titles);
 * the platform draws them.
 */

/** "Open Diagram…": picks a file and shows it in a quick preview. */
fun WorkspaceDeps.openDiagramDialog() {
    platform.chooseFile("Open a diagram", ProjectFiles.DIAGRAM_EXTENSIONS)?.let(windows::openFile)
}

/** "Open Folder…": picks a folder and opens it as a project. */
fun WorkspaceDeps.openFolderDialog() {
    chooseFolder()?.let(windows::openFolder)
}

fun WorkspaceDeps.chooseFolder(): File? = platform.chooseFolder("Open a project folder")

fun WorkspaceViewModel.openDiagramDialog() = deps.openDiagramDialog()

fun WorkspaceViewModel.chooseFolder(): File? = deps.chooseFolder()

/** Save dialog for a diagram; adds `.puml` when the name has no diagram extension. */
fun WorkspaceViewModel.chooseSaveTarget(suggested: String, dir: File?): File? {
    val f = deps.platform.chooseSaveTarget("Save diagram", suggested, dir) ?: return null
    return if (f.extension.lowercase() in ProjectFiles.DIAGRAM_EXTENSIONS) f else File(f.parentFile, "${f.name}.puml")
}
