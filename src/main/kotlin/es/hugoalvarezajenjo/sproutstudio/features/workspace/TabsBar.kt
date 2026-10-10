package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.common_close
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

import androidx.compose.runtime.getValue

import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.core.os.shortcutHint
import es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Dot
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.HLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.VLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide

@Composable
internal fun TabsBar(p: WorkspaceViewModel, onCloseTab: (Document) -> Unit) {
    if (p.docs.isEmpty()) return
    val c = ide
    Column {
        Row(Modifier.fillMaxWidth().height(36.dp).background(c.panel), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState())) {
            p.docs.forEachIndexed { i, d ->
                val active = i == p.activeIndex
                val interaction = remember { MutableInteractionSource() }
                val hovered by interaction.collectIsHoveredAsState()
                Box(
                    Modifier
                        .fillMaxHeight()
                        .background(if (active) c.editor else if (hovered) c.hover else Color.Transparent)
                        .hoverable(interaction)
                        .clickable { p.selectTab(i) },
                ) {
                    Row(
                        Modifier.fillMaxHeight().padding(start = 12.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Outlined.Schema, null, tint = c.syntax.arrow, modifier = Modifier.size(15.dp))
                        Text(d.name, style = MaterialTheme.typography.bodyMedium, color = if (active) c.text else c.textMuted)
                        Box(
                            Modifier.size(18.dp).clip(RoundedCornerShape(4.dp)).clickable { onCloseTab(d) },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                d.dirty && !hovered -> Dot(c.accent, 6)
                                hovered || active -> Icon(Icons.Outlined.Close, tr(Res.string.common_close), tint = c.textMuted, modifier = Modifier.size(13.dp))
                            }
                        }
                    }
                    // Active-tab indicator, like the New UI.
                    if (active) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(c.accent))
                }
                VLine()
            }
        }
            LayoutSwitcher(p)
        }
        HLine()
    }
}

/** IntelliJ's top-right editor switcher: code only, code + preview, preview only. */
@Composable
private fun LayoutSwitcher(p: WorkspaceViewModel) {
    Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        LAYOUT_BUTTONS.forEach { (l, icon) ->
            ToolButton(icon, "${tr(l.label)} (${l.shortcutHint})", selected = p.layout == l) { p.changeLayout(l) }
        }
    }
}

private val LAYOUT_BUTTONS = listOf(
    EditorLayout.EDITOR to Icons.Outlined.Code,
    EditorLayout.SPLIT to Icons.Outlined.VerticalSplit,
    EditorLayout.PREVIEW to Icons.Outlined.Image,
)

internal val EditorLayout.shortcutKey: Key
    get() = when (this) { EditorLayout.EDITOR -> Key.One; EditorLayout.SPLIT -> Key.Two; EditorLayout.PREVIEW -> Key.Three }

private val EditorLayout.shortcutHint: String
    get() = (if (Os.isMac) "⌥⌘" else "Ctrl+Alt+") + (ordinal + 1)
