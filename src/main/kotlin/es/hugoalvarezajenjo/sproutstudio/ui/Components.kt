package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * JetBrains-style toolbar action: a 28dp square with a 16dp line icon that only shows a
 * background on hover. [label] turns it into a compact text button.
 */
@Composable
fun ToolButton(
    icon: ImageVector?,
    tooltip: String? = null,
    label: String? = null,
    enabled: Boolean = true,
    selected: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        primary && enabled -> if (hovered) c.accent.copy(alpha = 0.88f) else c.accent
        !enabled -> Color.Transparent
        selected -> c.hover
        hovered -> c.hover
        else -> Color.Transparent
    }
    val fg = when {
        primary && enabled -> c.onAccent
        !enabled -> c.textDisabled
        else -> c.text
    }
    val content = @Composable {
        Row(
            Modifier
                .height(28.dp)
                .then(if (label == null) Modifier.width(28.dp) else Modifier)
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .hoverable(interaction)
                .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
                .padding(horizontal = if (label != null) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            if (icon != null) Icon(icon, contentDescription = tooltip ?: label, tint = fg, modifier = Modifier.size(16.dp))
            if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
    if (tooltip != null) Tip(tooltip) { content() } else content()
}

@OptIn(ExperimentalComposeUiApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun Tip(text: String, content: @Composable () -> Unit) {
    val c = ide
    TooltipArea(
        tooltip = {
            Surface(shape = RoundedCornerShape(6.dp), color = c.tooltip, shadowElevation = 6.dp) {
                Text(text, color = c.onTooltip, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
            }
        },
        delayMillis = 600,
    ) { content() }
}

/** Thin vertical divider between toolbar groups. */
@Composable
fun ToolSeparator() {
    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(16.dp).background(ide.popupBorder))
}

/** 1px separator line. */
@Composable
fun HLine() = Box(Modifier.fillMaxWidth().height(1.dp).background(ide.border))

@Composable
fun VLine(width: Dp = 1.dp) = Box(Modifier.width(width).fillMaxHeight().background(ide.border))

/** Small muted label, e.g. "Sequence" in the status bar. */
@Composable
fun StatusText(text: String, color: Color = ide.textMuted, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}

/** Outlined, rounded tag used sparingly (file name in Quick Preview). */
@Composable
fun Tag(text: String, icon: ImageVector? = null) {
    val c = ide
    Row(
        Modifier.height(24.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, c.popupBorder, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
        Text(text, color = c.text, fontSize = 12.sp)
    }
}

@Composable
fun Dot(color: Color, size: Int = 8) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier) {
    val c = ide
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = c.textDisabled, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.text, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textMuted, textAlign = TextAlign.Center)
    }
}
