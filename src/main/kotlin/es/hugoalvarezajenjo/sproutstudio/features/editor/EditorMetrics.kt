package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.ui.unit.dp

/**
 * Sizes the editor's pieces share. The gutter, the line highlights and the popups anchored to the
 * caret all line up with the text through the text field's padding, so it lives here once.
 */
internal object EditorMetrics {
    val gutter = 58.dp
    /** Git change bar at the gutter's right edge, next to the text. */
    val changeBar = 3.dp
    /** Height of the change popup's toolbar (the diff rows start right below it). */
    val hunkHeader = 34.dp

    val textStart = 12.dp
    val textEnd = 24.dp
    val textTop = 10.dp
    /** Room to scroll the last line up from the bottom edge. */
    val textBottom = 120.dp
}
