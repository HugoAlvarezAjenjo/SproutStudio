package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PathEntry
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PathLister
import java.io.File

/** Lists folders on disk for `!include path` completion. Hidden entries are skipped. */
object IncludePaths : PathLister {
    override fun list(from: String?, dir: String): List<PathEntry>? {
        val self = File(from ?: return null).absoluteFile.normalize()
        val folder = File(dir).let { if (it.isAbsolute) it else File(self.parentFile ?: return null, dir) }
        val children = folder.absoluteFile.normalize().listFiles() ?: return null
        return children
            .filter { !it.name.startsWith(".") && it.absoluteFile.normalize() != self }
            .map { PathEntry(it.name, it.isDirectory) }
    }
}
