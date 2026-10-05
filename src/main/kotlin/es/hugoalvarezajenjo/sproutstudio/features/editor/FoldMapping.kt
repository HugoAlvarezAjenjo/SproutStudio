package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.ui.text.input.OffsetMapping

/**
 * Maps between the real text and the folded view. A click INSIDE a placeholder maps into the
 * hidden range, which the document treats as "expand this fold" (like clicking IntelliJ's `…`).
 */
class FoldMapping(private val folds: List<FoldRegion>, private val originalLength: Int) : OffsetMapping {
    private val tStart = IntArray(folds.size)
    private val transformedLength: Int

    init {
        var shift = 0
        folds.forEachIndexed { i, f ->
            tStart[i] = f.hideStart + shift
            shift += Folding.PLACEHOLDER.length - (f.hideEnd - f.hideStart)
        }
        transformedLength = originalLength + shift
    }

    override fun originalToTransformed(offset: Int): Int {
        var shift = 0
        folds.forEachIndexed { i, f ->
            if (offset <= f.hideStart) return offset + shift
            if (offset < f.hideEnd) return tStart[i] + Folding.PLACEHOLDER.length // hidden: after the placeholder
            shift += Folding.PLACEHOLDER.length - (f.hideEnd - f.hideStart)
        }
        return (offset + shift).coerceIn(0, transformedLength)
    }

    override fun transformedToOriginal(offset: Int): Int {
        var shift = 0
        folds.forEachIndexed { i, f ->
            val ts = tStart[i]
            val te = ts + Folding.PLACEHOLDER.length
            if (offset <= ts) return offset - shift
            if (offset < te) return if (f.hideEnd - f.hideStart > 1) f.hideStart + 1 else f.hideStart
            if (offset == te) return f.hideEnd
            shift += Folding.PLACEHOLDER.length - (f.hideEnd - f.hideStart)
        }
        return (offset - shift).coerceIn(0, originalLength)
    }
}
