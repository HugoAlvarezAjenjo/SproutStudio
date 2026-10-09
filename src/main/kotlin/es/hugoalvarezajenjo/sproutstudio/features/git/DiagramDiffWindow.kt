package es.hugoalvarezajenjo.sproutstudio.features.git

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.common_close
import es.hugoalvarezajenjo.sproutstudio.resources.diff_before
import es.hugoalvarezajenjo.sproutstudio.resources.diff_commit
import es.hugoalvarezajenjo.sproutstudio.resources.diff_deleted
import es.hugoalvarezajenjo.sproutstudio.resources.diff_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.diff_didnt_exist
import es.hugoalvarezajenjo.sproutstudio.resources.diff_fit_both
import es.hugoalvarezajenjo.sproutstudio.resources.diff_last_commit
import es.hugoalvarezajenjo.sproutstudio.resources.diff_new_file
import es.hugoalvarezajenjo.sproutstudio.resources.diff_no_differences
import es.hugoalvarezajenjo.sproutstudio.resources.diff_not_in_version
import es.hugoalvarezajenjo.sproutstudio.resources.diff_parent
import es.hugoalvarezajenjo.sproutstudio.resources.diff_text
import es.hugoalvarezajenjo.sproutstudio.resources.diff_unsaved
import es.hugoalvarezajenjo.sproutstudio.resources.diff_working_copy
import es.hugoalvarezajenjo.sproutstudio.resources.menu_dark_diagram_preview
import es.hugoalvarezajenjo.sproutstudio.resources.menu_file
import es.hugoalvarezajenjo.sproutstudio.resources.menu_view
import es.hugoalvarezajenjo.sproutstudio.resources.preview_cant_render
import es.hugoalvarezajenjo.sproutstudio.resources.preview_n_of_m
import es.hugoalvarezajenjo.sproutstudio.resources.preview_next_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.preview_no_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.preview_previous_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.preview_rendering
import es.hugoalvarezajenjo.sproutstudio.resources.preview_zoom_in
import es.hugoalvarezajenjo.sproutstudio.resources.preview_zoom_out
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

import es.hugoalvarezajenjo.sproutstudio.features.document.DiagramExtensions
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceDeps
import es.hugoalvarezajenjo.sproutstudio.core.os.Os

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schema
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import es.hugoalvarezajenjo.sproutstudio.domain.git.DiffSide
import es.hugoalvarezajenjo.sproutstudio.domain.git.LineDiff
import es.hugoalvarezajenjo.sproutstudio.features.document.isDiagram
import es.hugoalvarezajenjo.sproutstudio.features.preview.DiagramView
import es.hugoalvarezajenjo.sproutstudio.features.preview.PreviewViewModel
import es.hugoalvarezajenjo.sproutstudio.features.preview.ZoomState
import es.hugoalvarezajenjo.sproutstudio.features.preview.describe
import es.hugoalvarezajenjo.sproutstudio.features.preview.rememberPreviewViewModel
import es.hugoalvarezajenjo.sproutstudio.features.preview.rememberZoomState
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.AppIcon
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.EmptyState
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.HLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Tag
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.VLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.editorTextStyle
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.io.File
import kotlin.math.max
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay

/** Compare window: by default the committed version next to today's. */
@Composable
fun DiagramDiffWindow(
    project: WorkspaceViewModel,
    file: File,
    left: DiffSide,
    right: DiffSide,
    textFirst: Boolean,
    onClose: () -> Unit,
) {
    val deps = project.deps
    Window(
        onCloseRequest = onClose,
        title = "${file.name}: ${sideTitle(left)} ↔ ${sideTitle(right)} — SproutStudio",
        icon = AppIcon.painter,
        state = rememberWindowState(size = DpSize(1400.dp, 820.dp)),
    ) {
        MenuBar {
            Menu(tr(Res.string.menu_file)) {
                Item(tr(Res.string.common_close), shortcut = KeyShortcut(Key.W, meta = Os.isMac, ctrl = !Os.isMac)) { onClose() }
            }
            Menu(tr(Res.string.menu_view)) {
                CheckboxItem(tr(Res.string.menu_dark_diagram_preview), checked = deps.diagram.dark.value) { deps.diagram.toggle() }
            }
        }
        PumlTheme(dark = deps.theme.dark.value) {
            Surface(Modifier.fillMaxSize(), color = ide.panel) {
                DiffView(project, file, left, right, textFirst = textFirst)
            }
        }
    }
}

/** Kept for callers/tests that compare with HEAD directly. */
@Composable
internal fun DiagramDiffView(p: WorkspaceViewModel, file: File) = DiffView(p, file, DiffSide.Head, DiffSide.WorkingCopy)

internal fun sideTitle(s: DiffSide) = when (s) {
    DiffSide.Head -> "HEAD"
    DiffSide.WorkingCopy -> text(Res.string.diff_working_copy)
    is DiffSide.At -> s.id.take(7)
    is DiffSide.Before -> text(Res.string.diff_before, s.id.take(7))
}

/** Text of one side; null = the file doesn't exist there. Re-reads when HEAD or the editor change. */
@Composable
private fun rememberSideText(p: WorkspaceViewModel, file: File, side: DiffSide): Pair<String?, Boolean> {
    val git = p.git
    val doc = p.docs.firstOrNull { it.file?.absoluteFile == file.absoluteFile }
    var text by remember(side) { mutableStateOf<String?>(null) }
    var loaded by remember(side) { mutableStateOf(false) }
    when (side) {
        DiffSide.WorkingCopy -> {
            // Outside edits show up; the disk is read off the UI thread (not needed with a tab open).
            val disk by remember(file) { p.deps.documents.watch(file).map { listOf(it) } }.collectAsState(null)
            if (doc != null) return doc.text to true
            return disk?.single() to (disk != null)
        }
        DiffSide.Head -> {
            val head = git.status.head
            LaunchedEffect(file, head) { text = head?.let { git.textAt(it, file) }; loaded = true }
        }
        is DiffSide.At -> LaunchedEffect(file, side) { text = git.textAt(side.id, file); loaded = true }
        is DiffSide.Before -> LaunchedEffect(file, side) { text = git.textAt(side.id + "^", file); loaded = true }
    }
    return text to loaded
}

/**
 * Two versions of [file], as the two diagrams side by side (shared zoom and pan) or as a unified
 * text diff. Non-diagram files only get the text view.
 */
@Composable
internal fun DiffView(
    p: WorkspaceViewModel,
    file: File,
    left: DiffSide,
    right: DiffSide,
    textFirst: Boolean = false,
    leadingTools: @Composable () -> Unit = { Tag(file.name, Icons.Outlined.Schema) },
) {
    val c = ide
    val isDiagram = file.isDiagram() || file.extension.lowercase() in DiagramExtensions
    var textMode by remember { mutableStateOf(textFirst || !isDiagram) }
    val (leftText, leftLoaded) = rememberSideText(p, file, left)
    val (rightText, rightLoaded) = rememberSideText(p, file, right)
    val doc = p.docs.firstOrNull { it.file?.absoluteFile == file.absoluteFile }

    Column(Modifier.fillMaxSize().testTag("diagram-diff")) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            leadingTools()
            Spacer(Modifier.width(8.dp))
            if (isDiagram) {
                ToolButton(null, label = tr(Res.string.diff_diagram), selected = !textMode) { textMode = false }
                ToolButton(null, label = tr(Res.string.diff_text), selected = textMode) { textMode = true }
            }
            Spacer(Modifier.weight(1f))
        }
        HLine()
        if (textMode) {
            TextDiff(p.git, leftText, rightText, leftLoaded && rightLoaded, left, right)
        } else {
            DiagramsSideBySide(p.deps, 
                file, leftText, rightText, leftLoaded,
                leftTitle = sideTitle(left), leftSub = sideSubtitle(p, left),
                rightTitle = sideTitle(right),
                rightSub = if (right == DiffSide.WorkingCopy && doc?.dirty == true) tr(Res.string.diff_unsaved) else sideSubtitle(p, right),
            )
        }
    }
}

private fun sideSubtitle(p: WorkspaceViewModel, s: DiffSide) = when (s) {
    DiffSide.Head -> p.git.status.head?.take(7)?.let { text(Res.string.diff_last_commit, it) } ?: ""
    DiffSide.WorkingCopy -> ""
    is DiffSide.At -> text(Res.string.diff_commit)
    is DiffSide.Before -> text(Res.string.diff_parent)
}

@Composable
private fun DiagramsSideBySide(
    deps: WorkspaceDeps, file: File, leftText: String?, rightText: String?, leftLoaded: Boolean,
    leftTitle: String, leftSub: String, rightTitle: String, rightSub: String,
) {
    val c = ide
    val leftP = rememberPreviewViewModel(deps, leftText.orEmpty(), file.parentFile, debounceMs = 50)
    val rightP = rememberPreviewViewModel(deps, rightText.orEmpty(), file.parentFile)
    val count = max(leftP.diagramCount, rightP.diagramCount)
    LaunchedEffect(rightP.index) { if (leftP.index != rightP.index) leftP.showDiagram(rightP.index) }
    val zoom = rememberZoomState()
    val fitW = max(leftP.image?.width ?: 0f, rightP.image?.width ?: 0f)
    val fitH = max(leftP.image?.height ?: 0f, rightP.image?.height ?: 0f)
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (count > 1) {
                ToolButton(Icons.Outlined.ChevronLeft, tr(Res.string.preview_previous_diagram), enabled = rightP.index > 0) { rightP.previousDiagram() }
                Text(tr(Res.string.preview_n_of_m, rightP.index + 1, count), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                ToolButton(Icons.Outlined.ChevronRight, tr(Res.string.preview_next_diagram), enabled = rightP.index < count - 1) { rightP.showDiagram(rightP.index + 1) }
            }
            Spacer(Modifier.weight(1f))
            ToolButton(Icons.Outlined.Remove, tr(Res.string.preview_zoom_out)) { zoom.zoomBy(1 / 1.25f) }
            Text(
                "${(zoom.scale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                modifier = Modifier.width(40.dp), textAlign = TextAlign.Center,
            )
            ToolButton(Icons.Outlined.Add, tr(Res.string.preview_zoom_in)) { zoom.zoomBy(1.25f) }
            ToolButton(Icons.Outlined.FitScreen, tr(Res.string.diff_fit_both), selected = zoom.autoFit) { if (fitW > 0f) zoom.fit(fitW, fitH) }
        }
        HLine()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Side(
                leftTitle, leftSub, leftP, zoom, fitW, fitH, Modifier.weight(1f),
                missing = if (leftLoaded && leftText == null) tr(Res.string.diff_not_in_version) else null,
            )
            VLine()
            Side(
                rightTitle, rightSub, rightP, zoom, fitW, fitH, Modifier.weight(1f),
                missing = if (rightText == null) tr(Res.string.diff_not_in_version) else null,
            )
        }
    }
}

@Composable
private fun Side(
    title: String, subtitle: String, preview: PreviewViewModel, zoom: ZoomState,
    fitW: Float, fitH: Float, modifier: Modifier, missing: String? = null,
) {
    val c = ide
    Column(modifier.fillMaxHeight()) {
        Row(
            Modifier.fillMaxWidth().height(28.dp).background(c.panel).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = c.text)
            Text(subtitle, fontSize = 12.sp, color = c.textMuted)
        }
        HLine()
        val img = preview.image
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(if (missing == null) img?.background ?: c.editor else c.editor)) {
            when {
                missing != null -> EmptyState(Icons.Outlined.Draw, missing, tr(Res.string.diff_didnt_exist), Modifier.align(Alignment.Center))
                img != null -> DiagramView(
                    img, zoom, dimmed = preview.stale,
                    fitWidth = if (fitW > 0f) fitW else img.width, fitHeight = if (fitH > 0f) fitH else img.height,
                )
                !preview.firstDone -> EmptyState(Icons.Outlined.Draw, tr(Res.string.preview_rendering), "", Modifier.align(Alignment.Center))
                else -> EmptyState(Icons.Outlined.ErrorOutline, tr(Res.string.preview_cant_render), preview.error?.describe() ?: tr(Res.string.preview_no_diagram), Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Unified text diff, whole file: red rows removed, green rows added, both line numbers. */
@Composable
private fun TextDiff(git: GitViewModel, base: String?, current: String?, loaded: Boolean, left: DiffSide, right: DiffSide) {
    val c = ide
    var rows by remember { mutableStateOf<List<LineDiff.Row>?>(null) }
    LaunchedEffect(base, current, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(80)
        rows = git.diffRows(base, current)
    }
    val r = rows ?: return
    val changes = r.count { it.kind != LineDiff.Row.Kind.SAME }
    val added = r.count { it.kind == LineDiff.Row.Kind.ADDED }
    val deleted = r.count { it.kind == LineDiff.Row.Kind.DELETED }
    Column(Modifier.fillMaxSize().background(c.editor).testTag("text-diff")) {
        Row(
            Modifier.fillMaxWidth().height(28.dp).background(c.panel).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("${sideTitle(left)} → ${sideTitle(right)}", style = MaterialTheme.typography.labelLarge, color = c.text)
            when {
                base == null && current != null -> Text(tr(Res.string.diff_new_file), fontSize = 12.sp, color = c.vcsAdded)
                current == null && base != null -> Text(tr(Res.string.diff_deleted), fontSize = 12.sp, color = c.vcsDeleted)
                changes == 0 -> Text(tr(Res.string.diff_no_differences), fontSize = 12.sp, color = c.textMuted)
                else -> {
                    Text("+$added", fontSize = 12.sp, color = c.vcsAdded)
                    Text("−$deleted", fontSize = 12.sp, color = c.vcsUnversioned)
                }
            }
        }
        HLine()
        val hScroll = rememberScrollState()
        val addedBg = c.gutterAdded.copy(alpha = if (c.isDark) 0.22f else 0.35f)
        val deletedBg = c.vcsUnversioned.copy(alpha = if (c.isDark) 0.18f else 0.14f)
        LazyColumn(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
            itemsIndexed(r) { _, row ->
                val bg = when (row.kind) {
                    LineDiff.Row.Kind.ADDED -> addedBg
                    LineDiff.Row.Kind.DELETED -> deletedBg
                    LineDiff.Row.Kind.SAME -> Color.Transparent
                }
                Row(Modifier.widthIn(min = 2000.dp).background(bg).padding(vertical = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    val num = editorTextStyle.copy(fontSize = 12.sp, color = c.lineNumber)
                    Text(row.oldLine?.toString() ?: "", style = num, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
                    Text(row.newLine?.toString() ?: "", style = num, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
                    Text(
                        when (row.kind) { LineDiff.Row.Kind.ADDED -> "+"; LineDiff.Row.Kind.DELETED -> "−"; else -> " " },
                        style = editorTextStyle.copy(color = c.textMuted), textAlign = TextAlign.Center, modifier = Modifier.width(24.dp),
                    )
                    Text(row.text.ifEmpty { " " }, style = editorTextStyle.copy(color = c.syntax.symbol), softWrap = false)
                }
            }
        }
    }
}
