package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.IncludeReader
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SourceFile
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads `!include`d files for navigation. A file open in a tab is read from [openText] (so a
 * jump lands where the tab's unsaved text has it); the rest from disk. Parsed files are kept
 * while their text (or, on disk, modification time) stays the same: ⌘-hover asks on every move.
 */
class IncludeFiles(private val openText: (File) -> String? = { null }) : IncludeReader {
    private class Cached(val file: SourceFile, val modified: Long?)

    private val cache = ConcurrentHashMap<String, Cached>()

    override fun read(from: String?, path: String): SourceFile? {
        val f = resolve(from, path) ?: return null
        val key = f.path
        val live = openText(f)
        val cached = cache[key]
        if (live != null) {
            if (cached != null && cached.modified == null && cached.file.text == live) return cached.file
            return SourceFile(key, live).also { cache[key] = Cached(it, null) }
        }
        val modified = f.lastModified()
        if (cached != null && cached.modified == modified) return cached.file
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        return SourceFile(key, text).also { cache[key] = Cached(it, modified) }
    }

    companion object {
        /** Disk only: for an editor outside a project window (and tests). */
        val DISK = IncludeFiles()

        /** The file [path] of an `!include` in the file at [from] names (absolute, normalized), if it exists. */
        fun resolve(from: String?, path: String): File? {
            val f = File(path).let { if (it.isAbsolute) it else File(File(from ?: return null).absoluteFile.parentFile ?: return null, path) }
            return f.absoluteFile.normalize().takeIf { it.isFile }
        }
    }
}
