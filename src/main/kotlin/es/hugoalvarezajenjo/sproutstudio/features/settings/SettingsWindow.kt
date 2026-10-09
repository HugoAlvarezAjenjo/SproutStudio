package es.hugoalvarezajenjo.sproutstudio.features.settings

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Templates
import es.hugoalvarezajenjo.sproutstudio.features.editor.Highlighter
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceDeps
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.AppIcon
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.LocalEditorTextStyle
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide

/** The app's Settings window: theme, diagram preview, editor font, saving and recents. */
@Composable
fun SettingsWindow(deps: WorkspaceDeps, onClose: () -> Unit) {
    val vm = remember(deps) { SettingsViewModel(deps) }
    Window(
        onCloseRequest = onClose,
        title = "Settings",
        icon = AppIcon.painter,
        resizable = true,
        state = rememberWindowState(size = DpSize(760.dp, 540.dp)),
        onPreviewKeyEvent = { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false },
    ) {
        PumlTheme(dark = vm.darkTheme, editorFontSize = vm.fontSize) {
            Surface(Modifier.fillMaxSize(), color = ide.editor) { SettingsContent(vm) }
        }
    }
}

internal enum class Section(val title: String, val icon: ImageVector) {
    APPEARANCE("Appearance", Icons.Outlined.Palette),
    EDITOR("Editor", Icons.Outlined.Code),
    GENERAL("General", Icons.Outlined.Tune),
}

@Composable
internal fun SettingsContent(vm: SettingsViewModel, initial: Section = Section.APPEARANCE) {
    val c = ide
    var section by rememberSaveable { mutableStateOf(initial) }
    Row(Modifier.fillMaxSize()) {
        // Left: the sections, IntelliJ-style.
        Column(
            Modifier.width(200.dp).fillMaxHeight().background(c.panel).padding(horizontal = 10.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Section.entries.forEach { s -> SectionRow(s, selected = s == section) { section = s } }
            Spacer(Modifier.weight(1f))
            ToolButton(null, label = "Restore defaults", enabled = !vm.allDefaults) { vm.resetToDefaults() }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
        // Right: the chosen section's settings.
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Text(section.title, style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(20.dp))
            when (section) {
                Section.APPEARANCE -> AppearanceSection(vm)
                Section.EDITOR -> EditorSection(vm)
                Section.GENERAL -> GeneralSection(vm)
            }
        }
    }
}

@Composable
private fun AppearanceSection(vm: SettingsViewModel) {
    SettingRow("Theme", "The colours of the whole app. The macOS title bar follows it after a restart.") {
        Segmented(
            listOf(Choice("Dark", Icons.Outlined.DarkMode), Choice("Light", Icons.Outlined.LightMode)),
            selected = if (vm.darkTheme) 0 else 1,
            tag = "theme",
        ) { vm.setDarkTheme(it == 0) }
    }
    SettingRow("Diagram preview", "Recolour diagrams dark in the preview. Exports keep the original colours.") {
        Segmented(
            listOf(Choice("Light", Icons.Outlined.LightMode), Choice("Dark", Icons.Outlined.DarkMode)),
            selected = if (vm.darkDiagram) 1 else 0,
            tag = "diagram",
        ) { vm.setDarkDiagram(it == 1) }
    }
}

@Composable
private fun EditorSection(vm: SettingsViewModel) {
    val c = ide
    SettingRow("Font size", "Text size in the code editor.") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolButton(Icons.Outlined.Remove, "Smaller", enabled = vm.canShrinkFont) { vm.changeFontSize(-1f) }
            Text(
                "${vm.fontSize.toInt()} pt", color = c.text, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.widthIn(min = 44.dp).testTag("font-size"),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            ToolButton(Icons.Outlined.Add, "Larger", enabled = vm.canGrowFont) { vm.changeFontSize(1f) }
        }
    }
    // A live sample, in the editor's real font, colours and size.
    val sample = remember { Templates.all.first().previewSource.lines().take(6).joinToString("\n") }
    val highlighted = remember(sample, c) { Highlighter.highlight(sample, c.syntax, emptySet(), -1) }
    Box(
        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp).clip(RoundedCornerShape(8.dp))
            .border(1.dp, c.popupBorder, RoundedCornerShape(8.dp)).background(c.editor).padding(14.dp),
    ) {
        Text(highlighted, style = LocalEditorTextStyle.current.copy(color = c.syntax.symbol), softWrap = false)
    }
}

@Composable
private fun GeneralSection(vm: SettingsViewModel) {
    SettingRow("Save automatically", "Save diagrams a second after you stop typing, and when you switch tab or app.") {
        Toggle(vm.autoSave, tag = "autosave") { vm.setAutoSave(it) }
    }
    val n = vm.recentCount
    SettingRow(
        "Recent projects",
        when (n) { 0 -> "No recent projects."; 1 -> "1 folder on the welcome screen."; else -> "$n folders on the welcome screen." },
    ) {
        ToolButton(null, label = "Clear list", enabled = n > 0) { vm.clearRecents() }
    }
}

/** One setting: title and explanation on the left, its control on the right. */
@Composable
private fun SettingRow(title: String, description: String, control: @Composable () -> Unit) {
    val c = ide
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Spacer(Modifier.height(2.dp))
            Text(description, fontSize = 12.sp, color = c.textMuted)
        }
        control()
    }
}

@Composable
private fun SectionRow(s: Section, selected: Boolean, onClick: () -> Unit) {
    val c = ide
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(6.dp))
            .background(if (selected) c.selection else if (hovered) c.hover else Color.Transparent)
            .hoverable(interaction)
            .selectable(selected, interactionSource = interaction, indication = null, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp)
            .testTag("section:${s.name}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(s.icon, null, tint = if (selected) c.text else c.textMuted, modifier = Modifier.size(16.dp))
        Text(s.title, style = MaterialTheme.typography.labelLarge, color = c.text)
    }
}

private class Choice(val label: String, val icon: ImageVector)

/** A segmented control: one of a few options, the chosen one highlighted. */
@Composable
private fun Segmented(options: List<Choice>, selected: Int, tag: String, onSelect: (Int) -> Unit) {
    val c = ide
    Row(
        Modifier.clip(RoundedCornerShape(7.dp)).background(c.panel).border(1.dp, c.border, RoundedCornerShape(7.dp)).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Row(
                Modifier.height(26.dp).clip(RoundedCornerShape(5.dp))
                    .background(if (on) c.accent else Color.Transparent)
                    .selectable(on, role = Role.RadioButton) { onSelect(i) }
                    .padding(horizontal = 10.dp)
                    .testTag("$tag:${o.label}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val fg = if (on) c.onAccent else c.text
                Icon(o.icon, null, tint = fg, modifier = Modifier.size(14.dp))
                Text(o.label, style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** An on/off switch. */
@Composable
private fun Toggle(checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val c = ide
    val x by animateDpAsState(if (checked) 16.dp else 2.dp)
    Box(
        Modifier.size(34.dp, 20.dp).clip(CircleShape)
            .background(if (checked) c.accent else c.hover)
            .border(1.dp, if (checked) c.accent else c.popupBorder, CircleShape)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .testTag(tag),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.offset(x = x).size(16.dp).clip(CircleShape).background(if (checked) c.onAccent else c.textMuted))
    }
}
