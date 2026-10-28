package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.OffsetMapping
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.IncludeReader
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Navigation
import es.hugoalvarezajenjo.sproutstudio.features.document.Document

/**
 * Where the editor's text sits: the latest text layout, the fold mapping it was laid out with,
 * and the measured sizes. It outlives a switch of document, unlike [CodeEditorState]: the text
 * field's node stays, so its size callbacks don't fire again for the new document.
 */
@Stable
internal class EditorLayout {
    var text by mutableStateOf<TextLayoutResult?>(null)
    /**
     * Folded view <-> real text. Set on every composition and read later by effects and input
     * handlers; nothing reads it while composing, so it needn't be state.
     */
    var mapping: OffsetMapping = OffsetMapping.Identity
    var fieldHeightPx by mutableStateOf(0)
    var viewportPx by mutableStateOf(0)

    /** Layout offset of [offset] in a real text of [length] characters. */
    fun toLayout(offset: Int, length: Int) = mapping.originalToTransformed(offset.coerceIn(0, length))

    /** Real text offset under [pos] (field content coordinates), if it is on a line at all. */
    fun offsetAt(pos: Offset, length: Int): Int? {
        val l = text ?: return null
        val line = l.getLineForOffset(l.getOffsetForPosition(pos))
        if (pos.y < l.getLineTop(line) || pos.y > l.getLineBottom(line)) return null
        return nearestOffsetAt(pos, length)
    }

    /** Real text offset nearest to [pos] (field content coordinates), even off the text. */
    fun nearestOffsetAt(pos: Offset, length: Int): Int? {
        val l = text ?: return null
        return mapping.transformedToOriginal(l.getOffsetForPosition(pos)).coerceIn(0, length)
    }

    /** The span a ⌘-click at [pos] would follow, when the pointer is really over it. */
    fun linkAt(pos: Offset, doc: Document, includes: IncludeReader): IntRange? {
        val l = text ?: return null
        val length = doc.text.length
        val r = offsetAt(pos, length)?.let { Navigation.linkAt(doc.text, it, doc.file?.absolutePath, includes) } ?: return null
        val x1 = l.getHorizontalPosition(l.safeOffset(toLayout(r.first, length)), true)
        val x2 = l.getHorizontalPosition(l.safeOffset(toLayout(r.last + 1, length)), true)
        return r.takeIf { pos.x in x1..x2 }
    }
}

/**
 * The text layout lags one frame behind the text: right after a keystroke at the very end of
 * the file, the caret is one past the end of the *previous* layout. Clamp before asking it.
 */
internal fun TextLayoutResult.safeOffset(offset: Int) = offset.coerceIn(0, layoutInput.text.length)
