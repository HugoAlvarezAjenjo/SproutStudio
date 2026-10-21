package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.TextLines
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.IdeColors

/** Find hits beyond this many aren't painted: the list still counts them, the screen can't show them anyway. */
private const val MAX_DRAWN_MATCHES = 2000

/** What the text field paints under its text, besides the syntax colours. Offsets are in the real text. */
internal class EditorHighlights(
    val caret: Int,
    /** 1-based lines with a problem. */
    val errorLines: Set<Int>,
    val lineStarts: IntArray,
    /** Other occurrences of the symbol at the caret. */
    val usages: List<IntRange>,
    /** The ⌘-hover link, underlined. */
    val link: IntRange?,
    val matches: List<IntRange>,
    val currentMatch: Int,
)

/** Paint [h] under the text laid out as [l]; [toLayout] maps a real offset into the (folded) layout. */
internal fun DrawScope.drawEditorHighlights(l: TextLayoutResult, toLayout: (Int) -> Int, c: IdeColors, h: EditorHighlights) {
    fun at(offset: Int) = l.safeOffset(toLayout(offset))

    // Line bands reach under the field's padding, edge to edge.
    val left = -EditorMetrics.textStart.toPx()
    val extra = (EditorMetrics.textStart + EditorMetrics.textEnd).toPx()
    fun band(line: Int, color: Color) {
        if (line !in 0 until l.lineCount) return
        val top = l.getLineTop(line)
        drawRect(color, Offset(left, top), Size(size.width + extra, l.getLineBottom(line) - top))
    }
    /** Every row of real 0-based [line]: one row, or several when it wraps. */
    fun realBand(line: Int, color: Color) {
        if (line !in h.lineStarts.indices) return
        val first = l.getLineForOffset(at(h.lineStarts[line]))
        val endOffset = if (line + 1 < h.lineStarts.size) h.lineStarts[line + 1] - 1 else Int.MAX_VALUE
        val last = l.getLineForOffset(at(endOffset))
        for (row in first..maxOf(first, last)) band(row, color)
    }
    fun fill(r: IntRange, color: Color) {
        val s = at(r.first)
        val e = at(r.last + 1)
        if (e > s) drawPath(l.getPathForRange(s, e), color)
    }

    realBand(TextLines.of(h.lineStarts, h.caret.coerceAtLeast(0)), c.currentLine)
    for (line in h.errorLines) realBand(line - 1, c.errorBg)
    for (r in h.usages) fill(r, c.symbolUsage)
    h.link?.let { r ->
        val s = at(r.first)
        val e = at(r.last + 1)
        val y = l.getLineBaseline(l.getLineForOffset(s)) + 2f * density
        drawLine(c.accent, Offset(l.getHorizontalPosition(s, true), y), Offset(l.getHorizontalPosition(e, true), y), strokeWidth = 1f * density)
    }
    h.matches.asSequence().take(MAX_DRAWN_MATCHES).forEachIndexed { i, m ->
        fill(m, if (i == h.currentMatch) c.findCurrent else c.findMatch)
    }
}
