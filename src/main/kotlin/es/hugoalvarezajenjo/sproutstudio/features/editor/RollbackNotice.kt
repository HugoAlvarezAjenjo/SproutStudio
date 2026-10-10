package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.editor_rolled_back
import es.hugoalvarezajenjo.sproutstudio.resources.editor_undo
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr
import kotlinx.coroutines.delay

/** How long "Lines rolled back · Undo" stays up. */
private const val SHOWN_MS = 8000L

/** "Lines rolled back · Undo", while the rollback that left [before] behind can still be undone. */
@Composable
internal fun RollbackNotice(doc: Document, before: TextFieldValue, modifier: Modifier = Modifier, onUndo: () -> Unit) {
    val c = ide
    LaunchedEffect(before) {
        delay(SHOWN_MS)
        doc.dropRollbackUndo(only = before)
    }
    Surface(
        shape = RoundedCornerShape(8.dp), color = c.popup, shadowElevation = 8.dp,
        modifier = modifier.padding(16.dp).border(1.dp, c.popupBorder, RoundedCornerShape(8.dp)),
    ) {
        Row(
            Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(tr(Res.string.editor_rolled_back), fontSize = 12.sp, color = c.text)
            ToolButton(null, label = tr(Res.string.editor_undo)) { onUndo() }
        }
    }
}
