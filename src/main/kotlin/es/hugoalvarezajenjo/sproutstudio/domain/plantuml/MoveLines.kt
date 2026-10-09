package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * ⌥↑ / ⌥↓ : move the line under the caret, or every line the selection touches, one line up or
 * down. The selection moves with the lines. At the top or bottom of the text nothing changes.
 */
object MoveLines {

    fun up(v: TextFieldValue): TextFieldValue {
        val (s, e) = EditOps.selectedLineRange(v)
        if (s == 0) return v
        val text = v.text
        val prevStart = TextLines.startAt(text, s - 1)
        val prev = text.substring(prevStart, s - 1)
        val newText = text.substring(0, prevStart) + text.substring(s, e) + "\n" + prev + text.substring(e)
        return shifted(v, newText, -(prev.length + 1))
    }

    fun down(v: TextFieldValue): TextFieldValue {
        val (s, e) = EditOps.selectedLineRange(v)
        val text = v.text
        if (e >= text.length) return v
        val nextEnd = TextLines.endAt(text, e + 1)
        val next = text.substring(e + 1, nextEnd)
        val newText = text.substring(0, s) + next + "\n" + text.substring(s, e) + text.substring(nextEnd)
        return shifted(v, newText, next.length + 1)
    }

    /** The selection moved [by] with its lines. A selection ending at the start of the next line
     *  can land past the end when that line was the last one, hence the clamp. */
    private fun shifted(v: TextFieldValue, text: String, by: Int) = TextFieldValue(
        text,
        TextRange((v.selection.start + by).coerceIn(0, text.length), (v.selection.end + by).coerceIn(0, text.length)),
    )
}
