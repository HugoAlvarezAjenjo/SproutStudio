package es.hugoalvarezajenjo.sproutstudio.preview

import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.render.ExportFormat
import es.hugoalvarezajenjo.sproutstudio.model.PreviewAction
import es.hugoalvarezajenjo.sproutstudio.render.PlantUmlRenderer
import es.hugoalvarezajenjo.sproutstudio.render.RenderError
import es.hugoalvarezajenjo.sproutstudio.ui.EmptyState
import es.hugoalvarezajenjo.sproutstudio.ui.DiagramPrefs
import es.hugoalvarezajenjo.sproutstudio.ui.HLine
import es.hugoalvarezajenjo.sproutstudio.ui.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.ToolSeparator
import es.hugoalvarezajenjo.sproutstudio.ui.ide
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Pixel density of preview renders: crisp on Retina and when zooming in up to ~3x. */
private const val PREVIEW_SCALE = 3.0

/** Live render state for one document. */
@Stable
class PreviewState {
    var image by mutableStateOf<DiagramImage?>(null)
    /** True when [image] is an older, successful render shown while the current text has an error. */
    var stale by mutableStateOf(false)
    var error by mutableStateOf<RenderError?>(null)
    var diagramCount by mutableStateOf(0)
    var index by mutableStateOf(0)
    var rendering by mutableStateOf(false)
    var firstDone by mutableStateOf(false)
    /** Colour mode of the last render, so toggling it skips the typing debounce. */
    internal var renderedDark: Boolean? = null
}

/**
 * Re-renders [text] whenever it changes, debounced. LaunchedEffect restarts on every keystroke,
 * which cancels the pending delay — that's the debounce.
 */
@Composable
fun rememberLivePreview(
    text: String,
    baseDir: File?,
    debounceMs: Long = 300,
    darkDiagram: Boolean = DiagramPrefs.dark,
): PreviewState {
    val state = remember { PreviewState() }
    LaunchedEffect(text, baseDir, state.index, darkDiagram) {
        // A colour toggle re-renders at once; only typing is debounced.
        if (state.firstDone && state.renderedDark == darkDiagram) delay(debounceMs)
        state.rendering = true
        val r = PlantUmlRenderer.renderPreview(text, baseDir, state.index, PREVIEW_SCALE, darkDiagram)
        state.renderedDark = darkDiagram
        state.diagramCount = r.diagramCount
        if (r.index != state.index) state.index = r.index
        state.error = r.error
        val parsed = r.bytes?.let { DiagramImage.fromPng(it, r.scale) }
        when {
            r.error == null && parsed != null -> { state.image = parsed; state.stale = false }
            // Keep the last good picture on screen while the user is mid-edit.
            state.image != null && state.firstDone -> state.stale = true
            // Never show PlantUML's own black-and-green error bitmap: the error strip says it better.
            r.error != null -> { state.image = null; state.stale = false }
            else -> { state.image = parsed; state.stale = false }
        }
        state.rendering = false
        state.firstDone = true
    }
    return state
}

@Composable
fun PreviewPane(
    preview: PreviewState,
    text: String,
    baseDir: File?,
    baseName: String,
    modifier: Modifier = Modifier,
    onJumpToLine: ((Int) -> Unit)? = null,
    leadingTools: @Composable RowScope.() -> Unit = {},
    /** Copy/export asked for from elsewhere (the command palette); [onRequestHandled] clears it. */
    request: PreviewAction? = null,
    onRequestHandled: () -> Unit = {},
) {
    val c = ide
    val zoom = rememberZoomState()
    val scope = rememberCoroutineScope()
    var toast by remember { mutableStateOf<String?>(null) }
    var exportMenu by remember { mutableStateOf(false) }

    LaunchedEffect(toast) { if (toast != null) { delay(2200); toast = null } }

    fun copyImage() = scope.launch {
        toast = runCatching { Export.copyPngToClipboard(text, baseDir, preview.index); "Image copied to clipboard" }
            .getOrElse { "Couldn't copy: ${it.message}" }
    }
    fun export(f: ExportFormat) = scope.launch {
        val name = if (preview.diagramCount > 1) "$baseName-${preview.index + 1}" else baseName
        toast = runCatching { Export.exportWithDialog(text, baseDir, preview.index, f, name) }
            .fold({ it?.let { file -> "Saved ${file.name}" } }, { "Export failed: ${it.message}" })
    }

    // Requests wait for the first render, so "export" right after opening a file still works.
    LaunchedEffect(request, preview.image, preview.firstDone) {
        val r = request ?: return@LaunchedEffect
        if (preview.image == null && !preview.firstDone) return@LaunchedEffect
        onRequestHandled()
        if (preview.image == null) { toast = "Nothing to export: fix the diagram first"; return@LaunchedEffect }
        when (r) {
            PreviewAction.COPY_IMAGE -> copyImage()
            PreviewAction.EXPORT_SVG -> export(ExportFormat.SVG)
            PreviewAction.EXPORT_PNG -> export(ExportFormat.PNG)
        }
    }

    Column(modifier.background(c.panel)) {
        // ── Toolbar: one compact 36dp strip, JetBrains tool-window style ──────────
        Row(
            Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            leadingTools()
            if (preview.diagramCount > 1) {
                ToolButton(Icons.Outlined.ChevronLeft, "Previous diagram", enabled = preview.index > 0) { preview.index-- }
                Text(
                    "${preview.index + 1} of ${preview.diagramCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textMuted,
                )
                ToolButton(Icons.Outlined.ChevronRight, "Next diagram", enabled = preview.index < preview.diagramCount - 1) { preview.index++ }
            }
            Spacer(Modifier.weight(1f))
            if (preview.rendering && preview.firstDone) {
                Text("Rendering…", fontSize = 11.sp, color = c.textMuted, modifier = Modifier.padding(end = 6.dp))
            }
            ToolButton(Icons.Outlined.Remove, "Zoom out (mouse wheel)") { zoom.zoomBy(1 / 1.25f) }
            Text(
                "${(zoom.scale * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = c.textMuted,
                modifier = Modifier.width(40.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            ToolButton(Icons.Outlined.Add, "Zoom in (mouse wheel)") { zoom.zoomBy(1.25f) }
            ToolButton(Icons.Outlined.FitScreen, "Fit to window (double-click)", selected = zoom.autoFit) {
                preview.image?.let { zoom.fit(it.width, it.height) }
            }
            ToolButton(Icons.Outlined.ImageSearch, "Actual size") { preview.image?.let { zoom.actualSize(it.width, it.height) } }
            ToolSeparator()
            ToolButton(
                if (DiagramPrefs.dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                if (DiagramPrefs.dark) "Dark diagram (exports keep original colours) — click for light"
                else "Light diagram — click for dark",
                selected = DiagramPrefs.dark,
            ) { DiagramPrefs.toggle() }
            ToolSeparator()
            ToolButton(Icons.Outlined.ContentCopy, "Copy image to clipboard", enabled = preview.image != null) {
                copyImage()
            }
            Box {
                ToolButton(Icons.Outlined.FileDownload, "Export…", enabled = preview.image != null) { exportMenu = true }
                DropdownMenu(exportMenu, onDismissRequest = { exportMenu = false }) {
                    ExportFormat.entries.forEach { f ->
                        DropdownMenuItem(text = { Text(f.label, style = MaterialTheme.typography.bodyMedium) }, onClick = {
                            exportMenu = false
                            export(f)
                        })
                    }
                }
            }
        }
        HLine()

        // ── Error strip ───────────────────────────────────────────────────────
        AnimatedVisibility(preview.error != null, enter = fadeIn(), exit = fadeOut()) {
            val err = preview.error
            if (err != null) ErrorStrip(err, onJumpToLine)
        }

        // ── Canvas: edge to edge, painted with the diagram's own background ───
        val img = preview.image
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(img?.background ?: c.editor)) {
            when {
                img != null -> DiagramView(img, zoom, dimmed = preview.stale)
                !preview.firstDone -> EmptyState(Icons.Outlined.Draw, "Rendering…", "Your diagram is on its way", Modifier.align(Alignment.Center))
                preview.error != null -> EmptyState(Icons.Outlined.ErrorOutline, "Can't render yet", "Fix the error above to see the diagram", Modifier.align(Alignment.Center))
                text.isBlank() -> EmptyState(Icons.Outlined.Draw, "Nothing to render", "Type @startuml to begin", Modifier.align(Alignment.Center))
                else -> EmptyState(Icons.Outlined.Draw, "No diagram found", "Wrap your diagram in @startuml … @enduml", Modifier.align(Alignment.Center))
            }
            Toast(toast, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

@Composable
private fun Toast(message: String?, modifier: Modifier) {
    val c = ide
    AnimatedVisibility(message != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = c.popup,
            shadowElevation = 8.dp,
            modifier = Modifier.border(1.dp, c.popupBorder, RoundedCornerShape(8.dp)),
        ) {
            Text(message.orEmpty(), color = c.text, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

/** IntelliJ-style inline notification: flat strip, icon, message, link action. */
@Composable
private fun ErrorStrip(err: RenderError, onJumpToLine: ((Int) -> Unit)?) {
    val c = ide
    val clickable = onJumpToLine != null && err.line != null
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.errorBg)
                .then(if (clickable) Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { onJumpToLine(err.line) } else Modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.ErrorOutline, null, tint = c.error, modifier = Modifier.size(16.dp))
            Text(
                buildString {
                    append(if (err.line != null) "Line ${err.line}: " else "Syntax error: ")
                    append(err.message)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).widthIn(min = 0.dp),
            )
            if (clickable) Text("Go to line", style = MaterialTheme.typography.labelMedium, color = c.accent)
        }
        HLine()
    }
}
