package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The single-line field of the find bar and Find in Files: red border on [error], accent when focused. */
@Composable
fun SearchField(
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
        textStyle = editorTextStyle.copy(color = c.text, fontSize = 13.sp),
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
