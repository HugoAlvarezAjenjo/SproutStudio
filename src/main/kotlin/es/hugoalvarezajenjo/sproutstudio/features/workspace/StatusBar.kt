package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.runtime.getValue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.features.git.BranchButton
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.StatusText
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide

@Composable
internal fun StatusBar(p: WorkspaceViewModel, line: Int, col: Int, info: StatusInfo?) {
    val c = ide
    Row(
        Modifier.fillMaxWidth().height(26.dp).background(c.panel).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Breadcrumb: folder › file
        val doc = p.active
        StatusText(listOfNotNull(p.root?.name, doc?.name).joinToString("  ›  "))
        Spacer(Modifier.weight(1f))
        if (doc != null) {
            info?.let {
                val problemText = when {
                    it.problems == 0 -> "No problems"
                    it.problems == 1 -> "1 problem"
                    else -> "${it.problems} problems"
                }
                StatusClickable(
                    text = problemText,
                    color = if (it.problems == 0) c.textMuted else c.error,
                    icon = if (it.problems == 0) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    onClick = { p.showProblems(true) },
                )
                StatusText(if (it.diagrams > 1) "${it.kind} · ${it.diagrams} diagrams" else it.kind)
            }
            StatusText("$line:$col")
            when {
                doc.saveError != null -> StatusText("Save failed: ${doc.saveError}", c.error, Icons.Outlined.ErrorOutline)
                doc.dirty -> StatusText(if (doc.file == null) "Not saved yet" else "Modified")
                else -> StatusText(if (p.deps.autoSaver.enabled) "Saved automatically" else "Saved")
            }
        }
        BranchButton(p)
        StatusText("Offline", icon = Icons.Outlined.Lock)
    }
}

/** A status-bar segment that behaves like a button: hover tint, hand cursor, click. */
@Composable
private fun StatusClickable(text: String, color: Color, icon: ImageVector, onClick: () -> Unit) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) c.hover else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}
