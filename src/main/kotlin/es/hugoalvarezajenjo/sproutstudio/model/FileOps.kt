package es.hugoalvarezajenjo.sproutstudio.model

import java.io.File
import java.nio.file.Files

/**
 * Create / rename / delete in the project tree. Every operation keeps the open tabs in step
 * (renamed files follow, deleted ones close) and returns an error message, or null on success.
 * Deleting moves to the system Trash, never a permanent delete.
 */
object FileOps {

    /** Moves [f] to the Trash; false if the platform has no Trash. Swappable for tests. */
    var trash: (File) -> Boolean = { f ->
        runCatching {
            val d = java.awt.Desktop.getDesktop()
            d.isSupported(java.awt.Desktop.Action.MOVE_TO_TRASH) && d.moveToTrash(f)
        }.getOrDefault(false)
    }

    /** Why [name] can't be used in [dir] (empty, bad characters, already there), or null. */
    fun validate(dir: File, name: String, except: File? = null): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Enter a name"
            n == "." || n == ".." -> "That name is reserved"
            n.any { it == '/' || it == '\\' || it == ':' || it.code < 32 } -> "A name can't contain / \\ or :"
            File(dir, n).let { it.exists() && it.absoluteFile != except?.absoluteFile && !sameIgnoringCase(it, except) } ->
                "“$n” already exists here"
            else -> null
        }
    }

    // macOS is case-insensitive: renaming a.puml to A.puml is a real rename, not a clash.
    private fun sameIgnoringCase(f: File, except: File?) =
        except != null && f.absoluteFile.parentFile == except.absoluteFile.parentFile && f.name.equals(except.name, ignoreCase = true)

    /** A diagram name gets `.puml` unless it already has a diagram extension. */
    fun diagramName(name: String): String {
        val n = name.trim()
        return if (n.substringAfterLast('.', "").lowercase() in DiagramExtensions) n else "$n.puml"
    }

    /** The first free "new-diagram.puml", "new-diagram-2.puml"… in [dir]. */
    fun freeName(dir: File, base: String, ext: String = ""): String {
        var n = 1
        while (true) {
            val name = if (n == 1) "$base$ext" else "$base-$n$ext"
            if (!File(dir, name).exists()) return name
            n++
        }
    }
}

/** A new diagram file: empty, or holding [template] with the caret on its `$0` marker. */
fun ProjectState.newDiagramIn(dir: File, name: String, template: es.hugoalvarezajenjo.sproutstudio.lang.Template? = null): Result<File> = runCatching {
    if (name.isBlank()) error("Enter a name")
    val n = FileOps.diagramName(name)
    FileOps.validate(dir, n)?.let { error(it) }
    val f = File(dir, n)
    Document.writeAtomically(f, template?.previewSource ?: "")
    expandTo(dir)
    refreshTree()
    open(f)
    template?.body?.indexOf("\$0")?.takeIf { it >= 0 }?.let { at ->
        active?.let { d -> d.value = d.value.copy(selection = androidx.compose.ui.text.TextRange(at)) }
    }
    f
}

fun ProjectState.newFolderIn(dir: File, name: String): Result<File> = runCatching {
    FileOps.validate(dir, name)?.let { error(it) }
    val f = File(dir, name.trim())
    Files.createDirectories(f.toPath())
    expandTo(dir)
    if (f !in expanded) expanded.add(f)
    refreshTree()
    f
}

fun ProjectState.rename(target: File, newName: String): Result<File> = runCatching {
    val from = target.absoluteFile
    val dir = from.parentFile ?: error("Can't rename this")
    require(from != root?.absoluteFile) { "The project folder itself can't be renamed here" }
    FileOps.validate(dir, newName, except = from)?.let { error(it) }
    val to = File(dir, newName.trim()).absoluteFile
    if (to == from) return@runCatching from
    relocate(from, to)
}

/**
 * Drag & drop: moves [target] (a file or a folder) into [destDir], keeping its name.
 * Refuses a move onto itself, into its own subfolder, or over an existing name.
 */
fun ProjectState.move(target: File, destDir: File): Result<File> = runCatching {
    val from = target.absoluteFile
    val dest = destDir.absoluteFile
    val r = root?.absoluteFile
    require(from != r) { "The project folder itself can't be moved" }
    require(dest.isDirectory) { "“${dest.name}” is not a folder" }
    require(dest != from && !dest.startsWith(from)) { "A folder can't be moved into itself" }
    if (dest == from.parentFile) return@runCatching from
    val to = File(dest, from.name)
    require(!to.exists()) { "“${from.name}” already exists in ${dest.name}" }
    relocate(from, to).also { expandTo(dest); if (dest != r && dest !in expanded) expanded.add(dest); refreshTree() }
}

/** Whether dropping [target] on [destDir] would do something (for the drop highlight). */
fun canMove(target: File, destDir: File): Boolean {
    val from = target.absoluteFile; val dest = destDir.absoluteFile
    return dest.isDirectory && dest != from && !dest.startsWith(from) && dest != from.parentFile && !File(dest, from.name).exists()
}

/** The shared part of rename and move: moves on disk, then tabs and expanded folders follow. */
private fun ProjectState.relocate(from: File, to: File): File {
    // Unsaved edits go to the old path first, so the move carries them.
    AutoSave.flush(docs.toList())
    Files.move(from.toPath(), to.toPath())
    for (d in docs) d.file?.absoluteFile?.let { f -> moved(f, from, to)?.let { d.file = it } }
    val exp = expanded.toList(); expanded.clear(); exp.forEach { e -> expanded.add(moved(e.absoluteFile, from, to) ?: e) }
    refreshTree()
    return to
}

/** Moves [target] to the Trash and closes its tabs (or every tab under it, for a folder). */
fun ProjectState.delete(target: File): Result<Unit> = runCatching {
    val f = target.absoluteFile
    require(f != root?.absoluteFile) { "The project folder itself can't be deleted here" }
    val inside = docs.filter { d -> d.file?.absoluteFile?.let { it == f || it.startsWith(f) } == true }
    // Close first: autosave must not write a deleted file back.
    inside.forEach { closeDoc(it) }
    check(FileOps.trash(f)) { "Couldn't move “${f.name}” to the Trash" }
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
private fun ProjectState.expandTo(dir: File) {
    val r = root?.absoluteFile ?: return
    var d: File? = dir.absoluteFile
    val chain = ArrayList<File>()
    while (d != null && d != r && d.startsWith(r)) { chain += d; d = d.parentFile }
    chain.forEach { if (it !in expanded) expanded.add(it) }
    rootCollapsed = false
}

/** A file-tree operation waiting for the user's input (a name, or a confirmation). */
sealed class FileAction {
    data class NewDiagram(val dir: File) : FileAction()
    data class NewFolder(val dir: File) : FileAction()
    data class Rename(val target: File) : FileAction()
    data class Delete(val target: File) : FileAction()
}
