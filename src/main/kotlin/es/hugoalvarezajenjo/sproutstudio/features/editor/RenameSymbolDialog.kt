package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.RenameProblem
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SymbolRenaming
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.dialog_cancel
import es.hugoalvarezajenjo.sproutstudio.resources.rename_confirm
import es.hugoalvarezajenjo.sproutstudio.resources.rename_err_blank
import es.hugoalvarezajenjo.sproutstudio.resources.rename_err_invalid
import es.hugoalvarezajenjo.sproutstudio.resources.rename_err_keyword
import es.hugoalvarezajenjo.sproutstudio.resources.rename_err_missing_dollar
import es.hugoalvarezajenjo.sproutstudio.resources.rename_err_stale
import es.hugoalvarezajenjo.sproutstudio.resources.rename_err_taken
import es.hugoalvarezajenjo.sproutstudio.resources.rename_err_unchanged
import es.hugoalvarezajenjo.sproutstudio.resources.rename_err_unexpected_dollar
import es.hugoalvarezajenjo.sproutstudio.resources.rename_in_files
import es.hugoalvarezajenjo.sproutstudio.resources.rename_label
import es.hugoalvarezajenjo.sproutstudio.resources.rename_title
import es.hugoalvarezajenjo.sproutstudio.resources.rename_usages
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.NameDialog
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr
import kotlinx.coroutines.CoroutineScope

/**
 * ⇧F6: the new name for [renaming]'s symbol, checked as you type, and how much will change.
 * [onRename] applies it and returns why it couldn't, or null when done.
 */
@Composable
fun RenameSymbolDialog(
    renaming: SymbolRenaming,
    scope: CoroutineScope,
    onRename: (String) -> RenameProblem?,
    onDismiss: () -> Unit,
) {
    val name = renaming.name
    // The name selected without a leading `$`, which has to stay.
    val state = remember(renaming) { mutableStateOf(TextFieldValue(name, TextRange(if (name.startsWith('$')) 1 else 0, name.length))) }
    val usages = tr(Res.plurals.rename_usages, renaming.usages, renaming.usages)
    val hint = if (renaming.files > 1) "$usages ${tr(Res.plurals.rename_in_files, renaming.files, renaming.files)}" else usages
    NameDialog(
        title = tr(Res.string.rename_title, name),
        creating = false,
        label = tr(Res.string.rename_label),
        confirm = tr(Res.string.rename_confirm),
        cancel = tr(Res.string.dialog_cancel),
        initial = name,
        state = state,
        hint = hint,
        check = { n -> renaming.problem(n)?.describe(n) },
        onDismiss = onDismiss,
        scope = scope,
    ) { n -> onRename(n)?.describe(n) }
}

/** What's wrong with [newName], for the dialog's error line. */
internal fun RenameProblem.describe(newName: String): String = when (this) {
    RenameProblem.BLANK -> text(Res.string.rename_err_blank)
    RenameProblem.UNCHANGED -> text(Res.string.rename_err_unchanged)
    RenameProblem.INVALID -> text(Res.string.rename_err_invalid)
    RenameProblem.MISSING_DOLLAR -> text(Res.string.rename_err_missing_dollar)
    RenameProblem.UNEXPECTED_DOLLAR -> text(Res.string.rename_err_unexpected_dollar)
    RenameProblem.KEYWORD -> text(Res.string.rename_err_keyword, newName)
    RenameProblem.TAKEN -> text(Res.string.rename_err_taken, newName)
    RenameProblem.STALE -> text(Res.string.rename_err_stale)
}
