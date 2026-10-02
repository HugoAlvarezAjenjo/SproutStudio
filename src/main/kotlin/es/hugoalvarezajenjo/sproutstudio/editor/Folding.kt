package es.hugoalvarezajenjo.sproutstudio.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle

/**
 * A foldable block. Folding hides [hideStart, hideEnd): from the end of the opening line up to
 * the closing keyword, so `class Pizza {…}` and `alt ok … end` stay on one readable line.
 */
data class FoldRegion(
    val kind: Kind,
    /** 0-based lines of the opener and the closer. */
    val startLine: Int,
    val endLine: Int,
    val hideStart: Int,
    val hideEnd: Int,
) {
    enum class Kind { DIAGRAM, BRACES, BLOCK, NOTE, COMMENT }
    operator fun contains(offset: Int) = offset in (hideStart + 1) until hideEnd
}

object Folding {
    const val PLACEHOLDER = " … "

    private enum class Open { DIAGRAM, BRACE, END, BOX, NOTE, REF, LEGEND, HEADER, FOOTER, IF, WHILE, REPEAT, FORK, SPLIT, SWITCH, COMMENT }

    private val endBlocks = setOf("alt", "opt", "loop", "par", "par2", "break", "critical", "group")

    /** Every foldable region, ordered by start; regions with nothing inside are skipped. */
    fun regions(text: String): List<FoldRegion> {
        val starts = lineStarts(text)
        val out = ArrayList<FoldRegion>()
        val stack = ArrayList<Pair<Open, Int>>() // (kind, start line)

        fun close(kind: Open, endLine: Int) {
            val i = stack.indexOfLast { it.first == kind }
            if (i < 0) return // stray closer: ignore rather than unbalance everything
            val start = stack[i].second
            while (stack.size > i) stack.removeAt(stack.lastIndex)
            if (endLine - start < 2) return // nothing between opener and closer
            val hideStart = lineEnd(text, starts, start)
            val endLineStart = starts[endLine]
            var hideEnd = endLineStart
            while (hideEnd < text.length && (text[hideEnd] == ' ' || text[hideEnd] == '\t')) hideEnd++
            val k = when (kind) {
                Open.DIAGRAM -> FoldRegion.Kind.DIAGRAM
                Open.BRACE -> FoldRegion.Kind.BRACES
                Open.NOTE -> FoldRegion.Kind.NOTE
                Open.COMMENT -> FoldRegion.Kind.COMMENT
                else -> FoldRegion.Kind.BLOCK
            }
            out += FoldRegion(k, start, endLine, hideStart, hideEnd)
        }

        var inComment = false
        for (ln in starts.indices) {
            val raw = text.substring(starts[ln], lineEnd(text, starts, ln))
            val t = raw.trim()
            val lower = t.lowercase()
            // ── block comments /' … '/ ──
            if (inComment) {
                if (t.endsWith("'/")) { inComment = false; close(Open.COMMENT, ln) }
                continue
            }
            if (t.startsWith("/'")) {
                if (!t.endsWith("'/") || t.length < 4) { inComment = true; stack += Open.COMMENT to ln }
                continue
            }
            if (t.isEmpty() || t.startsWith("'")) continue
            val word = lower.takeWhile { !it.isWhitespace() && it != '(' && it != '{' && it != ':' }

            when {
                lower.startsWith("@start") -> stack += Open.DIAGRAM to ln
                lower.startsWith("@end") -> close(Open.DIAGRAM, ln)
                t.startsWith("}") -> close(Open.BRACE, ln)
                lower == "end" || (word == "end" && lower.removePrefix("end").isBlank()) -> close(Open.END, ln)
                lower.startsWith("end box") || lower == "endbox" -> close(Open.BOX, ln)
                Regex("^end\\s*[hr]?note").containsMatchIn(lower) -> close(Open.NOTE, ln)
                lower.startsWith("end ref") || lower == "endref" -> close(Open.REF, ln)
                lower.startsWith("endlegend") || lower.startsWith("end legend") -> close(Open.LEGEND, ln)
                lower.startsWith("endheader") || lower.startsWith("end header") -> close(Open.HEADER, ln)
                lower.startsWith("endfooter") || lower.startsWith("end footer") -> close(Open.FOOTER, ln)
                lower.startsWith("endif") || lower.startsWith("end if") -> close(Open.IF, ln)
                lower.startsWith("endwhile") || lower.startsWith("end while") -> close(Open.WHILE, ln)
                lower.startsWith("repeat while") || lower.startsWith("repeatwhile") -> close(Open.REPEAT, ln)
                lower.startsWith("end fork") || lower.startsWith("endfork") || lower.startsWith("end merge") -> close(Open.FORK, ln)
                lower.startsWith("end split") || lower.startsWith("endsplit") -> close(Open.SPLIT, ln)
                lower.startsWith("endswitch") || lower.startsWith("end switch") -> close(Open.SWITCH, ln)
                // ── openers ──
                t.endsWith("{") -> stack += Open.BRACE to ln
                word in endBlocks -> stack += Open.END to ln
                word == "box" -> stack += Open.BOX to ln
                (word == "note" || word == "hnote" || word == "rnote") && ':' !in t && '"' !in t -> stack += Open.NOTE to ln
                word == "ref" && ':' !in t -> stack += Open.REF to ln
                lower == "legend" || (word == "legend" && lower.split(Regex("\\s+")).size <= 2) -> stack += Open.LEGEND to ln
                lower == "header" -> stack += Open.HEADER to ln
                lower == "footer" -> stack += Open.FOOTER to ln
                word == "if" -> stack += Open.IF to ln
                word == "while" -> stack += Open.WHILE to ln
                word == "repeat" -> stack += Open.REPEAT to ln
                word == "fork" && !lower.startsWith("fork again") -> stack += Open.FORK to ln
                word == "split" && !lower.startsWith("split again") -> stack += Open.SPLIT to ln
                word == "switch" -> stack += Open.SWITCH to ln
            }
        }
        return out.sortedWith(compareBy({ it.hideStart }, { -it.hideEnd }))
    }

    /** Of the folded regions, the ones actually visible as a placeholder (not inside another). */
    fun topLevel(folded: List<FoldRegion>): List<FoldRegion> {
        val out = ArrayList<FoldRegion>()
        var end = -1
        for (r in folded.sortedBy { it.hideStart }) {
            if (r.hideStart < end) continue
            out += r
            end = r.hideEnd
        }
        return out
    }

    fun lineStarts(text: String): IntArray {
        val list = ArrayList<Int>()
        list += 0
        for (i in text.indices) if (text[i] == '\n') list += i + 1
        return list.toIntArray()
    }

    private fun lineEnd(text: String, starts: IntArray, line: Int) =
        if (line + 1 < starts.size) starts[line + 1] - 1 else text.length

    /** 0-based line of [offset]. */
    fun lineOf(starts: IntArray, offset: Int): Int {
        var i = java.util.Arrays.binarySearch(starts, offset)
        if (i < 0) i = -i - 2
        return i.coerceIn(0, starts.size - 1)
    }

    /** Replace each folded range with the placeholder, keeping the syntax colours elsewhere. */
    fun transform(text: AnnotatedString, folds: List<FoldRegion>, placeholder: SpanStyle): Pair<AnnotatedString, OffsetMapping> {
        if (folds.isEmpty()) return text to OffsetMapping.Identity
        val b = AnnotatedString.Builder()
        var last = 0
        for (f in folds) {
            b.append(text.subSequence(last, f.hideStart))
            b.withStyle(placeholder) { append(PLACEHOLDER) }
            last = f.hideEnd
        }
        b.append(text.subSequence(last, text.length))
        return b.toAnnotatedString() to FoldMapping(folds, text.length)
    }
}

/**
 * Maps between the real text and the folded view. A click INSIDE a placeholder maps into the
 * hidden range, which the document treats as "expand this fold" (like clicking IntelliJ's `…`).
 */
class FoldMapping(private val folds: List<FoldRegion>, private val originalLength: Int) : OffsetMapping {
    private val tStart = IntArray(folds.size)
    private val transformedLength: Int

    init {
        var shift = 0
        folds.forEachIndexed { i, f ->
            tStart[i] = f.hideStart + shift
            shift += Folding.PLACEHOLDER.length - (f.hideEnd - f.hideStart)
        }
        transformedLength = originalLength + shift
    }

    override fun originalToTransformed(offset: Int): Int {
        var shift = 0
        folds.forEachIndexed { i, f ->
            if (offset <= f.hideStart) return offset + shift
            if (offset < f.hideEnd) return tStart[i] + Folding.PLACEHOLDER.length // hidden: after the placeholder
            shift += Folding.PLACEHOLDER.length - (f.hideEnd - f.hideStart)
        }
        return (offset + shift).coerceIn(0, transformedLength)
    }

    override fun transformedToOriginal(offset: Int): Int {
        var shift = 0
        folds.forEachIndexed { i, f ->
            val ts = tStart[i]
            val te = ts + Folding.PLACEHOLDER.length
            if (offset <= ts) return offset - shift
            if (offset < te) return if (f.hideEnd - f.hideStart > 1) f.hideStart + 1 else f.hideStart
            if (offset == te) return f.hideEnd
            shift += Folding.PLACEHOLDER.length - (f.hideEnd - f.hideStart)
        }
        return (offset - shift).coerceIn(0, originalLength)
    }
}

/**
 * Which regions are folded, kept on the Document. Folds are remembered by their [hideStart] and
 * shifted as the text is edited ([onEdit]); a fold whose start is edited away is dropped.
 */
@Stable
class FoldState {
    var folded by mutableStateOf<Set<Int>>(emptySet())
        private set

    private var cacheText: String? = null
    private var cache: List<FoldRegion> = emptyList()

    fun regions(text: String): List<FoldRegion> {
        if (text !== cacheText) { cache = Folding.regions(text); cacheText = text }
        return cache
    }

    /** Folded regions that still exist in [text]. */
    fun active(text: String): List<FoldRegion> =
        if (folded.isEmpty()) emptyList() else regions(text).filter { it.hideStart in folded }

    fun isFolded(r: FoldRegion) = r.hideStart in folded
    fun fold(r: FoldRegion) { folded = folded + r.hideStart }
    fun unfold(r: FoldRegion) { folded = folded - r.hideStart }
    fun toggle(r: FoldRegion) = if (isFolded(r)) unfold(r) else fold(r)
    fun set(rs: Collection<FoldRegion>) { folded = rs.mapTo(HashSet()) { it.hideStart } }
    fun clear() { folded = emptySet() }

    /** Keep folds attached to their text when [old] becomes [new]. */
    fun onEdit(old: String, new: String) {
        if (folded.isEmpty() || old == new) return
        val max = minOf(old.length, new.length)
        var p = 0
        while (p < max && old[p] == new[p]) p++
        var s = 0
        while (s < max - p && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
        val editEnd = old.length - s
        val delta = new.length - old.length
        folded = folded.mapNotNullTo(HashSet()) { k ->
            when {
                k < p -> k
                k >= editEnd -> k + delta
                else -> null // the opening line's end was edited: forget this fold
            }
        }
    }

    /**
     * Called for every new editor value. Arrow keys hop over a fold (caret from its start to its
     * end and back); a caret or selection end landing INSIDE a fold (click on `…`, find, error
     * jump) expands it, so nothing is ever edited unseen.
     */
    fun adjust(old: TextFieldValue, new: TextFieldValue): TextFieldValue {
        if (folded.isEmpty()) return new
        val active = active(new.text)
        if (active.isEmpty()) return new
        val sel = new.selection
        if (sel.collapsed && old.text == new.text && old.selection.collapsed) {
            val c = sel.start
            val hit = Folding.topLevel(active).firstOrNull { c in it }
            if (hit != null) {
                when (old.selection.start) {
                    hit.hideStart -> return new.copy(selection = TextRange(hit.hideEnd))
                    hit.hideEnd -> return new.copy(selection = TextRange(hit.hideStart))
                }
            }
        }
        val inside = active.filter { sel.start in it || sel.end in it }
        if (inside.isNotEmpty()) folded = folded - inside.map { it.hideStart }.toSet()
        return new
    }
}

// ── Document commands (Code → Folding menu, gutter clicks) ──────────────────────────────────

private fun es.hugoalvarezajenjo.sproutstudio.model.Document.caretLine(): Int =
    Folding.lineOf(Folding.lineStarts(text), value.selection.start)

/** Fold [r]; if the caret would end up hidden, park it at the end of the opening line first. */
fun es.hugoalvarezajenjo.sproutstudio.model.Document.foldRegion(r: FoldRegion) {
    val sel = value.selection
    if (sel.start in r || sel.end in r || (sel.min <= r.hideStart && sel.max >= r.hideEnd && !sel.collapsed)) {
        value = value.copy(selection = TextRange(r.hideStart))
    }
    folds.fold(r)
}

/** ⌘-: fold the innermost unfolded block around the caret (press again for the outer one). */
fun es.hugoalvarezajenjo.sproutstudio.model.Document.foldAtCaret(): Boolean {
    val line = caretLine()
    val r = folds.regions(text)
        .filter { line in it.startLine..it.endLine && !folds.isFolded(it) }
        .maxByOrNull { it.startLine } ?: return false
    foldRegion(r)
    return true
}

/** ⌘+: unfold the folded block on the caret line (or the innermost one around it). */
fun es.hugoalvarezajenjo.sproutstudio.model.Document.unfoldAtCaret(): Boolean {
    val line = caretLine()
    val folded = folds.active(text)
    val r = folded.filter { it.startLine == line }.minByOrNull { it.startLine }
        ?: folded.filter { line in it.startLine..it.endLine }.maxByOrNull { it.startLine }
        ?: return false
    folds.unfold(r)
    return true
}

fun es.hugoalvarezajenjo.sproutstudio.model.Document.foldAll() {
    val all = folds.regions(text)
    if (all.isEmpty()) return
    // Park the caret on the outermost opener that would hide it.
    val c = value.selection.start
    Folding.topLevel(all).firstOrNull { c in it }?.let {
        value = value.copy(selection = TextRange(it.hideStart))
    }
    folds.set(all)
}

fun es.hugoalvarezajenjo.sproutstudio.model.Document.unfoldAll() = folds.clear()
