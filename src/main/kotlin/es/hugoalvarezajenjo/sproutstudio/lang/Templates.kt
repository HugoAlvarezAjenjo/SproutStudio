package es.hugoalvarezajenjo.sproutstudio.lang

/**
 * A ready-made diagram or block the user can insert. [body] is the full text, with an optional
 * `$0` marking where the caret should land. [whole] templates are complete diagrams (shown in the
 * gallery and offered on an empty file); the rest are blocks you drop into an existing diagram.
 */
data class Template(
    val id: String,
    val name: String,
    val category: String,
    val body: String,
    val whole: Boolean = true,
) {
    /** Body with the `$0` caret marker stripped, for rendering a preview. */
    val previewSource: String get() = body.replace("\$0", "")
}

/** The built-in template catalogue, grouped by category for the gallery. */
object Templates {
    val all: List<Template> = listOf(
        // ── whole diagrams ──
        Template("seq", "Sequence", "UML",
            "@startuml\nactor User\nparticipant App\ndatabase DB\n\nUser -> App: $0request\nApp -> DB: query\nDB --> App: rows\nApp --> User: response\n@enduml\n"),
        Template("class", "Class", "UML",
            "@startuml\nclass $0Animal {\n  +name: String\n  +speak()\n}\nclass Dog\nclass Cat\nAnimal <|-- Dog\nAnimal <|-- Cat\n@enduml\n"),
        Template("activity", "Activity", "UML",
            "@startuml\nstart\n:$0Do something;\nif (Is it done?) then (yes)\n  :Celebrate;\nelse (no)\n  :Try again;\nendif\nstop\n@enduml\n"),
        Template("state", "State", "UML",
            "@startuml\n[*] --> $0Idle\nIdle --> Working : start\nWorking --> Idle : done\nWorking --> [*]\n@enduml\n"),
        Template("usecase", "Use case", "UML",
            "@startuml\nleft to right direction\nactor $0User\nrectangle System {\n  User --> (Log in)\n  User --> (Sign up)\n}\n@enduml\n"),
        Template("component", "Component", "UML",
            "@startuml\ncomponent [$0Frontend]\ncomponent [Backend]\ndatabase DB\n[Frontend] --> [Backend]\n[Backend] --> DB\n@enduml\n"),
        Template("object", "Object", "UML",
            "@startuml\nobject $0user\nuser : name = \"Ada\"\nuser : id = 42\n@enduml\n"),
        Template("deployment", "Deployment", "UML",
            "@startuml\nnode $0Server {\n  artifact app.jar\n}\ncloud Internet\nInternet --> Server\n@enduml\n"),
        Template("er", "Entity-relationship", "UML",
            "@startuml\nentity $0User {\n  * id : int\n  --\n  name : text\n}\nentity Order {\n  * id : int\n  --\n  user_id : int\n}\nUser ||--o{ Order\n@enduml\n"),
        // ── C4 (local macros, no network) ──
        Template("c4-context", "C4 Context", "C4",
            "@startuml\n' Local C4 macros (no !include needed for these shapes)\n!define Person(e_alias, e_label) rectangle \"e_label\" <<Person>> as e_alias\n!define System(e_alias, e_label) rectangle \"e_label\" <<System>> as e_alias\n\nPerson(user, \"$0Customer\")\nSystem(app, \"Web App\")\nSystem(mail, \"Email System\")\nuser --> app : uses\napp --> mail : sends email\n@enduml\n"),
        // ── non-UML ──
        Template("mindmap", "Mind map", "Other",
            "@startmindmap\n* $0Idea\n** Branch one\n*** Detail\n** Branch two\n@endmindmap\n"),
        Template("wbs", "Work breakdown", "Other",
            "@startwbs\n* $0Project\n** Phase 1\n*** Task A\n*** Task B\n** Phase 2\n@endwbs\n"),
        Template("gantt", "Gantt", "Other",
            "@startgantt\nProject starts 2024-01-01\n[$0Design] lasts 5 days\n[Build] lasts 10 days\n[Build] starts at [Design]'s end\n@endgantt\n"),
        Template("json", "JSON", "Other",
            "@startjson\n{\n  \"$0name\": \"Ada\",\n  \"roles\": [\"admin\", \"user\"]\n}\n@endjson\n"),
        Template("salt", "Wireframe (salt)", "Other",
            "@startsalt\n{+\n  Login | \"$0        \"\n  Password | \"****    \"\n  [Cancel] | [  OK  ]\n}\n@endsalt\n"),
        // ── blocks to drop into an existing diagram ──
        Template("block-note", "Note", "Blocks",
            "note $0right\n  Your note here\nend note\n", whole = false),
        Template("block-alt", "Alt / else", "Blocks",
            "alt $0success\n  A -> B: ok\nelse failure\n  A -> B: error\nend\n", whole = false),
        Template("block-loop", "Loop", "Blocks",
            "loop $0every item\n  A -> B: step\nend\n", whole = false),
        Template("block-group", "Group / box", "Blocks",
            "box \"$0Service\"\n  participant A\n  participant B\nend box\n", whole = false),
        Template("block-ref", "Ref", "Blocks",
            "ref over A, B : $0see other diagram\n", whole = false),
    )

    val wholeDiagrams: List<Template> get() = all.filter { it.whole }

    /** Category order for the gallery; anything else goes last. */
    private val order = listOf("UML", "C4", "Other", "Blocks")
    val byCategory: List<Pair<String, List<Template>>>
        get() = all.groupBy { it.category }.toList().sortedBy { order.indexOf(it.first).let { i -> if (i < 0) order.size else i } }
}
