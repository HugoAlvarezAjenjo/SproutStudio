package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.render.Problem

/** Where the user clicks a problem to go: a line in a specific diagram block. */
data class ProblemTarget(val line: Int?, val diagramIndex: Int)

/**
 * IntelliJ-style Problems tool window, pinned to the bottom of the editor. Lists every error
 * PlantUML found across all diagram blocks; clicking a row jumps to its line (and its block when
 * the file has several). Collapsible, so a clean file costs nothing but a 1-line "no problems" bar.
 */
@Composable
fun ProblemsPanel(
    problems: List<Problem>,
    expanded: Boolean,
    multiBlock: Boolean,
    onToggle: () -> Unit,
    onGoTo: (ProblemTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ide
    Column(modifier.fillMaxWidth().background(c.panel).testTag("problems-panel")) {
        ProblemsHeader(problems.size, expanded, onToggle)
        if (expanded) {
            HLine()
            if (problems.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Outlined.CheckCircle, "No problems", "Your diagram is valid")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                    itemsIndexed(problems, key = { i, p -> "$i:${p.diagramIndex}:${p.line}:${p.message}" }) { i, p ->
                        ProblemRow(p, multiBlock, Modifier.testTag("problem-row-$i")) { onGoTo(ProblemTarget(p.line, p.diagramIndex)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProblemsHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(if (hovered) c.hover else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (count == 0) {
            Icon(Icons.Outlined.CheckCircle, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
            Text("No problems", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
        } else {
            Icon(Icons.Outlined.ErrorOutline, null, tint = c.error, modifier = Modifier.size(14.dp))
            Text("Problems", style = MaterialTheme.typography.labelLarge, color = c.text)
            CountBadge(count)
        }
        Box(Modifier.width(0.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        // The toggle affordance: the whole bar is clickable, this just signals the state.
        Icon(
            if (expanded) Icons.Outlined.Close else Icons.Outlined.ErrorOutline,
            if (expanded) "Hide problems" else "Show problems",
            tint = c.textMuted,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun CountBadge(count: Int) {
    val c = ide
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(c.error.copy(alpha = 0.18f)).padding(horizontal = 7.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", color = c.error, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProblemRow(p: Problem, multiBlock: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier
            .fillMaxWidth()
            .height(26.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (hovered) c.hover else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = c.error, modifier = Modifier.size(15.dp))
        Text(
            p.message,
            style = MaterialTheme.typography.bodyMedium,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(location(p, multiBlock), style = MaterialTheme.typography.labelMedium, color = c.textMuted, maxLines = 1)
    }
}

/** "Diagram 2 · line 7", or just "line 7", or "diagram" when the line is unknown. */
private fun location(p: Problem, multiBlock: Boolean): String {
    val block = if (multiBlock) "Diagram ${p.diagramIndex + 1}" else null
    val line = p.line?.let { "line $it" }
    return listOfNotNull(block, line).joinToString(" · ").ifEmpty { "diagram" }
}
