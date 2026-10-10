package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * The occurrences a rename replaces in one text. [path] is the absolute path of an included file,
 * or null for the text being edited; [text] is what [ranges] (sorted, each covering the name
 * alone) were found in, so a caller can tell the text has changed since.
 */
data class RenameEdit(val path: String?, val text: String, val ranges: List<IntRange>)
