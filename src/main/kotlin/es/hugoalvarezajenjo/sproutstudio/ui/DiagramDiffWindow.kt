package es.hugoalvarezajenjo.sproutstudio.ui

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
import es.hugoalvarezajenjo.sproutstudio.git.DiffSide
import es.hugoalvarezajenjo.sproutstudio.git.LineDiff
import es.hugoalvarezajenjo.sproutstudio.model.AppState
import es.hugoalvarezajenjo.sproutstudio.model.AppWindow
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import es.hugoalvarezajenjo.sproutstudio.model.isDiagram
import es.hugoalvarezajenjo.sproutstudio.preview.DiagramView
import es.hugoalvarezajenjo.sproutstudio.preview.PreviewState
import es.hugoalvarezajenjo.sproutstudio.preview.ZoomState
import es.hugoalvarezajenjo.sproutstudio.preview.rememberLivePreview
import es.hugoalvarezajenjo.sproutstudio.preview.rememberZoomState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** Compare window: by default the committed version next to today's. */
@Composable
fun DiagramDiffWindow(win: AppWindow.DiagramDiff) {
    Window(
        onCloseRequest = { AppState.close(win) },
        title = "${win.file.name}: ${sideTitle(win.left)} ↔ ${sideTitle(win.right)} — SproutStudio",
        icon = AppIcon.painter,
        state = rememberWindowState(size = DpSize(1400.dp, 820.dp)),
    ) {
        MenuBar {
            Menu("File") {
                Item("Close", shortcut = KeyShortcut(Key.W, meta = Dialogs.isMac, ctrl = !Dialogs.isMac)) { AppState.close(win) }
            }
            Menu("View") {
                CheckboxItem("Dark Diagram Preview", checked = DiagramPrefs.dark) { DiagramPrefs.toggle() }
            }
        }
        PumlTheme {
            Surface(Modifier.fillMaxSize(), color = ide.panel) {
                DiffView(win.project, win.file, win.left, win.right, textFirst = win.textFirst)
            }
        }
    }
}

/** Kept for callers/tests that compare with HEAD directly. */
@Composable
internal fun DiagramDiffView(p: ProjectState, file: File) = DiffView(p, file, DiffSide.Head, DiffSide.WorkingCopy)

internal fun sideTitle(s: DiffSide) = when (s) {
    DiffSide.Head -> "HEAD"
    DiffSide.WorkingCopy -> "Working copy"
    is DiffSide.At -> s.id.take(7)
    is DiffSide.Before -> "Before ${s.id.take(7)}"
}

/** Text of one side; null = the file doesn't exist there. Re-reads when HEAD or the editor change. */
@Composable
private fun rememberSideText(p: ProjectState, file: File, side: DiffSide): Pair<String?, Boolean> {
    val git = p.git
    val doc = p.docs.firstOrNull { it.file?.absoluteFile == file.absoluteFile }
    var text by remember(side) { mutableStateOf<String?>(null) }
    var loaded by remember(side) { mutableStateOf(false) }
    when (side) {
        DiffSide.WorkingCopy -> {
            var disk by remember { mutableStateOf(runCatching { file.readText() }.getOrNull()) }
            LaunchedEffect(file, doc) {
                if (doc != null) return@LaunchedEffect
                var stamp = file.lastModified()
                while (true) {
                    delay(700)
                    val now = file.lastModified()
                    if (now != stamp) { stamp = now; disk = runCatching { file.readText() }.getOrNull() }
                }
            }
            return (doc?.text ?: disk) to true
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
    p: ProjectState,
    file: File,
    left: DiffSide,
    right: DiffSide,
    textFirst: Boolean = false,
    leadingTools: @Composable () -> Unit = { Tag(file.name, Icons.Outlined.Schema) },
) {
    val c = ide
    val isDiagram = file.isDiagram() || file.extension.lowercase() in es.hugoalvarezajenjo.sproutstudio.model.DiagramExtensions
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
                ToolButton(null, label = "Diagram", selected = !textMode) { textMode = false }
                ToolButton(null, label = "Text", selected = textMode) { textMode = true }
            }
            Spacer(Modifier.weight(1f))
        }
        HLine()
        if (textMode) {
            TextDiff(leftText, rightText, leftLoaded && rightLoaded, left, right)
        } else {
            DiagramsSideBySide(
                file, leftText, rightText, leftLoaded,
                leftTitle = sideTitle(left), leftSub = sideSubtitle(p, left),
                rightTitle = sideTitle(right),
                rightSub = if (right == DiffSide.WorkingCopy && doc?.dirty == true) "unsaved edits" else sideSubtitle(p, right),
            )
        }
    }
}

private fun sideSubtitle(p: ProjectState, s: DiffSide) = when (s) {
    DiffSide.Head -> p.git.status.head?.take(7)?.let { "last commit · $it" } ?: ""
    DiffSide.WorkingCopy -> ""
    is DiffSide.At -> "commit"
    is DiffSide.Before -> "parent"
}

@Composable
private fun DiagramsSideBySide(
    file: File, leftText: String?, rightText: String?, leftLoaded: Boolean,
    leftTitle: String, leftSub: String, rightTitle: String, rightSub: String,
) {
    val c = ide
    val leftP = rememberLivePreview(leftText.orEmpty(), file.parentFile, debounceMs = 50)
    val rightP = rememberLivePreview(rightText.orEmpty(), file.parentFile)
    val count = max(leftP.diagramCount, rightP.diagramCount)
    LaunchedEffect(rightP.index) { if (leftP.index != rightP.index) leftP.index = rightP.index }
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
                ToolButton(Icons.Outlined.ChevronLeft, "Previous diagram", enabled = rightP.index > 0) { rightP.index-- }
                Text("${rightP.index + 1} of $count", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                ToolButton(Icons.Outlined.ChevronRight, "Next diagram", enabled = rightP.index < count - 1) { rightP.index++ }
            }
            Spacer(Modifier.weight(1f))
            ToolButton(Icons.Outlined.Remove, "Zoom out (mouse wheel)") { zoom.zoomBy(1 / 1.25f) }
            Text(
                "${(zoom.scale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                modifier = Modifier.width(40.dp), textAlign = TextAlign.Center,
            )
            ToolButton(Icons.Outlined.Add, "Zoom in (mouse wheel)") { zoom.zoomBy(1.25f) }
            ToolButton(Icons.Outlined.FitScreen, "Fit both (double-click)", selected = zoom.autoFit) { if (fitW > 0f) zoom.fit(fitW, fitH) }
        }
        HLine()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Side(
                leftTitle, leftSub, leftP, zoom, fitW, fitH, Modifier.weight(1f),
                missing = if (leftLoaded && leftText == null) "Not in this version" else null,
            )
            VLine()
            Side(
                rightTitle, rightSub, rightP, zoom, fitW, fitH, Modifier.weight(1f),
                missing = if (rightText == null) "Not in this version" else null,
            )
        }
    }
}

@Composable
private fun Side(
    title: String, subtitle: String, preview: PreviewState, zoom: ZoomState,
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
                missing != null -> EmptyState(Icons.Outlined.Draw, missing, "The file didn't exist here", Modifier.align(Alignment.Center))
                img != null -> DiagramView(
                    img, zoom, dimmed = preview.stale,
                    fitWidth = if (fitW > 0f) fitW else img.width, fitHeight = if (fitH > 0f) fitH else img.height,
                )
                !preview.firstDone -> EmptyState(Icons.Outlined.Draw, "Rendering…", "", Modifier.align(Alignment.Center))
                else -> EmptyState(Icons.Outlined.ErrorOutline, "Can't render", preview.error?.message ?: "No diagram found", Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Unified text diff, whole file: red rows removed, green rows added, both line numbers. */
@Composable
private fun TextDiff(base: String?, current: String?, loaded: Boolean, left: DiffSide, right: DiffSide) {
    val c = ide
    var rows by remember { mutableStateOf<List<LineDiff.Row>?>(null) }
    LaunchedEffect(base, current, loaded) {
        if (!loaded) return@LaunchedEffect
        delay(80)
        rows = withContext(Dispatchers.Default) { LineDiff.rows(base.orEmpty(), current.orEmpty()) }
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
                base == null && current != null -> Text("new file", fontSize = 12.sp, color = c.vcsAdded)
                current == null && base != null -> Text("deleted", fontSize = 12.sp, color = c.vcsDeleted)
                changes == 0 -> Text("No differences", fontSize = 12.sp, color = c.textMuted)
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
