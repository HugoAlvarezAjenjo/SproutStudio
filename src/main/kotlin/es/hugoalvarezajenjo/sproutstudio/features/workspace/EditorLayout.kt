package es.hugoalvarezajenjo.sproutstudio.features.workspace

/** How the active tab is shown. */
enum class EditorLayout(val label: String) {
    EDITOR("Editor Only"),
    SPLIT("Editor and Preview"),
    PREVIEW("Preview Only"),
}
