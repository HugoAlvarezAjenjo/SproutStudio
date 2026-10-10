package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * "Rename Symbol" (⇧F6) on plain text: which occurrences change, and the change itself. Uses
 * [SymbolIndex], so a rename touches exactly what Find Usages lists (never message labels,
 * comments or a quoted display name).
 */
object SymbolRename {

    /**
     * The rename of the symbol at [offset], or null when there's none. A participant, class...
     * changes within its diagram block. A procedure, function, macro or `$variable` changes in
     * this text and in every local file it includes ([file] is this text's absolute path,
     * [includes] reads them), wherever it's declared among them.
     *
     * [project] lists the other diagrams (absolute paths) that may use it too: each one that
     * reaches the declaring file through its own includes changes as well, with the files it
     * includes. Without it, renaming in a shared `lib.puml` would break the diagrams using it.
     */
    fun at(
        text: String,
        offset: Int,
        file: String? = null,
        includes: IncludeReader = IncludeReader.NONE,
        project: List<String> = emptyList(),
    ): SymbolRenaming? {
        val index = SymbolIndex.of(text)
        val at = index.occurrenceAt(offset)
        if (at != null && at.scope != SymbolIndex.GLOBAL) return local(index, at)
        val name = at?.name ?: index.unknownAt(offset)?.name ?: return null
        return global(index, name, file, includes, project)
    }

    private fun local(index: SymbolIndex, at: Occurrence): SymbolRenaming {
        val ranges = index.usagesOf(at).map { it.range }
        val taken = index.occurrences.filter { it.scope == at.scope || it.scope == SymbolIndex.GLOBAL }.mapTo(HashSet()) { it.name }
        return SymbolRenaming(at.name, listOf(RenameEdit(null, index.text, ranges)), taken - at.name)
    }

    /** Null when no file declares [name]: an undeclared word isn't a symbol to rename. */
    private fun global(index: SymbolIndex, name: String, file: String?, includes: IncludeReader, project: List<String>): SymbolRenaming? {
        // Keyed by path; null is the text being edited.
        val texts = LinkedHashMap<String?, SymbolIndex>()
        texts[null] = index
        IncludeGraph.reachable(file, index.includes, includes).forEach { texts.putIfAbsent(it.path, it.index) }
        val declaring = texts.filterValues { it.globalDeclaration(name) != null }.keys.mapTo(HashSet()) { it ?: file }
        if (declaring.isEmpty()) return null
        for ((path, i) in includers(declaring, file, includes, project)) texts.putIfAbsent(path, i)
        val edits = texts.mapNotNull { (path, i) ->
            val ranges = (i.occurrences.filter { it.scope == SymbolIndex.GLOBAL } + i.unknown)
                .filter { it.name == name }.map { it.range }.distinct().sortedBy { it.first }
            if (ranges.isEmpty()) null else RenameEdit(path, i.text, ranges)
        }
        val taken = texts.flatMapTo(HashSet()) { (_, i) -> (i.occurrences + i.unknown).map { it.name } }
        return SymbolRenaming(name, edits, taken - name)
    }

    /**
     * The diagrams of [project] (but [file] itself) that reach one of the [declaring] files through
     * their includes, each followed by the files it includes: in that diagram they all see the
     * same declaration, whether or not they include it themselves.
     */
    private fun includers(declaring: Set<String?>, file: String?, reader: IncludeReader, project: List<String>): Sequence<Pair<String, SymbolIndex>> = sequence {
        for (path in project) {
            val f = reader.read(null, path) ?: continue
            if (f.path == file) continue
            val tree = IncludeGraph.reachable(f.path, f.index.includes, reader).toList()
            if (f.path !in declaring && tree.none { it.path in declaring }) continue
            yield(f.path to f.index)
            for (t in tree) if (t.path != file) yield(t.path to t.index)
        }
    }

    /**
     * [v] with each of [ranges] (from a [RenameEdit] of its text) replaced by [newName]. The
     * caret and selection stay on the same code; one inside a renamed name stays at the same
     * place in the new one (clamped to it).
     */
    fun apply(v: TextFieldValue, ranges: List<IntRange>, newName: String): TextFieldValue {
        val sorted = ranges.sortedBy { it.first }
        val out = StringBuilder(v.text.length)
        var at = 0
        for (r in sorted) {
            out.append(v.text, at, r.first).append(newName)
            at = r.last + 1
        }
        out.append(v.text, at, v.text.length)

        fun map(offset: Int): Int {
            var shift = 0
            for (r in sorted) {
                if (offset <= r.first) break
                val start = r.first + shift
                if (offset == r.last + 1) return start + newName.length
                if (offset <= r.last) return start + minOf(offset - r.first, newName.length)
                shift += newName.length - (r.last - r.first + 1)
            }
            return offset + shift
        }
        return TextFieldValue(out.toString(), TextRange(map(v.selection.start), map(v.selection.end)))
    }
}
