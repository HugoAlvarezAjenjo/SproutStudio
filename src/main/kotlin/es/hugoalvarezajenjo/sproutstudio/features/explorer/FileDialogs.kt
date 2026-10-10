package es.hugoalvarezajenjo.sproutstudio.features.explorer

import es.hugoalvarezajenjo.sproutstudio.resources.trash_restore_hint_windows
import es.hugoalvarezajenjo.sproutstudio.core.os.Os
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.branch_create
import es.hugoalvarezajenjo.sproutstudio.resources.dialog_cancel
import es.hugoalvarezajenjo.sproutstudio.resources.file_change
import es.hugoalvarezajenjo.sproutstudio.resources.file_choose_template
import es.hugoalvarezajenjo.sproutstudio.resources.file_clear
import es.hugoalvarezajenjo.sproutstudio.resources.file_in
import es.hugoalvarezajenjo.sproutstudio.resources.file_in_puml
import es.hugoalvarezajenjo.sproutstudio.resources.file_name
import es.hugoalvarezajenjo.sproutstudio.resources.file_new_folder
import es.hugoalvarezajenjo.sproutstudio.resources.file_new_name
import es.hugoalvarezajenjo.sproutstudio.resources.file_no_template
import es.hugoalvarezajenjo.sproutstudio.resources.file_rename
import es.hugoalvarezajenjo.sproutstudio.resources.file_rename_file
import es.hugoalvarezajenjo.sproutstudio.resources.file_rename_folder
import es.hugoalvarezajenjo.sproutstudio.resources.file_template
import es.hugoalvarezajenjo.sproutstudio.resources.menu_new_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.trash_confirm
import es.hugoalvarezajenjo.sproutstudio.resources.trash_empty_folder
import es.hugoalvarezajenjo.sproutstudio.resources.trash_holds
import es.hugoalvarezajenjo.sproutstudio.resources.trash_restore_hint
import es.hugoalvarezajenjo.sproutstudio.resources.trash_title
import es.hugoalvarezajenjo.sproutstudio.resources.trash_unsaved
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.NameDialog
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.tr

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Template
import es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction
import es.hugoalvarezajenjo.sproutstudio.features.explorer.describe
import es.hugoalvarezajenjo.sproutstudio.features.explorer.fileError
import es.hugoalvarezajenjo.sproutstudio.features.templates.TemplateGallery
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.services.files.FileError
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.io.File
import kotlinx.coroutines.launch

/** Shows the dialog for [WorkspaceViewModel.fileAction]: a name prompt, or a Trash confirmation. */
@Composable
internal fun FileActionDialog(p: WorkspaceViewModel) {
    when (val a = p.explorer.fileAction ?: return) {
        is FileAction.NewDiagram -> NewDiagramDialog(p, a.dir)

        is FileAction.NewFolder -> NameDialog(
            title = tr(Res.string.file_new_folder), creating = true, label = tr(Res.string.file_name), confirm = tr(Res.string.branch_create),
            initial = p.deps.files.freeName(a.dir, "new-folder"),
            state = remember(a) { mutableStateOf(selected(p.deps.files.freeName(a.dir, "new-folder"), false)) },
            hint = tr(Res.string.file_in, where(p, a.dir)),
            check = { p.deps.files.validate(a.dir, it)?.describe() },
            onDismiss = { p.explorer.dismissFileAction() },
            scope = p.scope,
            cancel = tr(Res.string.dialog_cancel),
        ) { p.explorer.newFolderIn(a.dir, it).exceptionOrNull()?.fileError?.describe() }

        is FileAction.Rename -> NameDialog(
            title = if (a.target.isDirectory) tr(Res.string.file_rename_folder) else tr(Res.string.file_rename_file), creating = false, label = tr(Res.string.file_new_name), confirm = tr(Res.string.file_rename),
            initial = a.target.name,
            state = remember(a) { mutableStateOf(selected(a.target.name, !a.target.isDirectory)) },
            hint = null,
            check = { p.deps.files.validate(a.target.absoluteFile.parentFile, it, except = a.target)?.describe() },
            onDismiss = { p.explorer.dismissFileAction() },
            scope = p.scope,
            cancel = tr(Res.string.dialog_cancel),
        ) { p.explorer.rename(a.target, it).exceptionOrNull()?.fileError?.describe() }

        is FileAction.Delete -> DeleteDialog(p, a.target)
    }
}

/**
 * New Diagram: a name, and optionally a template from the gallery. Without one the file is empty.
 * The name field's state lives here so it survives the dialog stepping aside for the gallery.
 */
@Composable
private fun NewDiagramDialog(p: WorkspaceViewModel, dir: File) {
    val initial = remember(dir) { p.deps.files.freeName(dir, "new-diagram", ".puml") }
    val name = remember(dir) { mutableStateOf(selected(initial, selectBase = true)) }
    var template by remember(dir) { mutableStateOf<Template?>(null) }
    var picking by remember(dir) { mutableStateOf(false) }
    if (picking) {
        TemplateGallery(
            thumbnails = p.deps.thumbnails,
            onDismiss = { picking = false },
            onPick = { template = it; picking = false },
            wholeOnly = true,
        )
        return
    }
    NameDialog(
        title = tr(Res.string.menu_new_diagram), creating = true, label = tr(Res.string.file_name), confirm = tr(Res.string.branch_create),
        state = name, initial = initial,
        hint = tr(Res.string.file_in_puml, where(p, dir)),
        check = { n -> (if (n.isBlank()) FileError.EmptyName else p.deps.files.validate(dir, p.deps.files.diagramName(n)))?.describe() },
        onDismiss = { p.explorer.dismissFileAction() },
        scope = p.scope,
        cancel = tr(Res.string.dialog_cancel),
        extra = {
            val c = ide
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(tr(Res.string.file_template), color = c.textMuted, fontSize = 12.sp)
                Text(
                    template?.let { "${it.name} (${it.category})" } ?: tr(Res.string.file_no_template),
                    color = if (template != null) c.text else c.textMuted, fontSize = 13.sp,
                    modifier = Modifier.weight(1f).testTag("new-diagram-template"),
                )
                if (template != null) TextButton(onClick = { template = null }) { Text(tr(Res.string.file_clear)) }
                TextButton(onClick = { picking = true }, modifier = Modifier.testTag("choose-template")) {
                    Text(if (template == null) tr(Res.string.file_choose_template) else tr(Res.string.file_change))
                }
            }
        },
    ) { p.explorer.newDiagramIn(dir, it, template).exceptionOrNull()?.fileError?.describe() }
}

private fun selected(text: String, selectBase: Boolean): TextFieldValue {
    val end = if (selectBase && text.contains('.')) text.lastIndexOf('.') else text.length
    return TextFieldValue(text, TextRange(0, end))
}

/** "project/sub" for the hint line under a name field. */
private fun where(p: WorkspaceViewModel, dir: File): String {
    val root = p.root?.absoluteFile ?: return dir.name
    val rel = dir.absoluteFile.relativeTo(root).path
    return if (rel.isEmpty()) root.name else "${root.name}/$rel"
}

@Composable
private fun DeleteDialog(p: WorkspaceViewModel, target: File) {
    val c = ide
    var failed by remember { mutableStateOf<String?>(null) }
    val f = target.absoluteFile
    val inside = remember(f) { if (f.isDirectory) f.walkTopDown().count { it.isFile } else 0 }
    val unsaved = p.docs.filter { d -> d.dirty && d.file?.absoluteFile?.let { it == f || it.startsWith(f) } == true }
    AlertDialog(
        onDismissRequest = { p.explorer.dismissFileAction() },
        containerColor = c.popup,
        shape = RoundedCornerShape(10.dp),
        title = { Text(tr(Res.string.trash_title, f.name), style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    buildString {
                        if (f.isDirectory) append(if (inside == 0) tr(Res.string.trash_empty_folder) else tr(Res.plurals.trash_holds, inside, inside)); append(' ')
                        append(tr(if (Os.isWindows) Res.string.trash_restore_hint_windows else Res.string.trash_restore_hint))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (unsaved.isNotEmpty()) Text(
                    tr(Res.string.trash_unsaved, unsaved.joinToString { it.name }),
                    color = c.error, fontSize = 12.sp,
                )
                failed?.let { Text(it, color = c.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                p.scope.launch {
                    failed = p.explorer.delete(f).exceptionOrNull()?.fileError?.describe()
                    if (failed == null) p.explorer.dismissFileAction()
                }
            }, modifier = Modifier.testTag("file-delete-confirm")) { Text(tr(Res.string.trash_confirm), color = c.error) }
        },
        dismissButton = { TextButton(onClick = { p.explorer.dismissFileAction() }) { Text(tr(Res.string.dialog_cancel)) } },
    )
}

