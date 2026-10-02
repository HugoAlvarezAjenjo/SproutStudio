package es.hugoalvarezajenjo.sproutstudio.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import es.hugoalvarezajenjo.sproutstudio.lang.Completion
import es.hugoalvarezajenjo.sproutstudio.lang.CompletionEngine
import es.hugoalvarezajenjo.sproutstudio.lang.CompletionKind
import es.hugoalvarezajenjo.sproutstudio.lang.CompletionRequest
import es.hugoalvarezajenjo.sproutstudio.lang.Highlighter
import es.hugoalvarezajenjo.sproutstudio.model.Document
import es.hugoalvarezajenjo.sproutstudio.ui.IdeColors
import es.hugoalvarezajenjo.sproutstudio.ui.editorTextStyle
import es.hugoalvarezajenjo.sproutstudio.ui.ide
import kotlinx.coroutines.delay

private const val GUTTER_DP = 52

/**
 * The text layout lags one frame behind the text: right after a keystroke at the very end of
 * the file, the caret is one past the end of the *previous* layout. Clamp before asking it.
 */
internal fun TextLayoutResult.safeOffset(offset: Int) = offset.coerceIn(0, layoutInput.text.length)

@Composable
fun CodeEditor(
    doc: Document,
    errorLine: Int?,
    modifier: Modifier = Modifier,
    onCaretMoved: (line: Int, col: Int) -> Unit = { _, _ -> },
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var fieldHeightPx by remember { mutableStateOf(0) }
    var completion by remember(doc) { mutableStateOf<CompletionRequest?>(null) }
    var selected by remember(doc) { mutableStateOf(0) }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val c = ide
    val value = doc.value
    val caret = value.selection.start

    val highlighted = remember(value.text, errorLine, caret, c) {
        Highlighter.highlight(value.text, c.syntax, errorLine, caret)
    }
    // ── folding: the field shows the folded view; layout offsets go through [toT] ──
    val activeFolds = Folding.topLevel(doc.folds.active(value.text))
    val folded = remember(highlighted, activeFolds, c) {
        Folding.transform(highlighted, activeFolds, SpanStyle(color = c.textMuted, background = c.hover))
    }
    val mapping = folded.second
    fun toT(o: Int) = mapping.originalToTransformed(o.coerceIn(0, value.text.length))
    val lineStarts = remember(value.text) { Folding.lineStarts(value.text) }
    val transformation = remember(folded) {
        VisualTransformation { TransformedText(folded.first, folded.second) }
    }
    // Effects outlive one composition; they read the mapping of the latest one.
    val currentMapping by rememberUpdatedState(mapping)
    fun liveT(o: Int) = currentMapping.originalToTransformed(o.coerceIn(0, doc.text.length))

    // Focus the editor when it appears / when the document changes.
    LaunchedEffect(doc) { delay(50); runCatching { focus.requestFocus() } }
    // ...and when asked to (the command palette closing hands the keyboard back).
    LaunchedEffect(doc.focusTick) { if (doc.focusTick > 0) { delay(16); runCatching { focus.requestFocus() } } }

    // Report caret position for the status bar.
    LaunchedEffect(caret, value.text) {
        val before = value.text.substring(0, caret.coerceIn(0, value.text.length))
        onCaretMoved(before.count { it == '\n' } + 1, caret - (before.lastIndexOf('\n') + 1) + 1)
    }

    // Jump-to-line requests from the preview's error banner.
    LaunchedEffect(doc.jumpRequest, layout) {
        val line = doc.jumpRequest ?: return@LaunchedEffect
        val l = layout ?: return@LaunchedEffect
        doc.value = doc.value.copy(selection = EditOps.selectLine(doc.text, line)) // unfolds if hidden
        doc.jumpRequest = null
        delay(16) // layout of the unfolded text
        val l2 = layout ?: l
        val idx = l2.getLineForOffset(l2.safeOffset(liveT(doc.value.selection.min)))
        vScroll.animateScrollTo((l2.getLineTop(idx) - 120f).toInt().coerceAtLeast(0))
        runCatching { focus.requestFocus() }
    }

    // ── find / replace ──
    val find = doc.find
    val findResult = remember(value.text, find.query, find.visible) {
        if (find.visible) FindReplace.find(value.text, find.query) else FindResult.Empty
    }
    val currentMatch = currentMatchIndex(value, findResult.matches)
    var viewportPx by remember { mutableStateOf(0) }
    // Typing in the find field (or flipping a toggle) jumps to the first hit from the caret.
    LaunchedEffect(find.query, find.visible) {
        if (!find.visible || find.query.text.isEmpty()) return@LaunchedEffect
        findIncremental(doc.value, find.query)?.let { doc.value = it; find.revealTick++ }
    }
    // Scroll the selected match into view after any find action.
    LaunchedEffect(find.revealTick) {
        if (find.revealTick == 0) return@LaunchedEffect
        delay(16) // let the layout catch up with a Replace that changed the text
        val l = layout ?: return@LaunchedEffect
        val line = l.getLineForOffset(l.safeOffset(liveT(doc.value.selection.min)))
        val top = l.getLineTop(line).toInt()
        val bottom = l.getLineBottom(line).toInt()
        val margin = with(density) { 40.dp.toPx() }.toInt()
        if (top < vScroll.value + margin || bottom > vScroll.value + viewportPx - margin) {
            vScroll.animateScrollTo((top - viewportPx / 3).coerceAtLeast(0))
        }
        val x = l.getHorizontalPosition(l.safeOffset(liveT(doc.value.selection.min)), true).toInt()
        val viewW = hScroll.viewportSize
        if (viewW > 0 && (x < hScroll.value || x > hScroll.value + viewW - margin)) {
            hScroll.animateScrollTo((x - viewW / 3).coerceAtLeast(0))
        }
    }
    fun closeFind() {
        find.close()
        runCatching { focus.requestFocus() }
    }

    fun update(newValue: TextFieldValue, typed: Boolean) {
        doc.value = newValue
        if (typed) {
            completion = CompletionEngine.complete(newValue.text, newValue.selection.start)
            selected = 0
        }
    }

    fun accept(item: Completion) {
        val req = completion ?: return
        val applied = CompletionEngine.apply(doc.text, req, item, doc.value.selection.start)
        doc.value = TextFieldValue(applied.text, TextRange(applied.caret))
        completion = null
    }

    Column(modifier.background(c.editor)) {
    if (find.visible) {
        FindBar(doc, findResult, currentMatch, onClose = { closeFind() })
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().onSizeChanged { viewportPx = it.height }) {
        val viewportW = maxWidth
        val viewportH = maxHeight
        Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            Gutter(c, doc, layout, mapping, lineStarts, errorLine, caret, measurer, density, fieldHeightPx)
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                BasicTextField(
                    value = value,
                    onValueChange = { nv ->
                        val typed = nv.text != doc.text
                        val moved = !typed && nv.selection != doc.value.selection
                        update(nv, typed)
                        if (moved) completion = null
                    },
                    textStyle = editorTextStyle.copy(color = c.syntax.symbol),
                    cursorBrush = SolidColor(c.text),
                    visualTransformation = transformation,
                    onTextLayout = { layout = it },
                    modifier = Modifier
                        .defaultMinSize(minWidth = viewportW - GUTTER_DP.dp, minHeight = viewportH)
                        .focusRequester(focus)
                        .onSizeChanged { fieldHeightPx = it.height }
                        .padding(start = 12.dp, end = 24.dp, top = 10.dp, bottom = 120.dp)
                        .drawBehind {
                            val l = layout ?: return@drawBehind
                            fun band(line0: Int, color: Color) {
                                if (line0 !in 0 until l.lineCount) return
                                val top = l.getLineTop(line0); val bottom = l.getLineBottom(line0)
                                drawRect(color, Offset(-12.dp.toPx(), top), Size(size.width + 36.dp.toPx(), bottom - top))
                            }
                            band(l.getLineForOffset(l.safeOffset(toT(caret))), c.currentLine)
                            errorLine?.let { e ->
                                if (e - 1 in lineStarts.indices) band(l.getLineForOffset(l.safeOffset(toT(lineStarts[e - 1]))), c.errorBg)
                            }
                            // Find hits: draw at most what's reasonable, current one stronger.
                            findResult.matches.asSequence().take(2000).forEachIndexed { i, m ->
                                val s = l.safeOffset(toT(m.first)); val e = l.safeOffset(toT(m.last + 1))
                                if (e > s) drawPath(l.getPathForRange(s, e), if (i == currentMatch) c.findCurrent else c.findMatch)
                            }
                        }
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val cmd = ev.isMetaPressed || ev.isCtrlPressed
                            val req = completion
                            when {
                                // ── completion popup navigation ──
                                req != null && ev.key == Key.DirectionDown -> { selected = (selected + 1) % req.items.size; true }
                                req != null && ev.key == Key.DirectionUp -> { selected = (selected - 1 + req.items.size) % req.items.size; true }
                                req != null && (ev.key == Key.Enter || ev.key == Key.Tab) -> { accept(req.items[selected]); true }
                                req != null && ev.key == Key.Escape -> { completion = null; true }
                                ev.key == Key.Escape && find.visible -> { closeFind(); true }
                                // ── editing shortcuts ──
                                ev.isCtrlPressed && ev.key == Key.Spacebar -> {
                                    completion = CompletionEngine.complete(doc.text, doc.value.selection.start, force = true); selected = 0; true
                                }
                                cmd && ev.key == Key.Slash -> { update(EditOps.toggleComment(doc.value), false); true }
                                cmd && ev.key == Key.D -> { update(EditOps.duplicateLines(doc.value), false); true }
                                ev.key == Key.Tab && ev.isShiftPressed -> { update(EditOps.outdent(doc.value), false); true }
                                ev.key == Key.Tab && !cmd -> { update(EditOps.indent(doc.value), false); true }
                                ev.key == Key.Enter && !cmd && !ev.isShiftPressed -> { update(EditOps.newline(doc.value), false); true }
                                else -> false
                            }
                        },
                )

                // ── completion popup, anchored at the caret ──
                val req = completion
                val l = layout
                if (req != null && l != null) {
                    val rect = l.getCursorRect(l.safeOffset(toT(caret)))
                    val pad = with(density) { 12.dp.toPx() }
                    val top = with(density) { 10.dp.toPx() }
                    Popup(
                        offset = IntOffset((rect.left + pad - 8).toInt(), (rect.bottom + top + 4).toInt()),
                        onDismissRequest = { completion = null },
                        properties = PopupProperties(focusable = false),
                    ) {
                        CompletionPopup(req, selected, onPick = { accept(it) })
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun Gutter(
    c: IdeColors,
    doc: Document,
    layout: TextLayoutResult?,
    mapping: OffsetMapping,
    lineStarts: IntArray,
    errorLine: Int?,
    caret: Int,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    fieldHeightPx: Int,
) {
    val topPad = with(density) { 10.dp.toPx() }
    val text = doc.text
    val currentLine = Folding.lineOf(lineStarts, caret.coerceIn(0, text.length)) + 1
    val regions = doc.folds.regions(text)
    val byStart = remember(regions) { regions.groupBy { it.startLine } }
    val markerW = with(density) { 14.dp.toPx() }

    /** Real 0-based line shown on visual line [i] (folds merge several real lines into one). */
    fun realLine(l: TextLayoutResult, i: Int): Int {
        // Compose reports the empty line after a trailing '\n' as starting on the previous line.
        if (i == l.lineCount - 1 && i > 0 && text.endsWith('\n')) return lineStarts.size - 1
        val o = mapping.transformedToOriginal(l.getLineStart(i).coerceIn(0, l.layoutInput.text.length))
        return Folding.lineOf(lineStarts, o.coerceIn(0, text.length))
    }

    Box(
        Modifier
            .width(GUTTER_DP.dp)
            .height(with(density) { fieldHeightPx.toDp() })
            .background(c.editor)
            .pointerInput(doc, layout, mapping, regions) {
                detectTapGestures { pos ->
                    val l = layout ?: return@detectTapGestures
                    if (pos.x < size.width - markerW - 4f) return@detectTapGestures
                    val i = l.getLineForVerticalPosition(pos.y - topPad)
                    val r = byStart[realLine(l, i)]?.maxByOrNull { it.endLine } ?: return@detectTapGestures
                    if (doc.folds.isFolded(r)) doc.folds.unfold(r) else doc.foldRegion(r)
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
                    val isErr = lineNo == errorLine
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
                    val cx = size.width - markerW / 2 - 2f
                    val cy = y + lineH / 2
                    val h = 3.5f * density.density
                    val path = androidx.compose.ui.graphics.Path().apply {
                        if (doc.folds.isFolded(r)) {
                            moveTo(cx - h / 2, cy - h); lineTo(cx + h / 2, cy); lineTo(cx - h / 2, cy + h)
                        } else {
                            moveTo(cx - h, cy - h / 2); lineTo(cx, cy + h / 2); lineTo(cx + h, cy - h / 2)
                        }
                    }
                    drawPath(
                        path, if (doc.folds.isFolded(r)) c.lineNumberActive else c.lineNumber,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.4f * density.density),
                    )
                }
            },
    )
}

@Composable
private fun CompletionPopup(req: CompletionRequest, selected: Int, onPick: (Completion) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem((selected - 3).coerceAtLeast(0)) }
    val c = ide
    Surface(
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 12.dp,
        color = c.popup,
        modifier = Modifier.widthIn(min = 260.dp, max = 380.dp).border(1.dp, c.popupBorder, RoundedCornerShape(8.dp)),
    ) {
        Column {
            LazyColumn(state = listState, modifier = Modifier.heightIn(max = 260.dp).padding(4.dp)) {
                itemsIndexed(req.items) { i, item ->
                    val isSel = i == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSel) c.selection else Color.Transparent)
                            .clickable { onPick(item) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KindBadge(item.kind)
                        Text(
                            item.label,
                            style = editorTextStyle.copy(fontSize = 13.sp, color = c.text),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (item.detail.isNotEmpty()) {
                            Text(item.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.popupBorder))
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("⏎ / ⇥ to insert   ·   esc to close", fontSize = 11.sp, color = c.textMuted)
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun KindBadge(kind: CompletionKind) {
    // IntelliJ completion icons: a coloured glyph per kind, no filled pill.
    val s = ide.syntax
    val fg = when (kind) {
        CompletionKind.KEYWORD -> s.keyword
        CompletionKind.SYMBOL -> s.arrow
        CompletionKind.SNIPPET -> s.string
        CompletionKind.SKINPARAM -> s.color
        CompletionKind.DIRECTIVE -> s.directive
    }
    Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
        Text(kind.badge, fontSize = 10.sp, color = fg, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}
