package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * Lists a folder for `!include path` completion. The domain never touches the disk: the app
 * hands one in, like [IncludeReader].
 */
fun interface PathLister {
    /**
     * What folder [dir] holds, as written in an `!include` of the file at absolute path [from]
     * (`""` is that file's own folder; null [from] is an unsaved text). The file itself is left
     * out. Null when the folder can't be listed.
     */
    fun list(from: String?, dir: String): List<PathEntry>?

    companion object {
        /** Lists nothing: no local path completion. */
        val NONE = PathLister { _, _ -> null }
    }
}
