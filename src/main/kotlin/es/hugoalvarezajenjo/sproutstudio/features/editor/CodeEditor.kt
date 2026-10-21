package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import es.hugoalvarezajenjo.sproutstudio.domain.git.LineDiff
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Folding
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.IncludeReader
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Navigation
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PathLister
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.TextLines
import es.hugoalvarezajenjo.sproutstudio.domain.text.FindReplace
import es.hugoalvarezajenjo.sproutstudio.domain.text.FindResult
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.LocalEditorTextStyle
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The text editor: gutter, the text field with its highlights, and the popups at the caret.
 *
 * This file only lays the pieces out and wires them: keys are in [handleKey], ⌘-click in
 * [followLinks], painting in [drawEditorHighlights] and [EditorGutter]. What they share lives in
 * [CodeEditorState] (per document) and [EditorLayout] (where the text sits).
 */
@Composable
fun CodeEditor(
    doc: Document,
    errorLine: Int?,
    modifier: Modifier = Modifier,
    /** Every line with a problem (all diagram blocks); [errorLine] is folded in. */
    errorLines: Set<Int> = emptySet(),
    /** Git markers against HEAD (empty outside a repo or for uncommitted files). */
    lineChanges: LineDiff.Index = LineDiff.Index.Empty,
    onCaretMoved: (line: Int, col: Int) -> Unit = { _, _ -> },
    /** ⌘-click into another file (an `!include`, or a declaration in an included file): the file to open. */
    onOpenFile: (FileJump) -> Unit = {},
    /** Reads included files, so ⌘-hover and ⌘-click reach declarations in them. */
    includes: IncludeReader = IncludeFiles.DISK,
    /** Lists folders, so completion inside `!include path` offers local files. */
    paths: PathLister = IncludePaths,
    /** Long lines continue on the next row (fitted to the editor's width) instead of scrolling sideways. */
    softWrap: Boolean = false,
) {
    SideEffect { doc.updateGitLines(lineChanges) }
    val state = remember(doc, paths) { CodeEditorState(doc, paths) }
    val geometry = remember { EditorLayout() }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    fun requestFocus() { runCatching { focus.requestFocus() } }

    val c = ide
    val textStyle = LocalEditorTextStyle.current
    val value = doc.value
    val caret = value.selection.start

    val errs = remember(errorLine, errorLines) { if (errorLine == null) errorLines else errorLines + errorLine }
    val highlighted = remember(value.text, errs, caret, c) {
        Highlighter.highlight(value.text, c.syntax, errs, caret)
    }
    // ── folding: the field shows the folded view; layout offsets go through [toT] ──
    val activeFolds = Folding.topLevel(doc.folds.active(value.text))
    val folded = remember(highlighted, activeFolds, c) {
        foldTransform(highlighted, activeFolds, SpanStyle(color = c.textMuted, background = c.hover))
    }
    val mapping = folded.second
    geometry.mapping = mapping
    fun toT(o: Int) = mapping.originalToTransformed(o.coerceIn(0, value.text.length))
    val lineStarts = remember(value.text) { TextLines.starts(value.text) }
    val transformation = remember(folded) {
        VisualTransformation { TransformedText(folded.first, folded.second) }
    }

    // Focus the editor when it appears / when the document changes.
    LaunchedEffect(doc) { delay(50); requestFocus() }
    // ...and when asked to (the command palette closing hands the keyboard back).
    LaunchedEffect(doc) { doc.focusRequestFlow.collect { delay(16); requestFocus() } }

    // Report caret position for the status bar.
    LaunchedEffect(caret, value.text) {
        val before = value.text.substring(0, caret.coerceIn(0, value.text.length))
        onCaretMoved(before.count { it == '\n' } + 1, caret - (before.lastIndexOf('\n') + 1) + 1)
    }

    /** Scroll the selection into view (a third down the viewport) when it is off screen. */
    suspend fun revealSelection() {
        delay(16) // let the layout catch up with a text that just changed (Replace, an unfold)
        val l = geometry.text ?: return
        val at = l.safeOffset(geometry.toLayout(doc.value.selection.min, doc.text.length))
        val line = l.getLineForOffset(at)
        val top = l.getLineTop(line).toInt()
        val bottom = l.getLineBottom(line).toInt()
        val viewportPx = geometry.viewportPx
        val margin = with(density) { 40.dp.toPx() }.toInt()
        if (top < vScroll.value + margin || bottom > vScroll.value + viewportPx - margin) {
            vScroll.animateScrollTo((top - viewportPx / 3).coerceAtLeast(0))
        }
        val x = l.getHorizontalPosition(at, true).toInt()
        val viewW = hScroll.viewportSize
        if (viewW > 0 && (x < hScroll.value || x > hScroll.value + viewW - margin)) {
            hScroll.animateScrollTo((x - viewW / 3).coerceAtLeast(0))
        }
    }

    // Selections asked for from outside: the Problems panel, Go to Declaration, Back.
    LaunchedEffect(doc) {
        doc.jumpRequests.collect { sel ->
            snapshotFlow { geometry.text }.filterNotNull().first() // the editor may have just appeared
            doc.value = doc.value.copy(selection = sel) // unfolds if hidden
            revealSelection()
            requestFocus()
        }
    }

    // ── find / replace ──
    val find = doc.find
    val findResult = remember(value.text, find.query, find.visible) {
        if (find.visible) FindReplace.find(value.text, find.query) else FindResult.Empty
    }
    val currentMatch = currentMatchIndex(value, findResult.matches)
    // Typing in the find field (or flipping a toggle) jumps to the first hit from the caret.
    LaunchedEffect(find.query, find.visible) {
        if (!find.visible || find.query.text.isEmpty()) return@LaunchedEffect
        findIncremental(doc.value, find.query)?.let { doc.value = it; find.requestReveal() }
    }
    // Scroll the selected match into view after any find action.
    LaunchedEffect(find) { find.revealRequests.collect { revealSelection() } }
    fun closeFind() {
        find.close()
        requestFocus()
    }

    // ── navigation: usages of the symbol at the caret, ⌘-hover links, ⌘-click ──
    val nav = doc.nav
    val currentOnOpenFile by rememberUpdatedState(onOpenFile)
    val currentIncludes by rememberUpdatedState(includes)
    val usageRanges = remember(value.text, caret) {
        Navigation.occurrencesAt(value.text, caret).takeIf { it.size > 1 }.orEmpty().map { it.range }
    }
    fun offsetAt(pos: Offset) = geometry.offsetAt(pos, doc.text.length)
    fun linkAt(pos: Offset) = geometry.linkAt(pos, doc, currentIncludes)
    fun follow(offset: Int) {
        // A frame later: after anything the field itself did with the click.
        scope.launch {
            withFrameNanos { }
            state.closeCompletion()
            state.link = null
            doc.goToDeclaration(offset, currentIncludes)?.let(currentOnOpenFile)
        }
    }

    Column(modifier.background(c.editor)) {
        if (find.visible) {
            FindBar(doc, findResult, currentMatch, onClose = { closeFind() })
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().onSizeChanged { geometry.viewportPx = it.height }) {
            val viewportW = maxWidth
            val viewportH = maxHeight
            Row(Modifier.fillMaxSize().verticalScroll(vScroll)) {
                EditorGutter(
                    c, doc, geometry.text, mapping, lineStarts, errs, caret, measurer, density, geometry.fieldHeightPx, lineChanges,
                    onChangeClick = state::toggleHunk,
                )
                HunkPopup(state, lineChanges, value.text, density, onDone = ::requestFocus)
                // Wrapping: the field is exactly as wide as the editor, so the text breaks at its edge.
                Box(if (softWrap) Modifier.weight(1f) else Modifier.weight(1f).horizontalScroll(hScroll)) {
                    BasicTextField(
                        value = value,
                        onValueChange = { nv ->
                            val typed = nv.text != doc.text
                            val moved = !typed && nv.selection != doc.value.selection
                            state.update(nv, typed)
                            if (moved) state.closePopups()
                        },
                        textStyle = textStyle.copy(color = c.syntax.symbol),
                        cursorBrush = SolidColor(c.text),
                        visualTransformation = transformation,
                        onTextLayout = { geometry.text = it },
                        modifier = Modifier
                            .then(if (softWrap) Modifier.fillMaxWidth() else Modifier)
                            .defaultMinSize(minWidth = viewportW - EditorMetrics.gutter, minHeight = viewportH)
                            .testTag("code-editor-field")
                            .focusRequester(focus)
                            .onSizeChanged { geometry.fieldHeightPx = it.height }
                            .padding(
                                start = EditorMetrics.textStart, end = EditorMetrics.textEnd,
                                top = EditorMetrics.textTop, bottom = EditorMetrics.textBottom,
                            )
                            .followLinks(state, ::offsetAt, ::linkAt, ::follow)
                            .drawBehind {
                                val l = geometry.text ?: return@drawBehind
                                val h = EditorHighlights(
                                    caret, errs, lineStarts, usageRanges, state.link, findResult.matches, currentMatch,
                                )
                                drawEditorHighlights(l, ::toT, c, h)
                            }
                            .onPreviewKeyEvent { state.handleKey(it, find, ::closeFind, ::linkAt) },
                    )

                    // ── completion popup and usages list, anchored at the caret ──
                    geometry.text?.let { l ->
                        val anchor = caretPopupOffset(l, toT(caret), density)
                        state.completion?.let { req ->
                            CaretPopup(anchor, onDismiss = state::closeCompletion) {
                                CompletionPopup(req, state.selected, onPick = state::accept)
                            }
                        }
                        nav.usages?.let { us ->
                            CaretPopup(anchor, onDismiss = nav::closeUsages) {
                                UsagesPopup(us, value.text, onPick = { nav.closeUsages(); doc.navigateTo(it.range) })
                            }
                        }
                    }
                }
            }
            doc.rollbackUndo?.let {
                RollbackNotice(doc, it, Modifier.align(Alignment.BottomCenter)) { doc.undoRollback(); requestFocus() }
            }
        }
    }
}

/** The git change popup, laid over its block when [CodeEditorState.hunk] is set. */
@Composable
private fun HunkPopup(state: CodeEditorState, lineChanges: LineDiff.Index, text: String, density: Density, onDone: () -> Unit) {
    val (ch, top) = state.hunk ?: return
    // Changes moved under us (typing, a commit): drop a popup that points at a stale block.
    if (ch !in lineChanges.changes) { state.closeHunk(); return }
    val base = lineChanges.base ?: return
    // IntelliJ-style: the toolbar sits just above the block's first line and the diff rows start
    // on that line, in the editor's font, so they cover it.
    val headerPx = with(density) { (EditorMetrics.hunkHeader + 1.dp).toPx() }
    fun close() { state.closeHunk(); onDone() }
    Popup(
        offset = IntOffset(with(density) { (EditorMetrics.gutter - 4.dp).roundToPx() }, (top - headerPx).toInt().coerceAtLeast(0)),
        onDismissRequest = state::closeHunk,
        properties = PopupProperties(focusable = true),
    ) {
        ChangePopup(
            ch, LineDiff.hunkRows(base, text, ch),
            onRollback = { state.closeHunk(); state.doc.rollbackChange(ch); onDone() },
            onClose = ::close,
        )
    }
}

/** A popup just below the caret that leaves the keyboard with the editor. */
@Composable
private fun CaretPopup(offset: IntOffset, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Popup(offset = offset, onDismissRequest = onDismiss, properties = PopupProperties(focusable = false), content = content)
}

/** Just below the caret at layout offset [caretT], in the text box's coordinates. */
private fun caretPopupOffset(l: TextLayoutResult, caretT: Int, density: Density): IntOffset {
    val rect = l.getCursorRect(l.safeOffset(caretT))
    val left = with(density) { EditorMetrics.textStart.toPx() }
    val top = with(density) { EditorMetrics.textTop.toPx() }
    return IntOffset((rect.left + left - 8).toInt(), (rect.bottom + top + 4).toInt())
}
