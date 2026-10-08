package es.hugoalvarezajenjo.sproutstudio.services.git

/** A status snapshot: the changes under the project folder plus what .gitignore hides there. */
data class GitStatus(
    val branch: String?,
    /** Commit id of HEAD, or null before the first commit. */
    val head: String?,
    val changes: List<FileChange>,
    /** Ignored files and folders (folders as a whole when everything in them is ignored). */
    val ignored: Set<IgnoredPath>,
) {
    val byPath: Map<String, FileChange> by lazy { changes.associateBy { it.path } }
    /** Folders (relative paths) that contain a tracked change, for the tree colours. */
    val changedDirs: Set<String> by lazy {
        changes.filter { it.type != ChangeType.UNVERSIONED }
            .flatMap { c -> generateSequence(c.path.substringBeforeLast('/', "")) { p -> p.substringBeforeLast('/', "").takeIf { p.isNotEmpty() } }.filter { it.isNotEmpty() } }
            .toSet()
    }
    /** Ignored paths, relative ("out" covers everything under it). */
    val ignoredPaths: Set<String> by lazy { ignored.map { it.path }.toSet() }

    companion object {
        val Empty = GitStatus(null, null, emptyList(), emptySet())
    }
}
