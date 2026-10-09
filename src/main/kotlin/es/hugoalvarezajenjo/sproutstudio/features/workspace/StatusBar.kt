package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.problems_none
import es.hugoalvarezajenjo.sproutstudio.resources.status_kind_diagrams
import es.hugoalvarezajenjo.sproutstudio.resources.status_modified
import es.hugoalvarezajenjo.sproutstudio.resources.status_not_saved
import es.hugoalvarezajenjo.sproutstudio.resources.status_offline
import es.hugoalvarezajenjo.sproutstudio.resources.status_problems
import es.hugoalvarezajenjo.sproutstudio.resources.status_save_failed
import es.hugoalvarezajenjo.sproutstudio.resources.status_saved
import es.hugoalvarezajenjo.sproutstudio.resources.status_saved_auto
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

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
                    it.problems == 0 -> tr(Res.string.problems_none)
                    it.problems == 1 -> tr(Res.plurals.status_problems, 1, 1)
                    else -> tr(Res.plurals.status_problems, it.problems, it.problems)
                }
                StatusClickable(
                    text = problemText,
                    color = if (it.problems == 0) c.textMuted else c.error,
                    icon = if (it.problems == 0) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                    onClick = { p.showProblems(true) },
                )
                StatusText(if (it.diagrams > 1) tr(Res.string.status_kind_diagrams, it.kind, it.diagrams) else it.kind)
            }
            StatusText("$line:$col")
            when {
                doc.saveError != null -> StatusText(tr(Res.string.status_save_failed, doc.saveError.toString()), c.error, Icons.Outlined.ErrorOutline)
                doc.dirty -> StatusText(if (doc.file == null) tr(Res.string.status_not_saved) else tr(Res.string.status_modified))
                else -> StatusText(if (p.deps.autoSaver.enabled) tr(Res.string.status_saved_auto) else tr(Res.string.status_saved))
            }
        }
        BranchButton(p)
        StatusText(tr(Res.string.status_offline), icon = Icons.Outlined.Lock)
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
