package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/** The local files a text reaches through its `!include`s: what navigation and rename look into. */
internal object IncludeGraph {
    /** A guard against huge include trees; a hand-written project never gets near it. */
    private const val MAX_FILES = 64

    /**
     * Every local file [includes] (of the text at absolute path [file]; null while unsaved) reach,
     * directly or through other includes, read by [reader]. Depth-first in include order, each
     * file once (so cycles end), lazily: a caller that stops early reads no further.
     */
    fun reachable(file: String?, includes: List<IncludeRef>, reader: IncludeReader): Sequence<SourceFile> = sequence {
        if (reader === IncludeReader.NONE) return@sequence
        val seen = HashSet<String>()
        file?.let(seen::add)
        val pending = ArrayDeque<Pair<String?, IncludeRef>>()
        includes.forEach { pending.addLast(file to it) }
        while (pending.isNotEmpty() && seen.size < MAX_FILES) {
            val (from, inc) = pending.removeFirst()
            if (!inc.isLocal) continue
            val f = reader.read(from, inc.path) ?: continue
            if (!seen.add(f.path)) continue
            yield(f)
            // Depth-first: this file's includes come before its siblings'.
            f.index.includes.asReversed().forEach { pending.addFirst(f.path to it) }
        }
    }
}
