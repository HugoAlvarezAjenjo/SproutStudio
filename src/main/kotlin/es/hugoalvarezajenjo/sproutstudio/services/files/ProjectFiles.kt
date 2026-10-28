package es.hugoalvarezajenjo.sproutstudio.services.files

import java.io.File
import java.nio.file.Files

/**
 * The project folder on disk: what the tree shows, and creating, moving and trashing in it.
 * Plain blocking file-system calls; it knows nothing of open tabs (the workspace keeps those in
 * step). Refusals are thrown as [FileOpException].
 *
 * [trash] moves a file to the system Trash (the platform's job); never a permanent delete.
 */
class ProjectFiles(private val trash: (File) -> Boolean) {

    /** Why [name] can't be used in [dir], or null. [except] is the file being renamed. */
    fun validate(dir: File, name: String, except: File? = null): FileError? {
        val n = name.trim()
        return when {
            n.isEmpty() -> FileError.EmptyName
            n == "." || n == ".." -> FileError.ReservedName
            n.any { it == '/' || it == '\\' || it == ':' || it.code < 32 } -> FileError.BadCharacters
            File(dir, n).let { it.exists() && it.absoluteFile != except?.absoluteFile && !sameIgnoringCase(it, except) } ->
                FileError.AlreadyExists(n)
            else -> null
        }
    }

    // macOS is case-insensitive: renaming a.puml to A.puml is a real rename, not a clash.
    private fun sameIgnoringCase(f: File, except: File?) =
        except != null && f.absoluteFile.parentFile == except.absoluteFile.parentFile && f.name.equals(except.name, ignoreCase = true)

    /** A diagram name gets `.puml` unless it already has a diagram extension. */
    fun diagramName(name: String): String {
        val n = name.trim()
        return if (n.substringAfterLast('.', "").lowercase() in DIAGRAM_EXTENSIONS) n else "$n.puml"
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

    fun createFolder(dir: File, name: String): File {
        validate(dir, name)?.let { throw FileOpException(it) }
        return File(dir, name.trim()).also { Files.createDirectories(it.toPath()) }
    }

    /** Where [target] would go for a rename to [newName]; refuses clashes and the project folder. */
    fun renameTarget(root: File?, target: File, newName: String): File {
        val from = target.absoluteFile
        val dir = from.parentFile ?: throw FileOpException(FileError.ProjectFolder)
        if (from == root?.absoluteFile) throw FileOpException(FileError.ProjectFolder)
        validate(dir, newName, except = from)?.let { throw FileOpException(it) }
        return File(dir, newName.trim()).absoluteFile
    }

    /** Where [target] would go when dropped on [destDir] (null: already there, nothing to do). */
    fun moveTarget(root: File?, target: File, destDir: File): File? {
        val from = target.absoluteFile
        val dest = destDir.absoluteFile
        if (from == root?.absoluteFile) throw FileOpException(FileError.ProjectFolder)
        if (!dest.isDirectory) throw FileOpException(FileError.NotAFolder(dest.name))
        if (dest == from || dest.startsWith(from)) throw FileOpException(FileError.IntoItself)
        if (dest == from.parentFile) return null
        val to = File(dest, from.name)
        if (to.exists()) throw FileOpException(FileError.AlreadyExistsIn(from.name, dest.name))
        return to
    }

    /** Whether dropping [target] on [destDir] would do something (for the drop highlight). */
    fun canMove(target: File, destDir: File): Boolean {
        val from = target.absoluteFile; val dest = destDir.absoluteFile
        return dest.isDirectory && dest != from && !dest.startsWith(from) && dest != from.parentFile && !File(dest, from.name).exists()
    }

    fun move(from: File, to: File) {
        runCatching { Files.move(from.toPath(), to.toPath()) }
            .onFailure { throw FileOpException(FileError.Failed(it.message ?: it.javaClass.simpleName)) }
    }

    /** A copy of the file [f] next to it, named like Finder does: "a-copy.puml", "a-copy-2.puml"… */
    fun duplicate(f: File): File {
        val from = f.absoluteFile
        val ext = from.extension.takeIf { it.isNotEmpty() }?.let { ".$it" } ?: ""
        val to = File(from.parentFile, freeName(from.parentFile, "${from.nameWithoutExtension}-copy", ext))
        runCatching { Files.copy(from.toPath(), to.toPath()) }
            .onFailure { throw FileOpException(FileError.Failed(it.message ?: it.javaClass.simpleName)) }
        return to
    }

    /** [dir] and every folder below it that the tree shows (the same ones [tree] walks into). */
    fun foldersUnder(dir: File): List<File> {
        val out = ArrayList<File>()
        fun walk(d: File, depth: Int) {
            if (depth > 8) return
            out += d
            d.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") && it.name !in IGNORED }.forEach { walk(it, depth + 1) }
        }
        walk(dir, 0)
        return out
    }

    fun moveToTrash(root: File?, target: File) {
        val f = target.absoluteFile
        if (f == root?.absoluteFile) throw FileOpException(FileError.ProjectFolder)
        if (!trash(f)) throw FileOpException(FileError.TrashFailed(f.name))
    }

    /** The visible tree under [root]: every folder (even empty), and diagrams; [expanded] folders open. */
    fun tree(root: File, expanded: Collection<File>): List<TreeNode> {
        val open = expanded.toSet()
        val out = ArrayList<TreeNode>()
        fun walk(dir: File, depth: Int) {
            val children = dir.listFiles().orEmpty()
                .filter { !it.name.startsWith(".") && it.name !in IGNORED }
                // A folder you just created must be visible; files: only diagrams, the app's subject.
                .filter { it.isDirectory || isDiagram(it) }
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            for (c in children) {
                out += TreeNode(c, depth, c.isDirectory)
                if (c.isDirectory && c in open) walk(c, depth + 1)
            }
        }
        walk(root, 0)
        return out
    }

    /** Every diagram under [root], expanded or not (the command palette's "go to file"). */
    fun allDiagrams(root: File, limit: Int = 2000): List<File> {
        val out = ArrayList<File>()
        fun walk(dir: File, depth: Int) {
            if (depth > 8 || out.size >= limit) return
            for (f in dir.listFiles().orEmpty().sortedBy { it.name.lowercase() }) {
                if (f.name.startsWith(".") || f.name in IGNORED) continue
                if (f.isDirectory) walk(f, depth + 1) else if (isDiagram(f) && out.size < limit) out += f.absoluteFile
            }
        }
        walk(root, 0)
        return out
    }

    companion object {
        val DIAGRAM_EXTENSIONS = setOf("puml", "plantuml", "pu", "iuml", "wsd")
        private val IGNORED = setOf("node_modules", "build", "target", "out", "dist", ".git", ".gradle")

        fun isDiagram(f: File) = f.isFile && f.extension.lowercase() in DIAGRAM_EXTENSIONS
    }
}
