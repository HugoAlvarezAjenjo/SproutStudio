package es.hugoalvarezajenjo.sproutstudio.services.git

import java.io.File

/** One changed file. [path] is relative to the repository's work tree, with '/' separators. */
data class FileChange(val path: String, val type: ChangeType, val file: File)
