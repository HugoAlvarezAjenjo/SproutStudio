package es.hugoalvarezajenjo.sproutstudio.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** Pure editing helpers; everything here is unit-testable without UI. */
object EditOps {

    const val INDENT = "  "

    private val opensBlock = Regex(
        """(\{\s*$)|^\s*(alt|else|opt|loop|par|break|critical|group|box|if|elseif|while|repeat|fork|split|partition|switch|case|note(?!.*:)|legend|package|namespace|rectangle|node|state|together)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val closesBlock = Regex("""^\s*(\}|end\b|endif|endwhile|end fork|end split|endswitch|end note|endlegend|end box|else\b|elseif\b)""", RegexOption.IGNORE_CASE)

    private fun lineStart(text: String, pos: Int) = text.lastIndexOf('\n', (pos - 1).coerceAtLeast(-1)).let { if (pos == 0) 0 else it + 1 }
    private fun lineEnd(text: String, pos: Int) = text.indexOf('\n', pos).let { if (it < 0) text.length else it }

    /**
     * Enter: if the current line is a block closer (`end`, `}`, `else`…) first pull it back one
     * level, then keep the line's indent and add one level after a block opener.
     */
    fun newline(v: TextFieldValue): TextFieldValue {
        val dedented = dedentCloser(v)
        val text = dedented.text
        val caret = dedented.selection.min
        val ls = lineStart(text, caret)
        val beforeCaret = text.substring(ls, caret)
        val indent = beforeCaret.takeWhile { it == ' ' || it == '\t' }
        val extra = if (opensBlock.containsMatchIn(beforeCaret)) INDENT else ""
        return replaceSelection(dedented, "\n" + indent + extra)
    }

    /** If the line up to the caret is a closer and is indented, remove one indent level. */
    fun dedentCloser(v: TextFieldValue): TextFieldValue {
        val text = v.text
        val caret = v.selection.min
        val ls = lineStart(text, caret)
        val line = text.substring(ls, caret)
        if (!line.startsWith(INDENT) || !closesBlock.containsMatchIn(line)) return v
        // A closer sits at its opener's level: the previous line's level if that line is the
        // opener itself, otherwise one level less than the block body.
        val prev = text.substring(0, ls).trimEnd('\n').substringAfterLast('\n')
        val prevIndent = prev.takeWhile { it == ' ' }.length
        val expected = if (opensBlock.containsMatchIn(prev)) prevIndent else (prevIndent - INDENT.length).coerceAtLeast(0)
        val ownIndent = line.takeWhile { it == ' ' }.length
        if (ownIndent <= expected) return v
        val newText = text.substring(0, ls) + line.removePrefix(INDENT) + text.substring(caret)
        return TextFieldValue(newText, TextRange(caret - INDENT.length))
    }

    fun indent(v: TextFieldValue): TextFieldValue {
        if (v.selection.collapsed) return replaceSelection(v, INDENT)
        return mapLines(v) { INDENT + it }
    }

    fun outdent(v: TextFieldValue): TextFieldValue = mapLines(v) { line ->
        when {
            line.startsWith(INDENT) -> line.removePrefix(INDENT)
            line.startsWith("\t") || line.startsWith(" ") -> line.substring(1)
            else -> line
        }
    }

    /** Cmd+/ : toggle PlantUML line comments (`'`). */
    fun toggleComment(v: TextFieldValue): TextFieldValue {
        val (s, e) = selectedLineRange(v)
        val lines = v.text.substring(s, e).split('\n')
        val allCommented = lines.filter { it.isNotBlank() }.all { it.trimStart().startsWith("'") }
        return mapLines(v) { line ->
            if (line.isBlank()) line
            else if (allCommented) {
                val i = line.indexOf('\'')
                line.removeRange(i, if (line.getOrNull(i + 1) == ' ') i + 2 else i + 1)
            } else {
                val ind = line.takeWhile { it == ' ' || it == '\t' }
                "$ind' ${line.substring(ind.length)}"
            }
        }
    }

    /** Cmd+D : duplicate the current line(s) below. */
    fun duplicateLines(v: TextFieldValue): TextFieldValue {
        val (s, e) = selectedLineRange(v)
        val block = v.text.substring(s, e)
        val newText = v.text.substring(0, e) + "\n" + block + v.text.substring(e)
        val shift = block.length + 1
        return TextFieldValue(newText, TextRange(v.selection.start + shift, v.selection.end + shift))
    }

    fun selectLine(text: String, line: Int): TextRange {
        var start = 0
        repeat((line - 1).coerceAtLeast(0)) {
            val nl = text.indexOf('\n', start)
            if (nl < 0) return TextRange(text.length)
            start = nl + 1
        }
        return TextRange(start, lineEnd(text, start))
    }

    private fun selectedLineRange(v: TextFieldValue): Pair<Int, Int> {
        val text = v.text
        val s = lineStart(text, v.selection.min)
        val endPos = if (!v.selection.collapsed && v.selection.max > 0 && text.getOrNull(v.selection.max - 1) == '\n') v.selection.max - 1 else v.selection.max
        return s to lineEnd(text, endPos.coerceAtLeast(s))
    }

    private fun mapLines(v: TextFieldValue, f: (String) -> String): TextFieldValue {
        val (s, e) = selectedLineRange(v)
        val old = v.text.substring(s, e)
        val lines = old.split('\n')
        val mapped = lines.map(f)
        val newBlock = mapped.joinToString("\n")
        val newText = v.text.substring(0, s) + newBlock + v.text.substring(e)
        val firstDelta = mapped.first().length - lines.first().length
        return if (v.selection.collapsed) {
            TextFieldValue(newText, TextRange((v.selection.start + firstDelta).coerceIn(s, s + newBlock.length)))
        } else {
            TextFieldValue(newText, TextRange(s, s + newBlock.length))
        }
    }

    private fun replaceSelection(v: TextFieldValue, insert: String): TextFieldValue {
        val t = v.text.substring(0, v.selection.min) + insert + v.text.substring(v.selection.max)
        return TextFieldValue(t, TextRange(v.selection.min + insert.length))
    }
}
