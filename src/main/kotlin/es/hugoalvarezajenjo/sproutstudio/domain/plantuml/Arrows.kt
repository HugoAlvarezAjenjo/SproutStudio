package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * Spacing around relation arrows, for the formatter: `A->B: hi` becomes `A -> B: hi`.
 *
 * Only lines shaped `<name> <arrow> <name>...` are touched, where a name is a word, a quoted
 * string, `[Component]`, `(Use case)` or `:Actor:`. The rest of the line (the label) is left
 * alone.
 *
 * Names are read the way PlantUML reads them, greedily: `Ao--B` is `Ao -- B` and `A--oB` is
 * `A -- oB` (checked against the PlantUML jar), and a dotted name can't end in `.`, so `A..>B`
 * is `A ..> B`. Arrows starting with a letter (`x->`, `o--`) are left alone, because
 * `title x->b` is text, not a relation.
 */
object Arrows {

    private const val NAME = """"[^"]*"|\([^)\n]*\)|\[[^\]\n]*]|:[^:\n]+:|[A-Za-z_$](?:[\w$]|\.(?=[\w$]))*"""
    private const val HEAD = """(?:<\|?|<<|[*+#]|[|}][o|])?"""
    private const val BODY = """[-.]+(?:\[[^\]\n]*][-.]*)?(?:(?:left|right|up|down|le|ri|do|l|r|u|d)[-.]+)?"""
    // A letter tail glued to a name belongs to the name (`A--oB` is `A -- oB`); `->x` and `->o`
    // are lost/circle message ends.
    private const val TAIL = """(?:>>?[ox](?![\w$])|\|?>>?|[*+#]|[ox](?![\w$])|[o|][|{])?"""

    private val relation = Regex(
        """^((?:$NAME)(?:\s+"[^"]*")?)(\s*)($HEAD$BODY$TAIL)(\s*)((?:"[^"]*"\s*)?(?:$NAME).*)$""",
    )

    fun space(line: String): String {
        val m = relation.matchEntire(line) ?: return line
        val (left, gapL, arrow, gapR, right) = m.destructured
        if (gapL == " " && gapR == " ") return line
        // A lone `-` isn't an arrow (`my-service`).
        if (arrow.length < 2) return line
        // `A ->(10) B` is a teoz delay, glued on purpose.
        if (gapR.isEmpty() && right.first() == '(') return line
        return "$left $arrow $right"
    }
}
