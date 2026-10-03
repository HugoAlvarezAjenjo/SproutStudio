package es.hugoalvarezajenjo.sproutstudio.lang

/** Diagram kinds we know how to be clever about. */
enum class DiagramKind(val label: String) {
    SEQUENCE("Sequence"),
    CLASS("Class"),
    ACTIVITY("Activity"),
    USECASE("Use case"),
    STATE("State"),
    COMPONENT("Component"),
    MINDMAP("Mind map"),
    GANTT("Gantt"),
    UNKNOWN("Diagram"),
}

/** A symbol the user declared in the file (participant, class, ...). */
data class Symbol(val name: String, val kind: String, val line: Int)

object PlantUmlLanguage {

    val diagramStarts = listOf(
        "@startuml", "@enduml", "@startmindmap", "@endmindmap", "@startgantt", "@endgantt",
        "@startwbs", "@endwbs", "@startjson", "@endjson", "@startyaml", "@endyaml", "@startsalt", "@endsalt",
    )

    val preprocessor = listOf(
        "!include", "!includesub", "!define", "!definelong", "!enddefinelong", "!pragma", "!theme",
        "!if", "!ifdef", "!ifndef", "!else", "!elseif", "!endif", "!function", "!endfunction",
        "!procedure", "!endprocedure", "!return", "!log", "!assert", "!foreach", "!endfor", "!while", "!endwhile",
    )

    val common = listOf(
        "title", "header", "footer", "caption", "legend", "endlegend", "note", "end note",
        "skinparam", "hide", "show", "left to right direction", "top to bottom direction",
        "scale", "newpage", "as", "of", "over", "left", "right", "top", "bottom",
    )

    private val byKind: Map<DiagramKind, List<String>> = mapOf(
        DiagramKind.SEQUENCE to listOf(
            "participant", "actor", "boundary", "control", "entity", "database", "collections", "queue",
            "activate", "deactivate", "destroy", "create", "return", "autonumber",
            "alt", "else", "opt", "loop", "par", "break", "critical", "group", "end",
            "ref over", "box", "end box", "== Section ==", "...", "|||", "hnote", "rnote",
        ),
        DiagramKind.CLASS to listOf(
            "class", "abstract class", "interface", "enum", "annotation", "entity", "struct",
            "package", "namespace", "extends", "implements", "together",
        ),
        DiagramKind.ACTIVITY to listOf(
            "start", "stop", "end", "if", "then", "else", "elseif", "endif", "while", "endwhile",
            "repeat", "repeat while", "fork", "fork again", "end fork", "split", "split again", "end split",
            "partition", "detach", "kill", "switch", "case", "endswitch", "backward",
        ),
        DiagramKind.USECASE to listOf("actor", "usecase", "rectangle", "package", "left to right direction"),
        DiagramKind.STATE to listOf("state", "[*]", "--", "||", "fork", "join", "choice", "end"),
        DiagramKind.COMPONENT to listOf(
            "component", "interface", "node", "cloud", "database", "folder", "frame", "package",
            "rectangle", "artifact", "storage", "queue", "port", "portin", "portout",
        ),
        DiagramKind.MINDMAP to listOf("*", "**", "***", "+", "-", "left side", "right side"),
        DiagramKind.GANTT to listOf(
            "Project starts", "requires", "starts", "ends", "lasts", "days", "happens", "is colored in",
            "and", "then", "-- Section --", "saturday are closed", "sunday are closed",
        ),
    )

    val themes = listOf(
        "amiga", "aws-orange", "black-knight", "bluegray", "blueprint", "carbon-gray", "cerulean",
        "cerulean-outline", "crt-amber", "crt-green", "cyborg", "cyborg-outline", "hacker", "lightgray",
        "mars", "materia", "materia-outline", "metal", "mimeograph", "minty", "mono", "plain",
        "reddress-darkblue", "reddress-darkgreen", "reddress-darkorange", "reddress-darkred",
        "reddress-lightblue", "reddress-lightgreen", "reddress-lightorange", "reddress-lightred",
        "sandstone", "silver", "sketchy", "sketchy-outline", "spacelab", "superhero", "superhero-outline",
        "toy", "united", "vibrant", "cloudscape-design",
    )

    /** PlantUML's standard named colours, offered after a `#` or a colour-valued skinparam. */
    val colors = listOf(
        "white", "black", "red", "green", "blue", "yellow", "orange", "purple", "pink", "brown",
        "gray", "lightgray", "darkgray", "lightblue", "lightgreen", "lightyellow", "gold", "cyan",
        "magenta", "navy", "teal", "olive", "maroon", "salmon", "tomato", "crimson", "coral",
        "khaki", "lavender", "ivory", "beige", "turquoise", "violet", "indigo", "transparent",
        "business", "technology", "motivation", "strategy", "implementation",
    )

    /** Arrow styles for sequence/other diagrams, offered after a declared symbol on a line. */
    val arrows = listOf("->", "-->", "->>", "-\\", "--\\", "<-", "<--", "<->", "o->", "x->", "..>", "--", "..")

    val skinparams = listOf(
        "monochrome", "shadowing", "handwritten", "backgroundColor", "defaultFontName", "defaultFontSize",
        "roundcorner", "linetype ortho", "linetype polyline", "nodesep", "ranksep", "dpi",
        "ArrowColor", "BorderColor", "sequenceMessageAlign", "responseMessageBelowArrow",
        "ParticipantBackgroundColor", "ParticipantBorderColor", "ActorBorderColor",
        "ClassBackgroundColor", "ClassBorderColor", "ClassAttributeIconSize",
        "ActivityBackgroundColor", "ActivityBorderColor", "NoteBackgroundColor", "NoteBorderColor",
        "PackageStyle", "componentStyle uml2", "maxMessageSize", "wrapWidth",
    )

    fun keywordsFor(kind: DiagramKind): List<String> =
        (byKind[kind].orEmpty() + common).distinct()

    /** All keywords, for highlighting (single-word ones only). */
    val allKeywords: Set<String> by lazy {
        (byKind.values.flatten() + common)
            .filter { it.all { c -> c.isLetter() } }
            .toSet()
    }

    private val declaration = Regex(
        """^\s*(participant|actor|boundary|control|entity|database|collections|queue|""" +
            """abstract\s+class|class|interface|enum|annotation|struct|component|node|cloud|folder|frame|""" +
            """artifact|storage|usecase|state|object|rectangle|package|namespace)\s+""" +
            """(?:"([^"]+)"|([\w.:$]+))(?:\s+as\s+([\w.:$]+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val arrowEndpoints = Regex("""^\s*([A-Za-z_][\w.]*)\s*[-.=<>ox*|\\/+#\[\]]*?(?:->|-->|<-|<--|->>|\.\.>|--|\.\.)[^\w]*\s*([A-Za-z_][\w.]*)""")

    /** Symbols declared or implicitly used (arrow endpoints) in [text]. */
    fun symbols(text: String): List<Symbol> {
        val seen = LinkedHashMap<String, Symbol>()
        text.lineSequence().forEachIndexed { i, line ->
            if (line.trimStart().startsWith("'")) return@forEachIndexed
            declaration.find(line)?.let { m ->
                val kind = m.groupValues[1].lowercase().replace(Regex("\\s+"), " ")
                val alias = m.groupValues[4]
                val name = alias.ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[2] } }
                if (name.isNotEmpty()) seen.putIfAbsent(name, Symbol(name, kind, i + 1))
                return@forEachIndexed
            }
            arrowEndpoints.find(line)?.let { m ->
                listOf(m.groupValues[1], m.groupValues[2]).forEach { n ->
                    if (n.isNotEmpty() && n !in allKeywords) seen.putIfAbsent(n, Symbol(n, "used", i + 1))
                }
            }
        }
        return seen.values.toList()
    }

    /** Guess the diagram kind of the block containing [offset]. */
    fun kindAt(text: String, offset: Int): DiagramKind {
        val safe = offset.coerceIn(0, text.length)
        val start = text.lastIndexOf("@start", safe.coerceAtMost(text.length - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it }
        val endIdx = text.indexOf("@end", safe).let { if (it < 0) text.length else it }
        val block = text.substring(start, maxOf(start, endIdx))
        val head = block.lineSequence().firstOrNull()?.trim()?.lowercase().orEmpty()
        return when {
            head.startsWith("@startmindmap") || head.startsWith("@startwbs") -> DiagramKind.MINDMAP
            head.startsWith("@startgantt") -> DiagramKind.GANTT
            else -> guessUml(block)
        }
    }

    private fun guessUml(block: String): DiagramKind {
        val lines = block.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("'") }.toList()
        fun any(re: Regex) = lines.any { re.containsMatchIn(it) }
        return when {
            any(Regex("""^(start|stop)$|^:.*;$|^if\s*\(""")) -> DiagramKind.ACTIVITY
            any(Regex("""^(abstract\s+)?(class|interface|enum)\s""")) -> DiagramKind.CLASS
            any(Regex("""^state\s|\[\*]""")) -> DiagramKind.STATE
            any(Regex("""^(usecase\s|\(.+\))""")) -> DiagramKind.USECASE
            any(Regex("""^(component|node|cloud|artifact)\s|^\[.+]""")) -> DiagramKind.COMPONENT
            any(Regex("""^(participant|actor|boundary|control|entity|database|queue)\s|->|-->|<-""")) -> DiagramKind.SEQUENCE
            else -> DiagramKind.UNKNOWN
        }
    }
}
