package es.hugoalvarezajenjo.sproutstudio.features.explorer

import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Template
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.services.files.FileError
import es.hugoalvarezajenjo.sproutstudio.services.files.FileOpException
import java.io.File

/**
 * Create / rename / move / delete in the project tree, keeping the open tabs in step (renamed
 * files follow, deleted ones close). Disk work goes through the project-files service and the
 * document store; refusals come back as a [FileError]. Deleting moves to the Trash, never a
 * permanent delete.
 */

/** The reason inside a failed file operation (anything unexpected becomes [FileError.Failed]). */
val Throwable.fileError: FileError
    get() = (this as? FileOpException)?.error ?: FileError.Failed(message ?: javaClass.simpleName)

private val WorkspaceViewModel.store get() = deps.documents
private val WorkspaceViewModel.files get() = deps.files

/** A new diagram file: empty, or holding [template] with the caret on its `$0` marker. */
suspend fun WorkspaceViewModel.newDiagramIn(dir: File, name: String, template: Template? = null): Result<File> = runCatching {
    if (name.isBlank()) throw FileOpException(FileError.EmptyName)
    val n = files.diagramName(name)
    files.validate(dir, n)?.let { throw FileOpException(it) }
    val f = File(dir, n)
    store.write(f, template?.previewSource ?: "")
    expandTo(dir)
    refreshTree()
    open(f)
    template?.body?.indexOf("\$0")?.takeIf { it >= 0 }?.let { at ->
        active?.let { d -> d.value = d.value.copy(selection = TextRange(at)) }
    }
    f
}

fun WorkspaceViewModel.newFolderIn(dir: File, name: String): Result<File> = runCatching {
    val f = files.createFolder(dir, name)
    expandTo(dir)
    if (f !in expanded) expanded.add(f)
    refreshTree()
    f
}

suspend fun WorkspaceViewModel.rename(target: File, newName: String): Result<File> = runCatching {
    val from = target.absoluteFile
    val to = files.renameTarget(root, from, newName)
    if (to == from) from else relocate(from, to)
}

/**
 * Drag & drop: moves [target] (a file or a folder) into [destDir], keeping its name.
 * Refuses a move onto itself, into its own subfolder, or over an existing name.
 */
suspend fun WorkspaceViewModel.move(target: File, destDir: File): Result<File> = runCatching {
    val from = target.absoluteFile
    val dest = destDir.absoluteFile
    val to = files.moveTarget(root, from, dest) ?: return@runCatching from
    relocate(from, to).also { expandTo(dest); if (dest != root?.absoluteFile && dest !in expanded) expanded.add(dest); refreshTree() }
}

/** Whether dropping [target] on [destDir] would do something (for the drop highlight). */
fun WorkspaceViewModel.canMove(target: File, destDir: File): Boolean = files.canMove(target, destDir)

/** The shared part of rename and move: moves on disk, then tabs and expanded folders follow. */
private suspend fun WorkspaceViewModel.relocate(from: File, to: File): File {
    // Unsaved edits go to the old path first, so the move carries them.
    deps.autoSaver.flush(docs.toList())
    // No save may land on either path while it moves.
    store.exclusive(from, to) { files.move(from, to) }
    for (d in docs) d.file?.absoluteFile?.let { f -> moved(f, from, to)?.let { d.file = it } }
    val exp = expanded.toList(); expanded.clear(); exp.forEach { e -> expanded.add(moved(e.absoluteFile, from, to) ?: e) }
    refreshTree()
    return to
}

/** Moves [target] to the Trash and closes its tabs (or every tab under it, for a folder). */
fun WorkspaceViewModel.delete(target: File): Result<Unit> = runCatching {
    val f = target.absoluteFile
    if (f == root?.absoluteFile) throw FileOpException(FileError.ProjectFolder)
    val inside = docs.filter { d -> d.file?.absoluteFile?.let { it == f || it.startsWith(f) } == true }
    // Close first: autosave must not write a deleted file back.
    inside.forEach { closeDoc(it) }
    files.moveToTrash(root, f)
    expanded.removeAll { it.absoluteFile == f || it.absoluteFile.startsWith(f) }
    refreshTree()
}

/** [f] after [from] became [to]: itself, or a file inside a renamed folder. Null if unaffected. */
private fun moved(f: File, from: File, to: File): File? = when {
    f == from -> to
    f.startsWith(from) -> File(to, f.relativeTo(from).path)
    else -> null
}

/** Expands every folder from the root down to [dir], so a new item is visible. */
private fun WorkspaceViewModel.expandTo(dir: File) {
    val r = root?.absoluteFile ?: return
    var d: File? = dir.absoluteFile
    val chain = ArrayList<File>()
    while (d != null && d != r && d.startsWith(r)) { chain += d; d = d.parentFile }
    chain.forEach { if (it !in expanded) expanded.add(it) }
    rootCollapsed = false
}
