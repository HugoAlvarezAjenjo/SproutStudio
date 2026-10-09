package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Reformat Code (⌥⌘L). Only touches whitespace, and only where PlantUML ignores it:
 * - each line is indented by its block depth ([BlockSyntax]); `@startuml` content stays at
 *   column 0, `else`/`case`/`fork again` sit at their block's level;
 * - free text (notes, legends, titles...) keeps its own relative indentation and moves with
 *   its opener; block comments and multi-line activity labels are left as they are;
 * - one space either side of a relation arrow (`A->B` becomes `A -> B`), when that can't change
 *   which arrow PlantUML reads;
 * - in class bodies, no gap after a member's visibility (`+    size` becomes `+size`), when
 *   PlantUML still reads it as visibility;
 * - trailing whitespace goes, runs of blank lines become one, and the text ends in one `\n`.
 *
 * Diagrams other than `@startuml` (`@startjson`, `@startyaml`, `@startmindmap`...) are copied
 * as they are: indentation means something there.
 */
object PlantUmlFormatter {

    /**
     * Format [v]'s whole text or, with a selection, just the selected lines. The caret (or
     * selection) stays on the same code.
     */
    fun format(v: TextFieldValue): TextFieldValue {
        val text = v.text
        val starts = TextLines.starts(text)
        val range = if (v.selection.collapsed) null else {
            val endOffset = if (v.selection.max > v.selection.min && text.getOrNull(v.selection.max - 1) == '\n') v.selection.max - 1 else v.selection.max
            TextLines.of(starts, v.selection.min)..TextLines.of(starts, endOffset)
        }
        val out = formatLines(text, range)
        val newText = out.joinToString("\n") { it.text }
        if (newText == text) return v
        val newStarts = IntArray(out.size)
        for (i in 1 until out.size) newStarts[i] = newStarts[i - 1] + out[i - 1].text.length + 1

        fun map(offset: Int): Int {
            val line = TextLines.of(starts, offset)
            val col = offset - starts[line]
            // The first output line that came from [line] or a later one (dropped blanks map forward).
            val o = out.indexOfFirst { it.source >= line }.let { if (it < 0) out.lastIndex else it }
            if (out[o].source != line) return newStarts[o]
            val oldIndent = text.substring(starts[line]).takeWhile { it == ' ' || it == '\t' }.length
            val newLine = out[o].text
            val newIndent = newLine.takeWhile { it == ' ' || it == '\t' }.length
            val newCol = if (col <= oldIndent) minOf(col, newIndent) else newIndent + (col - oldIndent)
            return newStarts[o] + newCol.coerceIn(0, newLine.length)
        }

        val sel = if (v.selection.collapsed) TextRange(map(v.selection.start))
        else if (range != null) {
            // Keep the formatted lines selected, so a second ⌥⌘L does the same again.
            val first = out.indexOfFirst { it.source >= range.first }
            val last = out.indexOfLast { it.source <= range.last }
            if (first < 0 || last < first) TextRange(map(v.selection.start))
            else TextRange(newStarts[first], newStarts[last] + out[last].text.length)
        } else TextRange(map(v.selection.start), map(v.selection.end))
        return TextFieldValue(newText, sel)
    }

    /** The formatted text. */
    fun format(text: String): String = formatLines(text, null).joinToString("\n") { it.text }

    /** An output line and the source line it came from. */
    private class Out(val text: String, val source: Int)

    /** An open block; [members] when it's a class body (`class X {`), whose lines are members. */
    private class Block(val kind: BlockKind, val members: Boolean = false)

    private fun formatLines(text: String, only: IntRange?): List<Out> {
        val lines = BlockSyntax.scan(text)
        val out = ArrayList<Out>(lines.size)
        val stack = ArrayList<Block>()
        var verbatimDiagram = false
        val bodyIndent = bodyIndents(lines)
        // How far the current free-text block's opener moved, for its body lines.
        var bodyShift = 0

        fun depth() = stack.count { it.kind != BlockKind.DIAGRAM }
        fun indent(n: Int) = EditOps.INDENT.repeat(n.coerceAtLeast(0))
        fun leading(s: String) = s.takeWhile { it == ' ' || it == '\t' }

        for (line in lines) {
            val inRange = only == null || line.index in only
            val raw = line.raw
            val t = raw.trim()
            val role = line.role
            if (verbatimDiagram && role != LineRole.Close(BlockKind.DIAGRAM)) {
                out += Out(raw, line.index)
                continue
            }
            val formatted: String = when (role) {
                is LineRole.Open -> {
                    val at = if (role.kind == BlockKind.DIAGRAM) { stack.clear(); 0 } else depth()
                    val s = indent(at) + t
                    if (role.kind == BlockKind.DIAGRAM) verbatimDiagram = !isUml(t)
                    if (role.kind in BlockSyntax.textBodies) {
                        // Never further left than the body's least indented line: PlantUML keeps
                        // the body's relative indentation, so that must not change.
                        bodyShift = if (inRange) maxOf(leading(s).length - leading(raw).length, -(bodyIndent[line.index] ?: 0)) else 0
                    }
                    stack += Block(role.kind, role.kind == BlockKind.BRACE && BlockSyntax.opensMembers(t))
                    s
                }
                is LineRole.Close -> {
                    if (role.kind == BlockKind.DIAGRAM) {
                        stack.clear()
                        verbatimDiagram = false
                    } else {
                        val i = stack.indexOfLast { it.kind == role.kind }
                        if (i >= 0) while (stack.size > i) stack.removeAt(stack.lastIndex)
                    }
                    indent(depth()) + t
                }
                is LineRole.Middle -> indent(if (stack.lastOrNull()?.kind in role.of) depth() - 1 else depth()) + t
                LineRole.Body -> {
                    val kind = stack.lastOrNull()?.kind
                    if (kind == null || kind == BlockKind.COMMENT || kind !in BlockSyntax.textBodies || t.isEmpty()) raw.trimEnd()
                    else shift(raw.trimEnd(), bodyShift)
                }
                LineRole.Plain -> when {
                    t.isEmpty() -> ""
                    stack.lastOrNull()?.members == true -> indent(depth()) + member(t)
                    else -> indent(depth()) + Arrows.space(t)
                }
            }
            if (!inRange) {
                out += Out(raw, line.index)
                continue
            }
            // One blank line at most, none at the very top.
            if (formatted.isEmpty() && role == LineRole.Plain && (out.isEmpty() || out.last().text.isEmpty())) continue
            out += Out(formatted, line.index)
        }
        if (only == null) {
            while (out.size > 1 && out.last().text.isEmpty()) out.removeAt(out.lastIndex)
            if (out.isNotEmpty() && out.last().text.isNotEmpty() && text.isNotEmpty()) out += Out("", lines.last().index + 1)
        }
        return out
    }

    /** For each free-text opener, the indentation of its least indented non-blank body line. */
    private fun bodyIndents(lines: List<BlockLine>): Map<Int, Int> {
        val out = HashMap<Int, Int>()
        var opener = -1
        for (l in lines) {
            when {
                l.role is LineRole.Open && (l.role as LineRole.Open).kind in BlockSyntax.textBodies -> opener = l.index
                l.role == LineRole.Body && opener >= 0 && l.raw.isNotBlank() -> {
                    val n = l.raw.takeWhile { it == ' ' || it == '\t' }.length
                    out[opener] = minOf(out[opener] ?: n, n)
                }
                l.role !is LineRole.Body -> opener = -1
            }
        }
        return out
    }

    /** A visibility modifier, the whitespace after it, and the rest of the member. */
    private val visibilityGap = Regex("""^([-#~+])[ \t]+(\S.*)$""")
    private val identifierStart = Regex("""^[A-Za-z_]""")

    /**
     * A class member line: `+    size: Size` becomes `+size: Size`. PlantUML only reads a glued
     * modifier as visibility when at least two characters follow it (`+x` is drawn as the text
     * "+x", `+ x` as a public `x`), and `+{static}` isn't read as one at all, so anything else
     * keeps a single space.
     */
    private fun member(t: String): String {
        val m = visibilityGap.matchEntire(t) ?: return t
        val (mod, rest) = m.destructured
        val glue = rest.length >= 2 && identifierStart.containsMatchIn(rest)
        return if (glue) mod + rest else "$mod $rest"
    }

    private fun isUml(start: String): Boolean {
        val type = start.lowercase().removePrefix("@start").takeWhile { it.isLetter() }
        return type == "uml"
    }

    /** Move [line] right ([by] > 0) or left, never past its first non-blank character. */
    private fun shift(line: String, by: Int): String = when {
        by > 0 -> " ".repeat(by) + line
        by < 0 -> {
            val lead = line.takeWhile { it == ' ' || it == '\t' }.length
            line.substring(minOf(-by, lead))
        }
        else -> line
    }
}
