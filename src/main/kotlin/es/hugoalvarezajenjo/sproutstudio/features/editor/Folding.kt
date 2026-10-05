package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.runtime.getValue

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
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

// ── Document commands (Code → Folding menu, gutter clicks) ──────────────────────────────────

private fun es.hugoalvarezajenjo.sproutstudio.features.document.Document.caretLine(): Int =
    Folding.lineOf(Folding.lineStarts(text), value.selection.start)

/** Fold [r]; if the caret would end up hidden, park it at the end of the opening line first. */
fun es.hugoalvarezajenjo.sproutstudio.features.document.Document.foldRegion(r: FoldRegion) {
    val sel = value.selection
    if (sel.start in r || sel.end in r || (sel.min <= r.hideStart && sel.max >= r.hideEnd && !sel.collapsed)) {
        value = value.copy(selection = TextRange(r.hideStart))
    }
    folds.fold(r)
}

/** ⌘-: fold the innermost unfolded block around the caret (press again for the outer one). */
fun es.hugoalvarezajenjo.sproutstudio.features.document.Document.foldAtCaret(): Boolean {
    val line = caretLine()
    val r = folds.regions(text)
        .filter { line in it.startLine..it.endLine && !folds.isFolded(it) }
        .maxByOrNull { it.startLine } ?: return false
    foldRegion(r)
    return true
}

/** ⌘+: unfold the folded block on the caret line (or the innermost one around it). */
fun es.hugoalvarezajenjo.sproutstudio.features.document.Document.unfoldAtCaret(): Boolean {
    val line = caretLine()
    val folded = folds.active(text)
    val r = folded.filter { it.startLine == line }.minByOrNull { it.startLine }
        ?: folded.filter { line in it.startLine..it.endLine }.maxByOrNull { it.startLine }
        ?: return false
    folds.unfold(r)
    return true
}

fun es.hugoalvarezajenjo.sproutstudio.features.document.Document.foldAll() {
    val all = folds.regions(text)
    if (all.isEmpty()) return
    // Park the caret on the outermost opener that would hide it.
    val c = value.selection.start
    Folding.topLevel(all).firstOrNull { c in it }?.let {
        value = value.copy(selection = TextRange(it.hideStart))
    }
    folds.set(all)
}

fun es.hugoalvarezajenjo.sproutstudio.features.document.Document.unfoldAll() = folds.clear()
