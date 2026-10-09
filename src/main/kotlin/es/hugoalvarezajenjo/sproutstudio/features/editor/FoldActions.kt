package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.FoldRegion
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Folding
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.TextLines
import es.hugoalvarezajenjo.sproutstudio.features.document.Document

// ── Document commands (Code → Folding menu, gutter clicks) ──────────────────────────────────

private fun Document.caretLine(): Int =
    TextLines.of(TextLines.starts(text), value.selection.start)

/** Fold [r]; if the caret would end up hidden, park it at the end of the opening line first. */
fun Document.foldRegion(r: FoldRegion) {
    val sel = value.selection
    if (sel.start in r || sel.end in r || (sel.min <= r.hideStart && sel.max >= r.hideEnd && !sel.collapsed)) {
        value = value.copy(selection = TextRange(r.hideStart))
    }
    folds.fold(r)
}

/** ⌘-: fold the innermost unfolded block around the caret (press again for the outer one). */
fun Document.foldAtCaret(): Boolean {
    val line = caretLine()
    val r = folds.regions(text)
        .filter { line in it.startLine..it.endLine && !folds.isFolded(it) }
        .maxByOrNull { it.startLine } ?: return false
    foldRegion(r)
    return true
}

/** ⌘+: unfold the folded block on the caret line (or the innermost one around it). */
fun Document.unfoldAtCaret(): Boolean {
    val line = caretLine()
    val folded = folds.active(text)
    val r = folded.filter { it.startLine == line }.minByOrNull { it.startLine }
        ?: folded.filter { line in it.startLine..it.endLine }.maxByOrNull { it.startLine }
        ?: return false
    folds.unfold(r)
    return true
}

fun Document.foldAll() {
    val all = folds.regions(text)
    if (all.isEmpty()) return
    // Park the caret on the outermost opener that would hide it.
    val c = value.selection.start
    Folding.topLevel(all).firstOrNull { c in it }?.let {
        value = value.copy(selection = TextRange(it.hideStart))
    }
    folds.set(all)
}

fun Document.unfoldAll() = folds.clear()
