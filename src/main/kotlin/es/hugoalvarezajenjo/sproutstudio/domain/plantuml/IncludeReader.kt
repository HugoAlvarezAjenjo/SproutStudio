package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * Reads the file an `!include` points at. The domain never touches the disk: the app hands one
 * in (and may answer with the text of an open tab instead of what is saved).
 */
fun interface IncludeReader {
    /**
     * The file [path] (as written in an `!include` of the file at absolute path [from]; null for
     * an unsaved text) names, or null when there is no such file.
     */
    fun read(from: String?, path: String): SourceFile?

    companion object {
        /** Follows no include: navigation stays within the text. */
        val NONE = IncludeReader { _, _ -> null }
    }
}
