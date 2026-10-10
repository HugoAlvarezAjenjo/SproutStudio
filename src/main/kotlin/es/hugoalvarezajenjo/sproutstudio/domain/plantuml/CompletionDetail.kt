package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * What a [Completion] is, shown muted beside it. The domain only says which; the view picks the
 * words (strings.xml), so the popup follows the app's language.
 */
sealed interface CompletionDetail {

    /** A fixed kind of suggestion. */
    enum class Tag : CompletionDetail {
        PREPROCESSOR, THEME, COLOR, SKINPARAM, LIBRARY, FOLDER, FILE, ARROW, CLOSE_DIAGRAM,
        MACRO, VARIABLE, USED,
    }

    /** A keyword of a [diagram] kind. */
    data class Keyword(val diagram: DiagramKind) : CompletionDetail

    /** A whole-diagram template, by its catalogue [name]. */
    data class Snippet(val name: String) : CompletionDetail

    /** Shown as is: a PlantUML word (`participant`, `class`) or the stdlib catalogue's text. */
    data class Verbatim(val text: String) : CompletionDetail
}
