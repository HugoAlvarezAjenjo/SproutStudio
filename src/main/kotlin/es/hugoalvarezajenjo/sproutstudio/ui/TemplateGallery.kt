package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.skiaCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import es.hugoalvarezajenjo.sproutstudio.lang.Template
import es.hugoalvarezajenjo.sproutstudio.lang.Templates
import es.hugoalvarezajenjo.sproutstudio.preview.DiagramImage
import es.hugoalvarezajenjo.sproutstudio.render.PlantUmlRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The template gallery: a grid of ready-made diagrams and blocks, each with a live thumbnail
 * rendered by PlantUML. Picking one inserts it (whole diagrams go into an empty file as its whole
 * content; blocks drop in at the caret). Opens over the window like the command palette.
 */
@Composable
fun TemplateGallery(onDismiss: () -> Unit, onPick: (Template) -> Unit) {
    val c = ide
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val groups = remember(query) {
        val q = query.trim().lowercase()
        Templates.byCategory
            .map { (cat, items) -> cat to items.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.category.lowercase().contains(q) } }
            .filter { it.second.isNotEmpty() }
    }

    Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .onPreviewKeyEvent { if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onDismiss(); true } else false },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp), color = c.popup, shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth(0.8f).fillMaxSize(0.82f)
                    .border(1.dp, c.popupBorder, RoundedCornerShape(12.dp))
                    .pointerInput(Unit) { detectTapGestures { } } // swallow clicks inside the panel
                    .testTag("template-gallery"),
            ) {
                Column(Modifier.padding(2.dp)) {
                    // Header: title, search, close.
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("New from template", fontSize = 15.sp, color = c.text)
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.width(220.dp).clip(RoundedCornerShape(6.dp)).background(c.hover)
                                .border(1.dp, c.popupBorder, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Outlined.Search, null, tint = c.textMuted, modifier = Modifier.width(15.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text("Filter templates…", fontSize = 13.sp, color = c.textMuted)
                                BasicTextField(
                                    query, { query = it }, singleLine = true,
                                    textStyle = editorTextStyle.copy(fontSize = 13.sp, color = c.text),
                                    cursorBrush = SolidColor(c.text),
                                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                )
                            }
                        }
                        ToolButton(Icons.Outlined.Close, "Close (esc)") { onDismiss() }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.popupBorder))
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(200.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        groups.forEach { (cat, items) ->
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                Text(cat, fontSize = 12.sp, color = c.textMuted, modifier = Modifier.padding(top = 4.dp))
                            }
                            items(items, key = { it.id }) { t -> TemplateCard(t) { onPick(t) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(template: Template, onPick: () -> Unit) {
    val c = ide
    var hovered by remember { mutableStateOf(false) }
    val thumb = rememberThumbnail(template)
    Column(
        Modifier.clip(RoundedCornerShape(8.dp))
            .border(1.dp, if (hovered) c.accent else c.popupBorder, RoundedCornerShape(8.dp))
            .background(c.panel).clickable(onClick = onPick).pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(template.id) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent()
                        hovered = e.type != androidx.compose.ui.input.pointer.PointerEventType.Exit
                    }
                }
            }
            .testTag("template-card-${template.id}"),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
            contentAlignment = Alignment.Center,
        ) { Thumbnail(thumb) }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.popupBorder))
        Text(
            template.name, fontSize = 13.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

/** Renders a template's thumbnail in the background; null until it's ready. */
@Composable
private fun rememberThumbnail(template: Template) = produceState<DiagramImage?>(null, template.id, DiagramPrefs.dark) {
    value = withContext(Dispatchers.Default) {
        runCatching {
            val bytes = PlantUmlRenderer.renderPreview(template.previewSource, null, 0, 1.0, DiagramPrefs.dark).bytes
            bytes?.let { DiagramImage.fromPng(it, 1.0) }
        }.getOrNull()
    }
}.value

/** Draws a thumbnail scaled to fit its box, or a "…" placeholder while it renders. */
@Composable
private fun Thumbnail(img: DiagramImage?) {
    val c = ide
    if (img != null) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize().background(img.background).padding(6.dp)) {
            val s = minOf(size.width / img.image.width, size.height / img.image.height, 1f).coerceAtLeast(0.01f)
            val w = img.image.width * s
            val h = img.image.height * s
            drawIntoCanvas { cv ->
                cv.skiaCanvas.drawImageRect(
                    img.image,
                    org.jetbrains.skia.Rect.makeWH(img.image.width.toFloat(), img.image.height.toFloat()),
                    org.jetbrains.skia.Rect.makeXYWH((size.width - w) / 2f, (size.height - h) / 2f, w, h),
                    org.jetbrains.skia.SamplingMode.MITCHELL, null, true,
                )
            }
        }
    } else {
        Box(Modifier.fillMaxSize().background(c.editor), contentAlignment = Alignment.Center) {
            Text("…", fontSize = 20.sp, color = c.textMuted)
        }
    }
}

/**
 * The templates as a left-side tool panel: the same catalogue as the gallery, but a narrow
 * vertical list with a small thumbnail per row. Picking a row inserts the template.
 */
@Composable
fun TemplateSidebar(onPick: (Template) -> Unit) {
    val c = ide
    var query by remember { mutableStateOf("") }
    val groups = remember(query) {
        val q = query.trim().lowercase()
        Templates.byCategory
            .map { (cat, items) -> cat to items.filter { q.isEmpty() || it.name.lowercase().contains(q) || it.category.lowercase().contains(q) } }
            .filter { it.second.isNotEmpty() }
    }
    Column(Modifier.fillMaxSize().background(c.panel).testTag("template-sidebar")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Outlined.Search, null, tint = c.textMuted, modifier = Modifier.width(14.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Templates", fontSize = 12.sp, color = c.textMuted)
                BasicTextField(
                    query, { query = it }, singleLine = true,
                    textStyle = editorTextStyle.copy(fontSize = 12.sp, color = c.text),
                    cursorBrush = SolidColor(c.text), modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
            groups.forEach { (cat, items) ->
                item(key = "h-$cat") {
                    Text(
                        cat.uppercase(), fontSize = 10.sp, color = c.textMuted,
                        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
                    )
                }
                items.forEach { t ->
                    item(key = t.id) { TemplateRow(t) { onPick(t) } }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(template: Template, onPick: () -> Unit) {
    val c = ide
    var hovered by remember { mutableStateOf(false) }
    val thumb = rememberThumbnail(template)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPick).pointerHoverIcon(PointerIcon.Hand)
            .background(if (hovered) c.hover else Color.Transparent)
            .pointerInput(template.id) {
                awaitPointerEventScope {
                    while (true) { val e = awaitPointerEvent(); hovered = e.type != androidx.compose.ui.input.pointer.PointerEventType.Exit }
                }
            }
            .padding(horizontal = 8.dp, vertical = 4.dp).testTag("template-row-${template.id}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.width(56.dp).height(38.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, c.border, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) { Thumbnail(thumb) }
        Text(template.name, fontSize = 13.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}
