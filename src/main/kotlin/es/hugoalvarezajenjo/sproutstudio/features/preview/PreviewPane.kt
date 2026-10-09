package es.hugoalvarezajenjo.sproutstudio.features.preview

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.preview_actual_size
import es.hugoalvarezajenjo.sproutstudio.resources.preview_cant_render_yet
import es.hugoalvarezajenjo.sproutstudio.resources.preview_copied
import es.hugoalvarezajenjo.sproutstudio.resources.preview_copy
import es.hugoalvarezajenjo.sproutstudio.resources.preview_copy_failed
import es.hugoalvarezajenjo.sproutstudio.resources.preview_dark_hint
import es.hugoalvarezajenjo.sproutstudio.resources.preview_export
import es.hugoalvarezajenjo.sproutstudio.resources.preview_export_failed
import es.hugoalvarezajenjo.sproutstudio.resources.preview_export_title
import es.hugoalvarezajenjo.sproutstudio.resources.preview_fit
import es.hugoalvarezajenjo.sproutstudio.resources.preview_fix_error
import es.hugoalvarezajenjo.sproutstudio.resources.preview_go_to_line
import es.hugoalvarezajenjo.sproutstudio.resources.preview_light_hint
import es.hugoalvarezajenjo.sproutstudio.resources.preview_line_prefix
import es.hugoalvarezajenjo.sproutstudio.resources.preview_n_of_m
import es.hugoalvarezajenjo.sproutstudio.resources.preview_next_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.preview_no_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.preview_nothing
import es.hugoalvarezajenjo.sproutstudio.resources.preview_nothing_to_export
import es.hugoalvarezajenjo.sproutstudio.resources.preview_on_its_way
import es.hugoalvarezajenjo.sproutstudio.resources.preview_previous_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.preview_remote_include
import es.hugoalvarezajenjo.sproutstudio.resources.preview_rendering
import es.hugoalvarezajenjo.sproutstudio.resources.preview_saved
import es.hugoalvarezajenjo.sproutstudio.resources.preview_syntax_prefix
import es.hugoalvarezajenjo.sproutstudio.resources.preview_type_start
import es.hugoalvarezajenjo.sproutstudio.resources.preview_wrap
import es.hugoalvarezajenjo.sproutstudio.resources.preview_zoom_in
import es.hugoalvarezajenjo.sproutstudio.resources.preview_zoom_out
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.features.preview.PreviewViewModel
import es.hugoalvarezajenjo.sproutstudio.features.preview.describe
import es.hugoalvarezajenjo.sproutstudio.features.preview.label
import es.hugoalvarezajenjo.sproutstudio.features.document.PreviewAction
import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import es.hugoalvarezajenjo.sproutstudio.services.render.OfflineIncludeException
import es.hugoalvarezajenjo.sproutstudio.services.render.RenderError
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.EmptyState
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.HLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolSeparator
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import java.io.File

@Composable
fun PreviewPane(
    preview: PreviewViewModel,
    /** Folder the export dialog opens in. */
    baseDir: File?,
    baseName: String,
    modifier: Modifier = Modifier,
    onJumpToLine: ((Int) -> Unit)? = null,
    leadingTools: @Composable RowScope.() -> Unit = {},
    /** Copy/export asked for from elsewhere (the command palette). */
    requests: Flow<PreviewAction> = emptyFlow(),
) {
    val c = ide
    val zoom = rememberZoomState()
    var exportMenu by remember { mutableStateOf(false) }
    val toast = preview.notice?.describe()

    LaunchedEffect(preview.notice) { if (preview.notice != null) { delay(2200); preview.clearNotice() } }

    fun copyImage() { preview.copyToClipboard() }
    fun export(f: ExportFormat) {
        chooseExportTarget(preview, baseDir, f, preview.exportName(baseName))?.let { preview.exportTo(f, it) }
    }

    // Requests wait for the first render, so "export" right after opening a file still works.
    LaunchedEffect(preview, requests) {
        requests.collect { r ->
            if (!preview.awaitExportable()) return@collect
            when (r) {
                PreviewAction.COPY_IMAGE -> copyImage()
                PreviewAction.EXPORT_SVG -> export(ExportFormat.SVG)
                PreviewAction.EXPORT_PNG -> export(ExportFormat.PNG)
                PreviewAction.EXPORT_PDF -> export(ExportFormat.PDF)
            }
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
                ToolButton(Icons.Outlined.ChevronLeft, tr(Res.string.preview_previous_diagram), enabled = preview.index > 0) { preview.previousDiagram() }
                Text(
                    tr(Res.string.preview_n_of_m, preview.index + 1, preview.diagramCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textMuted,
                )
                ToolButton(Icons.Outlined.ChevronRight, tr(Res.string.preview_next_diagram), enabled = preview.index < preview.diagramCount - 1) { preview.nextDiagram() }
            }
            Spacer(Modifier.weight(1f))
            if (preview.rendering && preview.firstDone) {
                Text(tr(Res.string.preview_rendering), fontSize = 11.sp, color = c.textMuted, modifier = Modifier.padding(end = 6.dp))
            }
            ToolButton(Icons.Outlined.Remove, tr(Res.string.preview_zoom_out)) { zoom.zoomBy(1 / 1.25f) }
            Text(
                "${(zoom.scale * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = c.textMuted,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center,
            )
            ToolButton(Icons.Outlined.Add, tr(Res.string.preview_zoom_in)) { zoom.zoomBy(1.25f) }
            ToolButton(Icons.Outlined.FitScreen, tr(Res.string.preview_fit), selected = zoom.autoFit) {
                preview.image?.let { zoom.fit(it.width, it.height) }
            }
            ToolButton(Icons.Outlined.ImageSearch, tr(Res.string.preview_actual_size)) { preview.image?.let { zoom.actualSize(it.width, it.height) } }
            ToolSeparator()
            ToolButton(
                if (preview.diagram.dark.value) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                if (preview.diagram.dark.value) tr(Res.string.preview_dark_hint)
                else tr(Res.string.preview_light_hint),
                selected = preview.diagram.dark.value,
            ) { preview.diagram.toggle() }
            ToolSeparator()
            ToolButton(Icons.Outlined.ContentCopy, tr(Res.string.preview_copy), enabled = preview.image != null) {
                copyImage()
            }
            Box {
                ToolButton(Icons.Outlined.FileDownload, tr(Res.string.preview_export), enabled = preview.image != null) { exportMenu = true }
                DropdownMenu(exportMenu, onDismissRequest = { exportMenu = false }) {
                    ExportFormat.entries.forEach { f ->
                        DropdownMenuItem(text = { Text(f.label(), style = MaterialTheme.typography.bodyMedium) }, onClick = {
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
                !preview.firstDone -> EmptyState(Icons.Outlined.Draw, tr(Res.string.preview_rendering), tr(Res.string.preview_on_its_way), Modifier.align(Alignment.Center))
                preview.error != null -> EmptyState(Icons.Outlined.ErrorOutline, tr(Res.string.preview_cant_render_yet), tr(Res.string.preview_fix_error), Modifier.align(Alignment.Center))
                preview.text.isBlank() -> EmptyState(Icons.Outlined.Draw, tr(Res.string.preview_nothing), tr(Res.string.preview_type_start), Modifier.align(Alignment.Center))
                else -> EmptyState(Icons.Outlined.Draw, tr(Res.string.preview_no_diagram), tr(Res.string.preview_wrap), Modifier.align(Alignment.Center))
            }
            Toast(toast, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

/** The export dialog, with the format's extension added when the user drops it. */
private fun chooseExportTarget(preview: PreviewViewModel, baseDir: File?, format: ExportFormat, baseName: String): File? {
    val dir = baseDir ?: File(System.getProperty("user.home"))
    val f = preview.chooseExportTarget(text(Res.string.preview_export_title, format.label()), "$baseName.${format.extension}", dir) ?: return null
    return if (f.name.endsWith(".${format.extension}")) f else File(f.parentFile, "${f.name}.${format.extension}")
}

@Composable private fun PreviewNotice.describe(): String = when (this) {
    PreviewNotice.Copied -> tr(Res.string.preview_copied)
    is PreviewNotice.CopyFailed -> tr(Res.string.preview_copy_failed, exportFailure(cause))
    is PreviewNotice.Saved -> tr(Res.string.preview_saved, file.name)
    is PreviewNotice.ExportFailed -> tr(Res.string.preview_export_failed, exportFailure(cause))
    PreviewNotice.NothingToExport -> tr(Res.string.preview_nothing_to_export)
}

@Composable private fun exportFailure(t: Throwable): String = when (t) {
    is OfflineIncludeException -> tr(Res.string.preview_remote_include, t.line)
    else -> t.message ?: t.javaClass.simpleName
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
                    append(if (err.line != null) tr(Res.string.preview_line_prefix, err.line ?: 0) else tr(Res.string.preview_syntax_prefix)); append(' ')
                    append(err.describe())
                },
                style = MaterialTheme.typography.bodyMedium,
                color = c.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).widthIn(min = 0.dp),
            )
            if (clickable) Text(tr(Res.string.preview_go_to_line), style = MaterialTheme.typography.labelMedium, color = c.accent)
        }
        HLine()
    }
}
