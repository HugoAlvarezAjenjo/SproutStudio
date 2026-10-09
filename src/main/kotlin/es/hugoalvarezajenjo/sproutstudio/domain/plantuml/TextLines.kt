package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import java.util.Arrays

/** Line offsets of a text, shared by folding, block scanning, formatting and the editor. */
object TextLines {

    /** The offset each line starts at; line 0 starts at 0. */
    fun starts(text: String): IntArray {
        val list = ArrayList<Int>()
        list += 0
        for (i in text.indices) if (text[i] == '\n') list += i + 1
        return list.toIntArray()
    }

    /** Where 0-based [line] ends, excluding its `\n`. */
    fun end(text: String, starts: IntArray, line: Int) =
        if (line + 1 < starts.size) starts[line + 1] - 1 else text.length

    /** 0-based line of [offset]. */
    fun of(starts: IntArray, offset: Int): Int {
        var i = Arrays.binarySearch(starts, offset)
        if (i < 0) i = -i - 2
        return i.coerceIn(0, starts.size - 1)
    }

    /** Offset where the line holding [at] starts; no line table needed, for one-off edits. */
    fun startAt(text: String, at: Int): Int = text.lastIndexOf('\n', at - 1) + 1

    /** Offset where the line holding [at] ends, excluding its `\n`. */
    fun endAt(text: String, at: Int): Int = text.indexOf('\n', at).let { if (it < 0) text.length else it }
}
