package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Asks for a name (new file, rename file, rename symbol): one field, a hint line that turns into
 * the error when [check] rejects the text, and Enter / Escape for confirm / cancel.
 */
@Composable
fun NameDialog(
    title: String,
    /** Something new: an invalid suggested name is flagged before any edit. */
    creating: Boolean,
    label: String,
    confirm: String,
    cancel: String,
    initial: String,
    /** The field's text, owned by the caller (it may outlive this dialog). */
    state: MutableState<TextFieldValue>,
    hint: String?,
    check: (String) -> String?,
    onDismiss: () -> Unit,
    /** Where the operation runs: the window's, so it isn't cut short by recomposition. */
    scope: CoroutineScope,
    /** Extra controls under the field (the template picker). */
    extra: @Composable () -> Unit = {},
    /** Runs the operation; returns an error to show, or null when done. */
    onConfirm: suspend (String) -> String?,
) {
    val c = ide
    var value by state
    var failed by remember { mutableStateOf<String?>(null) }
    val invalid = check(value.text)
    val error = failed ?: invalid.takeIf { value.text != initial || creating }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    var busy by remember { mutableStateOf(false) }
    fun submit() {
        if (invalid != null || busy) return
        busy = true
        scope.launch {
            failed = onConfirm(value.text)
            busy = false
            if (failed == null) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.popup,
        shape = RoundedCornerShape(10.dp),
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.width(360.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; failed = null },
                    label = { Text(label) },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("name-field")
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { submit(); true }
                            else if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onDismiss(); true }
                            else false
                        },
                )
                Text(
                    error ?: hint.orEmpty(),
                    color = if (error != null) c.error else c.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("name-hint"),
                )
                extra()
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = invalid == null, modifier = Modifier.testTag("name-confirm")) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancel) } },
    )
}
