package es.hugoalvarezajenjo.sproutstudio.features.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Template
import es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction
import es.hugoalvarezajenjo.sproutstudio.features.explorer.delete
import es.hugoalvarezajenjo.sproutstudio.features.explorer.describe
import es.hugoalvarezajenjo.sproutstudio.features.explorer.fileError
import es.hugoalvarezajenjo.sproutstudio.features.explorer.newDiagramIn
import es.hugoalvarezajenjo.sproutstudio.features.explorer.newFolderIn
import es.hugoalvarezajenjo.sproutstudio.features.explorer.rename
import es.hugoalvarezajenjo.sproutstudio.features.templates.TemplateGallery
import es.hugoalvarezajenjo.sproutstudio.features.templates.TemplateThumbnails
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.services.files.FileError
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Shows the dialog for [WorkspaceViewModel.fileAction]: a name prompt, or a Trash confirmation. */
@Composable
internal fun FileActionDialog(p: WorkspaceViewModel) {
    when (val a = p.fileAction ?: return) {
        is FileAction.NewDiagram -> NewDiagramDialog(p, a.dir)

        is FileAction.NewFolder -> NameDialog(
            title = "New Folder", label = "Name", confirm = "Create",
            initial = p.deps.files.freeName(a.dir, "new-folder"), selectBase = false,
            state = remember(a) { mutableStateOf(selected(p.deps.files.freeName(a.dir, "new-folder"), false)) },
            hint = "in ${where(p, a.dir)}",
            check = { p.deps.files.validate(a.dir, it)?.describe() },
            onDismiss = { p.fileAction = null },
            scope = p.scope,
        ) { p.newFolderIn(a.dir, it).exceptionOrNull()?.fileError?.describe() }

        is FileAction.Rename -> NameDialog(
            title = if (a.target.isDirectory) "Rename Folder" else "Rename File", label = "New name", confirm = "Rename",
            initial = a.target.name, selectBase = !a.target.isDirectory,
            state = remember(a) { mutableStateOf(selected(a.target.name, !a.target.isDirectory)) },
            hint = null,
            check = { p.deps.files.validate(a.target.absoluteFile.parentFile, it, except = a.target)?.describe() },
            onDismiss = { p.fileAction = null },
            scope = p.scope,
        ) { p.rename(a.target, it).exceptionOrNull()?.fileError?.describe() }

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
            thumbnails = TemplateThumbnails(p.deps.render, p.deps.diagram),
            onDismiss = { picking = false },
            onPick = { template = it; picking = false },
            wholeOnly = true,
        )
        return
    }
    NameDialog(
        title = "New Diagram", label = "Name", confirm = "Create",
        state = name, initial = initial,
        hint = "in ${where(p, dir)} · .puml is added if missing",
        check = { n -> (if (n.isBlank()) FileError.EmptyName else p.deps.files.validate(dir, p.deps.files.diagramName(n)))?.describe() },
        onDismiss = { p.fileAction = null },
        scope = p.scope,
        extra = {
            val c = ide
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Template", color = c.textMuted, fontSize = 12.sp)
                Text(
                    template?.let { "${it.name} (${it.category})" } ?: "None, empty file",
                    color = if (template != null) c.text else c.textMuted, fontSize = 13.sp,
                    modifier = Modifier.weight(1f).testTag("new-diagram-template"),
                )
                if (template != null) TextButton(onClick = { template = null }) { Text("Clear") }
                TextButton(onClick = { picking = true }, modifier = Modifier.testTag("choose-template")) {
                    Text(if (template == null) "Choose template…" else "Change…")
                }
            }
        },
    ) { p.newDiagramIn(dir, it, template).exceptionOrNull()?.fileError?.describe() }
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
private fun NameDialog(
    title: String,
    label: String,
    confirm: String,
    initial: String,
    /** The field's text, owned by the caller (it may outlive this dialog). */
    state: MutableState<TextFieldValue>,
    hint: String?,
    check: (String) -> String?,
    onDismiss: () -> Unit,
    /** Where the operation runs: the window's, so it isn't cut short by recomposition. */
    scope: CoroutineScope,
    /** Select the name without its extension, so typing replaces just the name (see [selected]). */
    @Suppress("UNUSED_PARAMETER") selectBase: Boolean = false,
    /** Extra controls under the field (the template picker). */
    extra: @Composable () -> Unit = {},
    /** Runs the operation; returns an error to show, or null when done. */
    onConfirm: suspend (String) -> String?,
) {
    val c = ide
    var value by state
    var failed by remember { mutableStateOf<String?>(null) }
    val invalid = check(value.text)
    val error = failed ?: invalid.takeIf { value.text != initial || title.startsWith("New") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    var busy by remember { mutableStateOf(false) }
    fun submit() {
        if (invalid != null || busy) return
        busy = true
        scope.launch {
            failed = onConfirm(value.text)
            busy = false
            if (failed == null) onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.popup,
        shape = RoundedCornerShape(10.dp),
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.width(360.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; failed = null },
                    label = { Text(label) },
                    singleLine = true,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("file-name-field")
                        .onPreviewKeyEvent { e ->
                            if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) { submit(); true }
                            else if (e.type == KeyEventType.KeyDown && e.key == Key.Escape) { onDismiss(); true }
                            else false
                        },
                )
                Text(
                    error ?: hint.orEmpty(),
                    color = if (error != null) c.error else c.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("file-name-hint"),
                )
                extra()
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = invalid == null, modifier = Modifier.testTag("file-name-confirm")) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(p: WorkspaceViewModel, target: File) {
    val c = ide
    var failed by remember { mutableStateOf<String?>(null) }
    val f = target.absoluteFile
    val inside = remember(f) { if (f.isDirectory) f.walkTopDown().count { it.isFile } else 0 }
    val unsaved = p.docs.filter { d -> d.dirty && d.file?.absoluteFile?.let { it == f || it.startsWith(f) } == true }
    AlertDialog(
        onDismissRequest = { p.fileAction = null },
        containerColor = c.popup,
        shape = RoundedCornerShape(10.dp),
        title = { Text("Move “${f.name}” to the Trash?", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                Text(
                    buildString {
                        if (f.isDirectory) append(if (inside == 0) "The folder is empty. " else "It holds $inside file${if (inside == 1) "" else "s"}. ")
                        append("You can get it back from the Trash in Finder.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (unsaved.isNotEmpty()) Text(
                    "Unsaved changes in ${unsaved.joinToString { it.name }} will be lost.",
                    color = c.error, fontSize = 12.sp,
                )
                failed?.let { Text(it, color = c.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                failed = p.delete(f).exceptionOrNull()?.fileError?.describe()
                if (failed == null) p.fileAction = null
            }, modifier = Modifier.testTag("file-delete-confirm")) { Text("Move to Trash", color = c.error) }
        },
        dismissButton = { TextButton(onClick = { p.fileAction = null }) { Text("Cancel") } },
    )
}

