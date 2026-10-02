package es.hugoalvarezajenjo.sproutstudio.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
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
import es.hugoalvarezajenjo.sproutstudio.model.Document
import es.hugoalvarezajenjo.sproutstudio.ui.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.ide

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
    LaunchedEffect(f.focusTick) {
        findValue = TextFieldValue(f.query.text, TextRange(0, f.query.text.length))
        runCatching { findFocus.requestFocus() }
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
                tooltip = if (f.replaceVisible) "Hide Replace" else "Replace (⌘R)",
                selected = f.replaceVisible,
            ) { f.replaceVisible = !f.replaceVisible }
            Field(
                value = findValue,
                onChange = {
                    findValue = it
                    if (it.text != f.query.text) { f.query = f.query.copy(text = it.text); f.notice = null }
                },
                placeholder = "Find",
                icon = true,
                error = result.error != null || (f.query.text.isNotEmpty() && result.matches.isEmpty()),
                modifier = Modifier.weight(1f).widthIn(max = 420.dp).then(keys { shift -> doc.findNext(forward = !shift) }).focusRequester(findFocus).testTag("find-field"),
            )
            Toggle("Cc", "Match Case", f.query.matchCase) { f.query = f.query.copy(matchCase = it) }
            Toggle("W", "Words", f.query.wholeWords) { f.query = f.query.copy(wholeWords = it) }
            Toggle(".*", "Regex", f.query.regex) { f.query = f.query.copy(regex = it) }
            Box(Modifier.widthIn(min = 56.dp).padding(horizontal = 4.dp)) {
                val (label, bad) = when {
                    result.error != null -> "Bad regex" to true
                    f.notice != null -> f.notice!! to false
                    f.query.text.isEmpty() -> "" to false
                    result.matches.isEmpty() -> "0 results" to true
                    current >= 0 -> "${current + 1}/${result.matches.size}" to false
                    else -> "${result.matches.size} results" to false
                }
                Text(label, fontSize = 12.sp, color = if (bad) c.error else c.textMuted, maxLines = 1, modifier = Modifier.testTag("find-count"))
            }
            ToolButton(Icons.Outlined.KeyboardArrowUp, tooltip = "Previous (⇧Enter)", enabled = result.matches.isNotEmpty()) { doc.findNext(false) }
            ToolButton(Icons.Outlined.KeyboardArrowDown, tooltip = "Next (Enter)", enabled = result.matches.isNotEmpty()) { doc.findNext(true) }
            ToolButton(Icons.Outlined.Close, tooltip = "Close (Esc)", onClick = onClose)
        }
        if (f.replaceVisible) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.width(28.dp))
                var replValue by remember { mutableStateOf(TextFieldValue(f.replacement)) }
                Field(
                    value = replValue,
                    onChange = { replValue = it; f.replacement = it.text },
                    placeholder = if (f.query.regex) "Replace ($1 for groups)" else "Replace",
                    icon = false,
                    error = false,
                    modifier = Modifier.weight(1f).widthIn(max = 420.dp).then(keys { doc.replaceNext() }).testTag("replace-field"),
                )
                val any = result.matches.isNotEmpty()
                ToolButton(null, label = "Replace", enabled = any) { doc.replaceNext() }
                ToolButton(null, label = "Replace All", enabled = any) { doc.replaceAll() }
            }
        }
    }
}

@Composable
private fun Field(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    placeholder: String,
    icon: Boolean,
    error: Boolean,
    modifier: Modifier,
) {
    val c = ide
    var focused by remember { mutableStateOf(false) }
    val border = when {
        error -> c.error
        focused -> c.accent
        else -> c.popupBorder
    }
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = es.hugoalvarezajenjo.sproutstudio.ui.editorTextStyle.copy(color = c.text, fontSize = 13.sp),
        cursorBrush = SolidColor(c.text),
        modifier = modifier.widthIn(min = 120.dp).onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier.height(28.dp).background(c.editor, RoundedCornerShape(5.dp))
                    .border(1.dp, border, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (icon) Icon(Icons.Outlined.Search, null, tint = c.textMuted, modifier = Modifier.size(14.dp))
                Box(Modifier.weight(1f)) {
                    if (value.text.isEmpty()) Text(placeholder, color = c.textDisabled, fontSize = 13.sp, maxLines = 1)
                    inner()
                }
            }
        },
    )
}

@Composable
private fun Toggle(label: String, tooltip: String, on: Boolean, onChange: (Boolean) -> Unit) {
    ToolButton(null, tooltip = tooltip, label = label, selected = on) { onChange(!on) }
}
