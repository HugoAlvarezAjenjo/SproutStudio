package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.domain.git.LineChange
import es.hugoalvarezajenjo.sproutstudio.domain.git.LineDiff
import es.hugoalvarezajenjo.sproutstudio.features.palette.shortcutHint
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.change_added
import es.hugoalvarezajenjo.sproutstudio.resources.change_deleted
import es.hugoalvarezajenjo.sproutstudio.resources.change_modified
import es.hugoalvarezajenjo.sproutstudio.resources.common_close_esc
import es.hugoalvarezajenjo.sproutstudio.resources.editor_rollback
import es.hugoalvarezajenjo.sproutstudio.resources.editor_rollback_tooltip
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.LocalEditorTextStyle
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

/**
 * IntelliJ's change popup: a small toolbar and the diff of this block only (committed lines in
 * red, current ones in green, the differing part of a modified line marked stronger).
 */
@Composable
internal fun ChangePopup(
    change: LineChange,
    rows: List<LineDiff.HunkRow>,
    onRollback: () -> Unit,
    onClose: () -> Unit,
) {
    val c = ide
    val n = change.end - change.start
    val title = when (change.type) {
        LineChange.Type.ADDED -> tr(Res.plurals.change_added, n, n)
        LineChange.Type.DELETED -> (change.baseEnd - change.baseStart).let { tr(Res.plurals.change_deleted, it, it) }
        LineChange.Type.MODIFIED -> tr(Res.plurals.change_modified, n, n)
    }
    val addedBg = c.gutterAdded.copy(alpha = if (c.isDark) 0.22f else 0.30f)
    val addedHi = c.gutterAdded.copy(alpha = if (c.isDark) 0.50f else 0.65f)
    val deletedBg = c.vcsUnversioned.copy(alpha = if (c.isDark) 0.18f else 0.14f)
    val deletedHi = c.vcsUnversioned.copy(alpha = if (c.isDark) 0.42f else 0.32f)
    Surface(
        shape = RoundedCornerShape(6.dp), shadowElevation = 12.dp, color = c.popup,
        modifier = Modifier.widthIn(min = 320.dp, max = 640.dp).border(1.dp, c.popupBorder, RoundedCornerShape(6.dp))
            .onPreviewKeyEvent { e -> if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onClose(); true } else false }
            .testTag("change-popup"),
    ) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            Row(
                Modifier.fillMaxWidth().height(EditorMetrics.hunkHeader).padding(start = 10.dp, end = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, fontSize = 12.sp, color = c.textMuted, modifier = Modifier.weight(1f).padding(end = 12.dp))
                ToolButton(
                    Icons.AutoMirrored.Outlined.Undo,
                    tooltip = tr(Res.string.editor_rollback_tooltip, shortcutHint("Z", alt = true)),
                    label = tr(Res.string.editor_rollback),
                ) { onRollback() }
                ToolButton(Icons.Outlined.Close, tr(Res.string.common_close_esc)) { onClose() }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.popupBorder))
            Column(
                Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                    .testTag("change-popup-diff"),
            ) {
                // Full line boxes (no first/last-line trim), so each row is exactly one editor line tall.
                val rowStyle = LocalEditorTextStyle.current.copy(
                    lineHeightStyle = LineHeightStyle(
                        LineHeightStyle.Alignment.Center,
                        LineHeightStyle.Trim.None,
                    ),
                )
                rows.forEach { row ->
                    val deleted = row.kind == LineDiff.Row.Kind.DELETED
                    val text = buildAnnotatedString {
                        append(row.text.ifEmpty { " " })
                        row.changed?.let { r ->
                            addStyle(SpanStyle(background = if (deleted) deletedHi else addedHi), r.first, (r.last + 1).coerceAtMost(row.text.length))
                        }
                    }
                    Row(Modifier.defaultMinSize(minWidth = 320.dp).background(if (deleted) deletedBg else addedBg)) {
                        Text(
                            if (deleted) "−" else "+",
                            style = rowStyle.copy(color = if (deleted) c.vcsUnversioned else c.vcsAdded),
                            modifier = Modifier.width(16.dp).padding(start = 4.dp),
                        )
                        Text(text, style = rowStyle.copy(color = c.syntax.symbol), maxLines = 1, softWrap = false, modifier = Modifier.padding(end = 16.dp))
                    }
                }
            }
        }
    }
}
