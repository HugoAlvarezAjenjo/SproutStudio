package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.features.explorer.OsTexts
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_new_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_new_diagram_hint
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_no_recents
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_no_recents_hint
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_open_file
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_open_file_hint
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_open_folder
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_open_folder_hint
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_recent_projects
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_remove_recent
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_settings
import es.hugoalvarezajenjo.sproutstudio.resources.welcome_tagline
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.AppIcon
import es.hugoalvarezajenjo.sproutstudio.core.buildinfo.BuildInfo
import androidx.compose.foundation.Image

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
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import androidx.compose.material.icons.outlined.Settings
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.io.File

@Composable
internal fun Welcome(p: WorkspaceViewModel) {
    val c = ide
    // Read without remember so removing a recent repaints the list (recents is backed by a Setting state).
    val recents = p.deps.recents.folders()
    // IntelliJ-style: a sidebar flush with the left edge, recents filling the rest of the window.
    Row(Modifier.fillMaxSize().background(c.editor)) {
            // Left: brand + create/open actions, tip anchored to the bottom.
            Column(Modifier.width(320.dp).fillMaxHeight().background(c.panel).padding(horizontal = 24.dp, vertical = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppIcon.painter?.let { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
                    Column {
                        Text("SproutStudio", style = MaterialTheme.typography.headlineSmall, color = c.text)
                        Text(tr(Res.string.welcome_tagline, BuildInfo.version), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
                Spacer(Modifier.height(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionCard(
                        Icons.Outlined.FolderOpen, tr(Res.string.welcome_open_folder), tr(Res.string.welcome_open_folder_hint), primary = true,
                    ) { p.chooseFolder()?.let { p.openRoot(it) } }
                    ActionCard(Icons.Outlined.Description, tr(Res.string.welcome_open_file), tr(Res.string.welcome_open_file_hint)) { p.openDiagramDialog() }
                    ActionCard(Icons.AutoMirrored.Outlined.NoteAdd, tr(Res.string.welcome_new_diagram), tr(Res.string.welcome_new_diagram_hint)) { p.newDocument() }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    tr(OsTexts.welcomeTip),
                    fontSize = 12.sp, color = c.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                ToolButton(Icons.Outlined.Settings, label = tr(Res.string.welcome_settings)) { p.deps.windows.openSettings() }
            }

            Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))

            // Right: recent projects.
            Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 32.dp, vertical = 28.dp)) {
                Text(
                    tr(Res.string.welcome_recent_projects),
                    style = MaterialTheme.typography.labelSmall, color = c.textMuted,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp,
                )
                Spacer(Modifier.height(10.dp))
                if (recents.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            Icons.Outlined.History,
                            tr(Res.string.welcome_no_recents),
                            tr(Res.string.welcome_no_recents_hint),
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
            Tip(tr(Res.string.welcome_remove_recent)) {
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (removeHovered) c.hover else Color.Transparent)
                        .hoverable(removeInteraction)
                        .clickable(interactionSource = removeInteraction, indication = null, onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Close, tr(Res.string.welcome_remove_recent), tint = c.textMuted, modifier = Modifier.size(15.dp))
                }
            }
        }
    }
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
