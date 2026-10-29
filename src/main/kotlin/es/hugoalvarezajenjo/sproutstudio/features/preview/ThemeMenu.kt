package es.hugoalvarezajenjo.sproutstudio.features.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PlantUmlLanguage
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.preview_theme
import es.hugoalvarezajenjo.sproutstudio.resources.preview_theme_default
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.IdeDropdownMenu
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.MenuItem
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.menu
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

/**
 * Toolbar dropdown with PlantUML's bundled themes. [current] is the shown diagram's `!theme`
 * (null = default look); picking one calls [onPick], which edits the source.
 */
@Composable
fun ThemeMenu(current: String?, enabled: Boolean, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        // Icon only, so the toolbar keeps room for copy/export; the tooltip names the theme.
        ToolButton(
            Icons.Outlined.Palette, tr(Res.string.preview_theme, current ?: tr(Res.string.preview_theme_default)),
            enabled = enabled, selected = current != null,
        ) { open = true }
        IdeDropdownMenu(open, onDismissRequest = { open = false }) { themeMenu(current, onPick) }
    }
}

/** Default first, then the bundled themes; the one in use is ticked. */
internal fun themeMenu(current: String?, onPick: (String?) -> Unit) = menu {
    group { add(MenuItem(text(Res.string.preview_theme_default), checked = current == null) { onPick(null) }) }
    group {
        PlantUmlLanguage.themes.forEach { t ->
            add(MenuItem(t, checked = t.equals(current, ignoreCase = true)) { onPick(t) })
        }
    }
}
