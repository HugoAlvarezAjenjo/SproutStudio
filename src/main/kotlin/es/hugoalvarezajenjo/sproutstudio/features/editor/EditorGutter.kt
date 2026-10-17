package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.domain.git.LineChange
import es.hugoalvarezajenjo.sproutstudio.domain.git.LineDiff
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.FoldRegion
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.TextLines
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.IdeColors
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.editorTextStyle

/** Clickable things in the gutter. */
private sealed interface GutterHit {
    data class Change(val change: LineChange) : GutterHit
    data class Fold(val region: FoldRegion) : GutterHit
}

/** Line numbers, error dots, fold chevrons and git change bars, line for line with the text field's [layout]. */
@Composable
internal fun EditorGutter(
    c: IdeColors,
    doc: Document,
    layout: TextLayoutResult?,
    mapping: OffsetMapping,
    lineStarts: IntArray,
    errorLines: Set<Int>,
    caret: Int,
    measurer: TextMeasurer,
    density: Density,
    fieldHeightPx: Int,
    lineChanges: LineDiff.Index,
    onChangeClick: (LineChange, Float) -> Unit,
) {
    val topPad = with(density) { EditorMetrics.textTop.toPx() }
    val text = doc.text
    val currentLine = TextLines.of(lineStarts, caret.coerceIn(0, text.length)) + 1
    val regions = doc.folds.regions(text)
    val byStart = remember(regions) { regions.groupBy { it.startLine } }
    val barW = with(density) { EditorMetrics.changeBar.toPx() }
    // Fold chevron + line numbers sit left of the change bar.
    val markerW = with(density) { 14.dp.toPx() } + barW + with(density) { 2.dp.toPx() }

    /** Real 0-based line shown on visual line [i] (folds merge several real lines into one). */
    fun realLine(l: TextLayoutResult, i: Int): Int {
        // Compose reports the empty line after a trailing '\n' as starting on the previous line.
        if (i == l.lineCount - 1 && i > 0 && text.endsWith('\n')) return lineStarts.size - 1
        val o = mapping.transformedToOriginal(l.getLineStart(i).coerceIn(0, l.layoutInput.text.length))
        return TextLines.of(lineStarts, o.coerceIn(0, text.length))
    }

    val gutterW = with(density) { EditorMetrics.gutter.toPx() }

    /** Top (gutter px) of the visual line that shows real line [line]; past the end = bottom of the text. */
    fun lineTopPx(l: TextLayoutResult, line: Int): Float {
        if (line >= lineStarts.size) return topPad + l.getLineBottom(l.lineCount - 1)
        val o = l.safeOffset(mapping.originalToTransformed(lineStarts[line].coerceIn(0, text.length)))
        val i = l.getLineForOffset(o)
        // The empty line after a trailing '\n' (see [realLine]).
        if (line == lineStarts.size - 1 && i == l.lineCount - 2 && text.endsWith('\n')) return topPad + l.getLineTop(i + 1)
        return topPad + l.getLineTop(i)
    }

    /** What a click at [pos] would hit: a change bar (a few px wider than drawn) or a fold chevron. */
    fun hitTest(pos: Offset): GutterHit? {
        val l = layout ?: return null
        if (pos.x < gutterW - markerW - 4f || pos.y < topPad - 4f) return null
        val i = l.getLineForVerticalPosition(pos.y - topPad)
        if (pos.x >= gutterW - barW - 5 * density.density && lineChanges.changes.isNotEmpty()) {
            val real = realLine(l, i)
            val count = if (text.isEmpty()) 1 else l.lineCount
            val lastReal = if (i + 1 < count) realLine(l, i + 1) - 1 else lineStarts.size - 1
            val top = topPad + l.getLineTop(i)
            val h = l.getLineBottom(i) - l.getLineTop(i)
            val nearBottom = pos.y > top + h * 0.7f
            val ch = (real..maxOf(real, lastReal)).firstNotNullOfOrNull { lineChanges.changeAt(it) }
                ?: lineChanges.changeAt(real, orBelow = nearBottom)
                ?: lineChanges.changeAt(real, orBelow = true)
            if (ch != null) return GutterHit.Change(ch)
        }
        val r = byStart[realLine(l, i)]?.maxByOrNull { it.endLine } ?: return null
        return GutterHit.Fold(r)
    }

    // Hover feedback: a hand cursor over anything clickable, and the hovered bar drawn wider.
    var hoverPos by remember { mutableStateOf<Offset?>(null) }
    val hovered = hoverPos?.let { hitTest(it) }
    val hotChange = (hovered as? GutterHit.Change)?.change
    val hotFold = (hovered as? GutterHit.Fold)?.region

    Box(
        Modifier
            .width(EditorMetrics.gutter)
            .height(with(density) { fieldHeightPx.toDp() })
            .testTag("editor-gutter")
            .background(c.editor)
            .pointerHoverIcon(if (hovered != null) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        hoverPos = when (e.type) {
                            PointerEventType.Exit -> null
                            else -> e.changes.firstOrNull()?.position ?: hoverPos
                        }
                    }
                }
            }
            .pointerInput(doc, layout, mapping, regions, lineChanges) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    when (val hit = hitTest(pos)) {
                        is GutterHit.Change -> onChangeClick(hit.change, lineTopPx(l, hit.change.start))
                        is GutterHit.Fold -> if (doc.folds.isFolded(hit.region)) doc.folds.unfold(hit.region) else doc.foldRegion(hit.region)
                        null -> {}
                    }
                }
            }
            .drawBehind {
                val l = layout ?: return@drawBehind
                val lines = if (text.isEmpty()) 1 else l.lineCount
                for (i in 0 until lines) {
                    val y = topPad + l.getLineTop(i)
                    val lineH = l.getLineBottom(i) - l.getLineTop(i)
                    val real = realLine(l, i)
                    val lineNo = real + 1
                    // ── git change bar: a folded line shows what is hidden inside it ──
                    val lastReal = if (i + 1 < lines) realLine(l, i + 1) - 1 else lineStarts.size - 1
                    val change = if (lastReal > real) lineChanges.inRange(real, lastReal) else lineChanges.at(real)
                    val barX = size.width - barW
                    // The hovered block (all its lines) is drawn wider, as in IntelliJ.
                    val hot = hotChange != null && hotChange.type != LineChange.Type.DELETED &&
                        hotChange.start <= lastReal && real < hotChange.end
                    val hotDeleted = hotChange?.type == LineChange.Type.DELETED
                    if (change != null) {
                        val col = when (change) {
                            LineChange.Type.ADDED -> c.gutterAdded
                            LineChange.Type.MODIFIED -> c.gutterModified
                            LineChange.Type.DELETED -> c.gutterDeleted
                        }
                        val w = if (hot) barW * 2.2f else barW
                        drawRect(col, Offset(size.width - w, y), Size(w, lineH))
                    } else if (lineChanges.deletedAbove(real) || (i == lines - 1 && lineChanges.deletedAbove(real + 1))) {
                        // Deleted lines: a small grey wedge on the boundary where they were.
                        val atBottom = !lineChanges.deletedAbove(real)
                        val yy = if (atBottom) y + lineH else y
                        val k = if (hotDeleted && hotChange.start == (if (atBottom) real + 1 else real)) 1.7f else 1f
                        val wedge = Path().apply {
                            moveTo(barX, yy - barW * k); lineTo(barX + barW * 1.6f * k, yy); lineTo(barX, yy + barW * k); close()
                        }
                        drawPath(wedge, c.gutterDeleted)
                    }
                    val isErr = lineNo in errorLines
                    val isCur = lineNo == currentLine
                    if (isErr) drawCircle(c.error, 4f, Offset(8f, y + lineH / 2))
                    val label = measurer.measure(
                        lineNo.toString(),
                        editorTextStyle.copy(
                            fontSize = 12.sp,
                            color = when { isErr -> c.error; isCur -> c.lineNumberActive; else -> c.lineNumber },
                        ),
                    )
                    drawText(label, topLeft = Offset(size.width - markerW - label.size.width - 4f, y + (lineH - label.size.height) / 2))
                    // Fold marker: ▾ open / ▸ folded, IntelliJ-style chevron next to the number.
                    val r = byStart[real]?.maxByOrNull { it.endLine } ?: continue
                    val cx = size.width - barW - 2.dp.toPx() - 7.dp.toPx()
                    val cy = y + lineH / 2
                    val h = 3.5f * density.density
                    val path = Path().apply {
                        if (doc.folds.isFolded(r)) {
                            moveTo(cx - h / 2, cy - h); lineTo(cx + h / 2, cy); lineTo(cx - h / 2, cy + h)
                        } else {
                            moveTo(cx - h, cy - h / 2); lineTo(cx, cy + h / 2); lineTo(cx + h, cy - h / 2)
                        }
                    }
                    drawPath(
                        path, if (doc.folds.isFolded(r) || r == hotFold) c.lineNumberActive else c.lineNumber,
                        style = Stroke(width = (if (r == hotFold) 1.9f else 1.4f) * density.density),
                    )
                }
            },
    )
}
