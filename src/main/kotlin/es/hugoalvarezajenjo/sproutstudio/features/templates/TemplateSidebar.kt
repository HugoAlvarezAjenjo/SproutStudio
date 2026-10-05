package es.hugoalvarezajenjo.sproutstudio.features.templates

import androidx.compose.runtime.setValue

import androidx.compose.runtime.getValue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Template
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Templates
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.editorTextStyle
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide

/**
 * The templates as a left-side tool panel: the same catalogue as the gallery, but a narrow
 * vertical list with a small thumbnail per row. Picking a row inserts the template.
 */
@Composable
fun TemplateSidebar(thumbnails: TemplateThumbnails, onPick: (Template) -> Unit) {
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
                    item(key = t.id) { TemplateRow(thumbnails, t) { onPick(t) } }
                }
            }
        }
    }
}

@Composable
private fun TemplateRow(thumbnails: TemplateThumbnails, template: Template, onPick: () -> Unit) {
    val c = ide
    var hovered by remember { mutableStateOf(false) }
    val thumb = rememberThumbnail(thumbnails, template)
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
