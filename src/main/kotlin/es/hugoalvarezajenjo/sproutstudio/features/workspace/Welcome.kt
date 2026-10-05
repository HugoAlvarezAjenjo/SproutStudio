package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ListRow
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide

@Composable
internal fun Welcome(p: WorkspaceViewModel) {
    val c = ide
    Box(Modifier.fillMaxSize().background(c.editor), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 520.dp).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppMark(44)
                Column {
                    Text("SproutStudio", style = MaterialTheme.typography.headlineMedium, color = c.text)
                    Text("PlantUML, offline. Version 0.1", style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolButton(Icons.Outlined.FolderOpen, label = "Open Folder", primary = true) { p.chooseFolder()?.let { p.openRoot(it) } }
                ToolButton(Icons.Outlined.Description, label = "Open File") { p.openDiagramDialog() }
                ToolButton(Icons.AutoMirrored.Outlined.NoteAdd, label = "New Diagram") { p.newDocument() }
            }
            val recents = remember { p.deps.recents.folders() }
            if (recents.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text("Recent", style = MaterialTheme.typography.labelMedium, color = c.textMuted)
                Column {
                    recents.forEach { dir ->
                        ListRow(onClick = { p.openRoot(dir) }) {
                            Initials(dir.name)
                            Column(Modifier.weight(1f)) {
                                Text(dir.name, style = MaterialTheme.typography.labelLarge, color = c.text)
                                Text(
                                    dir.parent?.replace(System.getProperty("user.home"), "~") ?: "",
                                    fontSize = 11.sp, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Tip: double-click any .puml file in Finder for an instant preview.", fontSize = 12.sp, color = c.textMuted)
        }
    }
}

/** Gradient-free app mark: rounded square with an accent "S". */
@Composable
private fun AppMark(size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size / 4).dp)).background(Color(0xFF2FA36B)),
        contentAlignment = Alignment.Center,
    ) { Text("S", color = Color.White, fontSize = (size * 0.5).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }
}

/** JetBrains recent-project tile: two initials on a tinted square. */
@Composable
private fun Initials(name: String) {
    val hues = listOf(0xFF3574F0, 0xFF2FA36B, 0xFFC77DBB, 0xFFCF8E6D, 0xFF2AACB8)
    val tint = Color(hues[(name.hashCode() and 0x7fffffff) % hues.size])
    val letters = name.split('-', '_', ' ', '.').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(tint), contentAlignment = Alignment.Center) {
        Text(letters, color = Color.White, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }
}
