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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
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

    // Focus the editor when it appears / when the document changes.
    LaunchedEffect(doc) { delay(50); runCatching { focus.requestFocus() } }

    // Report caret position for the status bar.
    LaunchedEffect(caret, value.text) {
        val before = value.text.substring(0, caret.coerceIn(0, value.text.length))
        onCaretMoved(before.count { it == '\n' } + 1, caret - (before.lastIndexOf('\n') + 1) + 1)
    }

    // Jump-to-line requests from the preview's error banner.
    LaunchedEffect(doc.jumpRequest, layout) {
        val line = doc.jumpRequest ?: return@LaunchedEffect
        val l = layout ?: return@LaunchedEffect
        doc.value = doc.value.copy(selection = EditOps.selectLine(doc.text, line))
        val idx = (line - 1).coerceIn(0, l.lineCount - 1)
        vScroll.animateScrollTo((l.getLineTop(idx) - 120f).toInt().coerceAtLeast(0))
        doc.jumpRequest = null
        runCatching { focus.requestFocus() }
    }

    val highlighted = remember(value.text, errorLine, caret, c) {
        Highlighter.highlight(value.text, c.syntax, errorLine, caret)
    }
    val transformation = remember(highlighted) {
        VisualTransformation { TransformedText(highlighted, OffsetMapping.Identity) }
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

    BoxWithConstraints(modifier.background(c.editor)) {
        val viewportW = maxWidth
        val viewportH = maxHeight
        Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
            Gutter(c, layout, value.text, errorLine, caret, measurer, density, fieldHeightPx)
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
                            band(l.getLineForOffset(l.safeOffset(caret)), c.currentLine)
                            errorLine?.let { band(it - 1, c.errorBg) }
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
                    val rect = l.getCursorRect(l.safeOffset(caret))
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

@Composable
private fun Gutter(
    c: IdeColors,
    layout: TextLayoutResult?,
    text: String,
    errorLine: Int?,
    caret: Int,
    measurer: androidx.compose.ui.text.TextMeasurer,
    density: androidx.compose.ui.unit.Density,
    fieldHeightPx: Int,
) {
    val topPad = with(density) { 10.dp.toPx() }
    val currentLine = text.substring(0, caret.coerceIn(0, text.length)).count { it == '\n' } + 1
    Box(
        Modifier
            .width(GUTTER_DP.dp)
            .height(with(density) { fieldHeightPx.toDp() })
            .background(c.editor)
            .drawBehind {
                val l = layout ?: return@drawBehind
                val lines = if (text.isEmpty()) 1 else l.lineCount
                for (i in 0 until lines) {
                    val y = topPad + l.getLineTop(i)
                    val lineNo = i + 1
                    val isErr = lineNo == errorLine
                    val isCur = lineNo == currentLine
                    if (isErr) drawCircle(c.error, 4f, Offset(10f, y + (l.getLineBottom(i) - l.getLineTop(i)) / 2))
                    val label = measurer.measure(
                        lineNo.toString(),
                        editorTextStyle.copy(
                            fontSize = 12.sp,
                            color = when { isErr -> c.error; isCur -> c.lineNumberActive; else -> c.lineNumber },
                        ),
                    )
                    val lineH = l.getLineBottom(i) - l.getLineTop(i)
                    drawText(label, topLeft = Offset(size.width - label.size.width - 12f, y + (lineH - label.size.height) / 2))
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
