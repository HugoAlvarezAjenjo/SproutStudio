package es.hugoalvarezajenjo.sproutstudio.features.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PlantUmlLanguage
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.preview_theme
import es.hugoalvarezajenjo.sproutstudio.resources.preview_theme_default
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

/**
 * Toolbar dropdown with PlantUML's bundled themes. [current] is the shown diagram's `!theme`
 * (null = default look); picking one calls [onPick], which edits the source.
 */
@Composable
fun ThemeMenu(current: String?, enabled: Boolean, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val default = tr(Res.string.preview_theme_default)
    Box {
        // Icon only, so the toolbar keeps room for copy/export; the tooltip names the theme.
        ToolButton(Icons.Outlined.Palette, tr(Res.string.preview_theme, current ?: default), enabled = enabled, selected = current != null) { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }, modifier = Modifier.testTag("theme-menu")) {
            ThemeItem(default, selected = current == null) { open = false; onPick(null) }
            HorizontalDivider(color = ide.popupBorder)
            PlantUmlLanguage.themes.forEach { t ->
                ThemeItem(t, selected = t.equals(current, ignoreCase = true)) { open = false; onPick(t) }
            }
        }
    }
}

@Composable
private fun ThemeItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            if (selected) Icon(Icons.Outlined.Check, null, tint = ide.accent, modifier = Modifier.size(16.dp))
            else Box(Modifier.size(16.dp))
        },
        onClick = onClick,
    )
}
