package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.background
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
import es.hugoalvarezajenjo.sproutstudio.model.AppState
import es.hugoalvarezajenjo.sproutstudio.model.AppWindow
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import es.hugoalvarezajenjo.sproutstudio.preview.DiagramView
import es.hugoalvarezajenjo.sproutstudio.preview.PreviewState
import es.hugoalvarezajenjo.sproutstudio.preview.ZoomState
import es.hugoalvarezajenjo.sproutstudio.preview.rememberLivePreview
import es.hugoalvarezajenjo.sproutstudio.preview.rememberZoomState
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.max

/** "Compare Diagram with HEAD": the committed picture on the left, today's on the right. */
@Composable
fun DiagramDiffWindow(win: AppWindow.DiagramDiff) {
    Window(
        onCloseRequest = { AppState.close(win) },
        title = "${win.file.name}: HEAD ↔ Working Copy — SproutStudio",
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
                DiagramDiffView(win.project, win.file)
            }
        }
    }
}

/** The compare view itself (no window), so tests and snapshots can drive it. */
@Composable
internal fun DiagramDiffView(p: ProjectState, file: File) {
    val c = ide
    val git = p.git
    val head = git.status.head
    var base by remember { mutableStateOf<String?>(null) }
    var baseLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(file, head) { base = git.headText(file); baseLoaded = true }

    // Current text: the open editor's (even unsaved), else the file on disk, kept fresh.
    val doc = p.docs.firstOrNull { it.file?.absoluteFile == file.absoluteFile }
    var diskText by remember { mutableStateOf(runCatching { file.readText() }.getOrDefault("")) }
    LaunchedEffect(file, doc) {
        if (doc != null) return@LaunchedEffect
        var stamp = file.lastModified()
        while (true) {
            delay(700)
            val now = file.lastModified()
            if (now != stamp) { stamp = now; diskText = runCatching { file.readText() }.getOrDefault(diskText) }
        }
    }
    val current = doc?.text ?: diskText

    val left = rememberLivePreview(base.orEmpty(), file.parentFile, debounceMs = 50)
    val right = rememberLivePreview(current, file.parentFile)
    // Same diagram index on both sides when the file holds several.
    val count = max(left.diagramCount, right.diagramCount)
    LaunchedEffect(right.index) { if (left.index != right.index) left.index = right.index }

    val zoom = rememberZoomState()
    val fitW = max(left.image?.width ?: 0f, right.image?.width ?: 0f)
    val fitH = max(left.image?.height ?: 0f, right.image?.height ?: 0f)

    Column(Modifier.fillMaxSize().testTag("diagram-diff")) {
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Tag(file.name, Icons.Outlined.Schema)
            if (count > 1) {
                Spacer(Modifier.width(6.dp))
                ToolButton(Icons.Outlined.ChevronLeft, "Previous diagram", enabled = right.index > 0) { right.index-- }
                Text("${right.index + 1} of $count", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                ToolButton(Icons.Outlined.ChevronRight, "Next diagram", enabled = right.index < count - 1) { right.index++ }
            }
            Spacer(Modifier.weight(1f))
            ToolButton(Icons.Outlined.Remove, "Zoom out (mouse wheel)") { zoom.zoomBy(1 / 1.25f) }
            Text(
                "${(zoom.scale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = c.textMuted,
                modifier = Modifier.width(40.dp), textAlign = TextAlign.Center,
            )
            ToolButton(Icons.Outlined.Add, "Zoom in (mouse wheel)") { zoom.zoomBy(1.25f) }
            ToolButton(Icons.Outlined.FitScreen, "Fit both (double-click)", selected = zoom.autoFit) {
                if (fitW > 0f) zoom.fit(fitW, fitH)
            }
        }
        HLine()
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Side(
                "HEAD", head?.take(7)?.let { "last commit · $it" } ?: "",
                left, zoom, fitW, fitH, Modifier.weight(1f),
                missing = if (baseLoaded && base == null) "Not in the last commit" else null,
            )
            VLine()
            Side(
                "Working copy", if (doc?.dirty == true) "unsaved edits" else "",
                right, zoom, fitW, fitH, Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Side(
    title: String,
    subtitle: String,
    preview: PreviewState,
    zoom: ZoomState,
    fitW: Float,
    fitH: Float,
    modifier: Modifier,
    missing: String? = null,
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
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(img?.background ?: c.editor)) {
            when {
                missing != null -> EmptyState(Icons.Outlined.Draw, missing, "There is nothing to compare against", Modifier.align(Alignment.Center))
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
