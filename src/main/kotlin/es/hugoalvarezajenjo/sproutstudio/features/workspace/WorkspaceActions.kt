package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.ui.input.key.Key
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.EditOps
import es.hugoalvarezajenjo.sproutstudio.features.editor.changeAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.editor.findNext
import es.hugoalvarezajenjo.sproutstudio.features.editor.foldAll
import es.hugoalvarezajenjo.sproutstudio.features.editor.foldAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.editor.rollbackAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.editor.selectedText
import es.hugoalvarezajenjo.sproutstudio.features.editor.unfoldAll
import es.hugoalvarezajenjo.sproutstudio.features.editor.unfoldAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.git.canCompareWithHead
import es.hugoalvarezajenjo.sproutstudio.features.git.hasCommittedChanges
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_toggle_comment
import es.hugoalvarezajenjo.sproutstudio.resources.menu_compare_with_head
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_in_files
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_next
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_previous
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_usages
import es.hugoalvarezajenjo.sproutstudio.resources.menu_fold_all
import es.hugoalvarezajenjo.sproutstudio.resources.menu_fold_block
import es.hugoalvarezajenjo.sproutstudio.resources.menu_go_to_declaration
import es.hugoalvarezajenjo.sproutstudio.resources.menu_navigate_back
import es.hugoalvarezajenjo.sproutstudio.resources.menu_navigate_forward
import es.hugoalvarezajenjo.sproutstudio.resources.menu_reformat_code
import es.hugoalvarezajenjo.sproutstudio.resources.menu_rename_symbol
import es.hugoalvarezajenjo.sproutstudio.resources.menu_replace
import es.hugoalvarezajenjo.sproutstudio.resources.menu_rollback_lines
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_diff_with_head
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_history
import es.hugoalvarezajenjo.sproutstudio.resources.menu_unfold_all
import es.hugoalvarezajenjo.sproutstudio.resources.menu_unfold_block
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Action
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Shortcut

/**
 * The project window's actions on the active document, each defined once for the menu bar, the
 * command palette and the editor's right-click menu. A property builds its [Action] when read,
 * so `enabled` is always the current state (and the menu bar, which reads them while composing,
 * updates when it changes).
 */
class WorkspaceActions(private val p: WorkspaceViewModel) {
    private val d get() = p.active
    private val has get() = d != null

    // ── Edit ──
    val find get() = Action("edit.find", Res.string.menu_find, Shortcut(Key.F, "F"), Icons.Outlined.Search, has, restoresFocus = false) {
        d?.let { p.revealEditor(); it.find.open(replace = false, seed = it.selectedText()) }
    }
    val replace get() = Action("edit.replace", Res.string.menu_replace, Shortcut(Key.R, "R"), Icons.Outlined.FindReplace, has, restoresFocus = false) {
        d?.let { p.revealEditor(); it.find.open(replace = true, seed = it.selectedText()) }
    }
    val findNext get() = Action("edit.findNext", Res.string.menu_find_next, Shortcut(Key.G, "G"), enabled = has) { p.revealEditor(); d?.findNext(true) }
    val findPrevious get() = Action("edit.findPrev", Res.string.menu_find_previous, Shortcut(Key.G, "G", shift = true), enabled = has) { p.revealEditor(); d?.findNext(false) }
    val findInFiles get() = Action(
        "edit.findInFiles", Res.string.menu_find_in_files, Shortcut(Key.F, "F", shift = true), Icons.Outlined.TravelExplore, p.root != null, restoresFocus = false,
    ) { p.findInFiles() }
    val toggleComment get() = Action("edit.comment", Res.string.cmd_toggle_comment, Shortcut(Key.Slash, "/"), enabled = has) {
        d?.let { p.revealEditor(); it.value = EditOps.toggleComment(it.value) }
    }

    // ── Code ──
    val foldBlock get() = Action("code.fold", Res.string.menu_fold_block, Shortcut(Key.Minus, "-"), Icons.Outlined.UnfoldLess, has) { p.revealEditor(); d?.foldAtCaret() }
    val unfoldBlock get() = Action("code.unfold", Res.string.menu_unfold_block, Shortcut(Key.Equals, "="), Icons.Outlined.UnfoldMore, has) { p.revealEditor(); d?.unfoldAtCaret() }
    val foldAll get() = Action("code.foldAll", Res.string.menu_fold_all, Shortcut(Key.Minus, "-", shift = true), enabled = has) { p.revealEditor(); d?.foldAll() }
    val unfoldAll get() = Action("code.unfoldAll", Res.string.menu_unfold_all, Shortcut(Key.Equals, "=", shift = true), enabled = has) { p.revealEditor(); d?.unfoldAll() }
    val reformat get() = Action("code.reformat", Res.string.menu_reformat_code, Shortcut(Key.L, "L", alt = true), Icons.AutoMirrored.Outlined.FormatAlignLeft, has) { p.reformatCode() }
    val goToDeclaration get() = Action("code.declaration", Res.string.menu_go_to_declaration, Shortcut(Key.B, "B"), enabled = has) { p.goToDeclaration() }
    val findUsages get() = Action("code.usages", Res.string.menu_find_usages, Shortcut(Key.F7, "F7", command = false, alt = true), Icons.Outlined.Search, has) { p.findUsages() }
    val renameSymbol get() = Action(
        "code.rename", Res.string.menu_rename_symbol, Shortcut(Key.F6, "F6", command = false, shift = true), Icons.Outlined.DriveFileRenameOutline, has,
    ) { p.renameSymbol() }
    val navigateBack get() = Action("code.back", Res.string.menu_navigate_back, Shortcut(Key.DirectionLeft, "←", alt = true), enabled = d?.nav?.canGoBack == true) { p.navigateBack() }
    val navigateForward get() = Action("code.forward", Res.string.menu_navigate_forward, Shortcut(Key.DirectionRight, "→", alt = true), enabled = d?.nav?.canGoForward == true) { p.navigateForward() }

    // ── Git, on the active file ──
    val compareWithHead get() = Action("git.compare", Res.string.menu_compare_with_head, icon = Icons.Outlined.Difference, enabled = canCompareWithHead(p, d?.file)) {
        d?.file?.let { p.deps.windows.compareWithHead(p, it) }
    }
    val rollbackLines get() = Action(
        "git.rollbackLines", Res.string.menu_rollback_lines, Shortcut(Key.Z, "Z", alt = true), Icons.AutoMirrored.Outlined.Undo, d?.changeAtCaret() != null,
    ) { p.revealEditor(); d?.rollbackAtCaret() }
    val showDiff get() = Action("git.diff", Res.string.menu_show_diff_with_head, enabled = hasCommittedChanges(p, d?.file)) {
        d?.file?.let { p.deps.windows.compareWithHead(p, it, textFirst = true) }
    }
    val showHistory get() = Action("git.history", Res.string.menu_show_history, icon = Icons.Outlined.History, enabled = p.git.status.head != null && d?.file != null) {
        d?.file?.let { p.deps.windows.showHistory(p, it) }
    }

    /** Every action above (a new one must be added here too: WorkspaceActionsTest checks the palette lists them). */
    val all: List<Action> get() = listOf(
        find, replace, findNext, findPrevious, findInFiles, toggleComment,
        foldBlock, unfoldBlock, foldAll, unfoldAll, reformat, goToDeclaration, findUsages, renameSymbol, navigateBack, navigateForward,
        compareWithHead, rollbackLines, showDiff, showHistory,
    )
}
