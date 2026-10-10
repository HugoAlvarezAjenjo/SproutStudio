package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.core.os.shortcutHint
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.common_close_esc
import es.hugoalvarezajenjo.sproutstudio.resources.find_bad_regex
import es.hugoalvarezajenjo.sproutstudio.resources.find_hide_replace
import es.hugoalvarezajenjo.sproutstudio.resources.find_next
import es.hugoalvarezajenjo.sproutstudio.resources.find_no_results
import es.hugoalvarezajenjo.sproutstudio.resources.find_placeholder
import es.hugoalvarezajenjo.sproutstudio.resources.find_previous
import es.hugoalvarezajenjo.sproutstudio.resources.find_replace
import es.hugoalvarezajenjo.sproutstudio.resources.find_replace_all
import es.hugoalvarezajenjo.sproutstudio.resources.find_replace_regex
import es.hugoalvarezajenjo.sproutstudio.resources.find_replaced
import es.hugoalvarezajenjo.sproutstudio.resources.find_results
import es.hugoalvarezajenjo.sproutstudio.resources.find_show_replace
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

import es.hugoalvarezajenjo.sproutstudio.domain.text.FindResult

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.SearchField
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide

/**
 * IntelliJ-style find / replace bar shown above the editor.
 * Enter = next, Shift+Enter = previous, Esc = close; in the replace field Enter = replace.
 */
@Composable
fun FindBar(doc: Document, result: FindResult, current: Int, onClose: () -> Unit) {
    val f = doc.find
    val c = ide
    val findFocus = remember { FocusRequester() }
    var findValue by remember { mutableStateOf(TextFieldValue(f.query.text)) }

    // ⌘F / ⌘R while the bar is open: refocus and select the query, like IntelliJ.
    LaunchedEffect(f) {
        fun focusQuery() {
            findValue = TextFieldValue(f.query.text, TextRange(0, f.query.text.length))
            runCatching { findFocus.requestFocus() }
        }
        focusQuery() // the bar just opened
        f.focusRequests.collect { focusQuery() }
    }

    fun keys(onEnter: (shift: Boolean) -> Unit) = Modifier.onPreviewKeyEvent { ev ->
        if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (ev.key) {
            Key.Enter, Key.NumPadEnter -> { onEnter(ev.isShiftPressed); true }
            Key.Escape -> { onClose(); true }
            else -> false
        }
    }

    Column(Modifier.fillMaxWidth().background(c.panel).padding(horizontal = 8.dp, vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ToolButton(
                Icons.Outlined.SwapHoriz,
                tooltip = if (f.replaceVisible) tr(Res.string.find_hide_replace) else tr(Res.string.find_show_replace, shortcutHint("R")),
                selected = f.replaceVisible,
            ) { f.replaceVisible = !f.replaceVisible }
            SearchField(
                value = findValue,
                onChange = {
                    findValue = it
                    if (it.text != f.query.text) { f.query = f.query.copy(text = it.text); f.clearReplaced() }
                },
                placeholder = tr(Res.string.find_placeholder),
                icon = true,
                error = result.error != null || (f.query.text.isNotEmpty() && result.matches.isEmpty()),
                modifier = Modifier.weight(1f).widthIn(max = 420.dp).then(keys { shift -> doc.findNext(forward = !shift) }).focusRequester(findFocus).testTag("find-field"),
            )
            FindOptions(f.query) { f.query = it }
            Box(Modifier.widthIn(min = 56.dp).padding(horizontal = 4.dp)) {
                val (label, bad) = when {
                    result.error != null -> tr(Res.string.find_bad_regex) to true
                    f.replacedCount != null -> tr(Res.string.find_replaced, f.replacedCount ?: 0) to false
                    f.query.text.isEmpty() -> "" to false
                    result.matches.isEmpty() -> tr(Res.string.find_no_results) to true
                    current >= 0 -> "${current + 1}/${result.matches.size}" to false
                    else -> tr(Res.plurals.find_results, result.matches.size, result.matches.size) to false
                }
                Text(label, fontSize = 12.sp, color = if (bad) c.error else c.textMuted, maxLines = 1, modifier = Modifier.testTag("find-count"))
            }
            ToolButton(Icons.Outlined.KeyboardArrowUp, tooltip = tr(Res.string.find_previous), enabled = result.matches.isNotEmpty()) { doc.findNext(false) }
            ToolButton(Icons.Outlined.KeyboardArrowDown, tooltip = tr(Res.string.find_next), enabled = result.matches.isNotEmpty()) { doc.findNext(true) }
            ToolButton(Icons.Outlined.Close, tooltip = tr(Res.string.common_close_esc), onClick = onClose)
        }
        if (f.replaceVisible) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(28.dp))
                var replValue by remember { mutableStateOf(TextFieldValue(f.replacement)) }
                SearchField(
                    value = replValue,
                    onChange = { replValue = it; f.replacement = it.text },
                    placeholder = if (f.query.regex) tr(Res.string.find_replace_regex) else tr(Res.string.find_replace),
                    icon = false,
                    error = false,
                    modifier = Modifier.weight(1f).widthIn(max = 420.dp).then(keys { doc.replaceNext() }).testTag("replace-field"),
                )
                val any = result.matches.isNotEmpty()
                ToolButton(null, label = tr(Res.string.find_replace), enabled = any) { doc.replaceNext() }
                ToolButton(null, label = tr(Res.string.find_replace_all), enabled = any) { doc.replaceAll() }
            }
        }
    }
}
