package es.hugoalvarezajenjo.sproutstudio.features.workspace

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.EmptyState
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Tip
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.io.File

@Composable
internal fun Welcome(p: WorkspaceViewModel) {
    val c = ide
    // Read without remember so removing a recent repaints the list (recents is backed by a Setting state).
    val recents = p.deps.recents.folders()
    Box(Modifier.fillMaxSize().background(c.editor), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 920.dp).fillMaxWidth().padding(40.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            // Left: brand + create/open actions, tip anchored to the bottom.
            Column(Modifier.width(300.dp).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppMark(48)
                    Column {
                        Text("SproutStudio", style = MaterialTheme.typography.headlineSmall, color = c.text)
                        Text("PlantUML, offline · v0.1", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
                Spacer(Modifier.height(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionCard(
                        Icons.Outlined.FolderOpen, "Open folder", "Open a project directory", primary = true,
                    ) { p.chooseFolder()?.let { p.openRoot(it) } }
                    ActionCard(Icons.Outlined.Description, "Open file", "Open a single .puml diagram") { p.openDiagramDialog() }
                    ActionCard(Icons.AutoMirrored.Outlined.NoteAdd, "New diagram", "Start from a blank diagram") { p.newDocument() }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Tip: double-click any .puml file in Finder for an instant preview.",
                    fontSize = 12.sp, color = c.textMuted,
                )
            }

            // Right: recent projects.
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    "RECENT PROJECTS",
                    style = MaterialTheme.typography.labelSmall, color = c.textMuted,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
                )
                Spacer(Modifier.height(10.dp))
                if (recents.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            Icons.Outlined.History,
                            "No recent projects",
                            "Folders you open will show up here.",
                        )
                    }
                } else {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        recents.forEach { dir ->
                            RecentRow(
                                dir,
                                onOpen = { p.openRoot(dir) },
                                onRemove = { p.deps.recents.remove(dir) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A labelled, bordered button on the welcome screen: icon + title + one-line description. */
@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        primary -> if (hovered) c.accent.copy(alpha = 0.88f) else c.accent
        hovered -> c.hover
        else -> Color.Transparent
    }
    val fg = if (primary) c.onAccent else c.text
    val subFg = if (primary) c.onAccent.copy(alpha = 0.8f) else c.textMuted
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(if (primary) Modifier else Modifier.border(1.dp, c.border, RoundedCornerShape(8.dp)))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = fg)
            Text(subtitle, fontSize = 11.sp, color = subFg)
        }
    }
}

/** A recent-project row: initials tile, name, home-relative path, and a remove button on hover. */
@Composable
private fun RecentRow(dir: File, onOpen: () -> Unit, onRemove: () -> Unit) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val removeInteraction = remember { MutableInteractionSource() }
    val removeHovered by removeInteraction.collectIsHoveredAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (hovered) c.hover else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Initials(dir.name)
        Column(Modifier.weight(1f)) {
            Text(dir.name, style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                dir.parent?.replace(System.getProperty("user.home"), "~") ?: "",
                fontSize = 11.sp, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (hovered) {
            Tip("Remove from recents") {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (removeHovered) c.hover else Color.Transparent)
                        .hoverable(removeInteraction)
                        .clickable(interactionSource = removeInteraction, indication = null, onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, "Remove from recents", tint = c.textMuted, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}

/** Rounded square app mark with an accent "S". */
@Composable
private fun AppMark(size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(Color(0xFF2FA36B)),
        contentAlignment = Alignment.Center,
    ) { Text("S", color = Color.White, fontSize = (size * 0.5).sp, fontWeight = FontWeight.Bold) }
}

/** JetBrains recent-project tile: two initials on a tinted square. */
@Composable
private fun Initials(name: String) {
    val hues = listOf(0xFF3574F0, 0xFF2FA36B, 0xFFC77DBB, 0xFFCF8E6D, 0xFF2AACB8)
    val tint = Color(hues[(name.hashCode() and 0x7fffffff) % hues.size])
    val letters = name.split('-', '_', ' ', '.').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(7.dp)).background(tint), contentAlignment = Alignment.Center) {
        Text(letters, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
