package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.IncludeReader
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Navigation
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.NavigationTarget
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import java.io.File

// ── Document commands (Code → Navigate menu, ⌘-click) ──────────────────────────────────────

private val Document.caret: Int get() = value.selection.start

/**
 * ⌘B / ⌘-click at [offset]: jump to the declaration, or list the usages when already on it.
 * Returns the file to open for the caller: the one an `!include` points at, or the included
 * file that declares the procedure / function / macro / `$variable` at [offset] (with the
 * declaration to select). Null otherwise, also when the file doesn't exist or this document
 * has no folder to resolve it against. [includes] reads the included files.
 */
fun Document.goToDeclaration(offset: Int = caret, includes: IncludeReader = IncludeFiles.DISK): FileJump? {
    nav.closeUsages()
    return when (val t = Navigation.definitionAt(text, offset, file?.absolutePath, includes)) {
        is NavigationTarget.InText -> { navigateTo(t.range); null }
        is NavigationTarget.Usages -> { placeCaret(offset); nav.showUsages(UsagesList(t.symbol, t.occurrences)); null }
        is NavigationTarget.IncludedFile -> IncludeFiles.resolve(file?.absolutePath, t.path)?.let { FileJump(it) }
        is NavigationTarget.InFile -> FileJump(File(t.path), t.range)
        null -> null
    }
}

/** ⌥F7: list where the symbol at the caret appears. False when the caret isn't on a symbol used elsewhere. */
fun Document.findUsages(offset: Int = caret): Boolean {
    val u = Navigation.usagesAt(text, offset) ?: return false
    placeCaret(offset)
    nav.showUsages(UsagesList(u.symbol, u.occurrences))
    return true
}

/** Enter in the usages list: go to the selected row. */
fun Document.openSelectedUsage() {
    val u = nav.usages ?: return
    nav.closeUsages()
    navigateTo(u.items[u.selected].range)
}

/** Select [range] and scroll to it, remembering where the caret was for Back. */
fun Document.navigateTo(range: IntRange) {
    nav.recordJump(caret)
    reveal(range)
}

/** Select [range] and scroll to it (a tab just opened by Go to Declaration: there's no Back here). */
fun Document.reveal(range: IntRange) {
    val sel = TextRange(range.first.coerceIn(0, text.length), (range.last + 1).coerceIn(0, text.length))
    value = value.copy(selection = sel) // unfolds a block that hides it
    jumpTo(sel)
}

fun Document.navigateBack(): Boolean = nav.popBack(caret)?.let { moveCaret(it); true } ?: false

fun Document.navigateForward(): Boolean = nav.popForward(caret)?.let { moveCaret(it); true } ?: false

private fun Document.moveCaret(to: Int) {
    val sel = TextRange(to.coerceIn(0, text.length))
    value = value.copy(selection = sel)
    jumpTo(sel)
}

private fun Document.placeCaret(offset: Int) {
    if (offset != caret) value = value.copy(selection = TextRange(offset.coerceIn(0, text.length)))
}
