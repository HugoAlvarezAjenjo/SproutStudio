package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import es.hugoalvarezajenjo.sproutstudio.model.FileAction
import es.hugoalvarezajenjo.sproutstudio.model.FileOps
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import es.hugoalvarezajenjo.sproutstudio.model.delete
import es.hugoalvarezajenjo.sproutstudio.model.newDiagramIn
import es.hugoalvarezajenjo.sproutstudio.model.newFolderIn
import es.hugoalvarezajenjo.sproutstudio.model.rename
import java.io.File

/** Shows the dialog for [ProjectState.fileAction]: a name prompt, or a Trash confirmation. */
@Composable
internal fun FileActionDialog(p: ProjectState) {
    when (val a = p.fileAction ?: return) {
        is FileAction.NewDiagram -> NameDialog(
            title = "New Diagram", label = "Name", confirm = "Create",
            initial = FileOps.freeName(a.dir, "new-diagram", ".puml"), selectBase = true,
            hint = "in ${where(p, a.dir)} · .puml is added if missing",
            check = { n -> if (n.isBlank()) "Enter a name" else FileOps.validate(a.dir, FileOps.diagramName(n)) },
            onDismiss = { p.fileAction = null },
        ) { p.newDiagramIn(a.dir, it).exceptionOrNull()?.message }

        is FileAction.NewFolder -> NameDialog(
            title = "New Folder", label = "Name", confirm = "Create",
            initial = FileOps.freeName(a.dir, "new-folder"), selectBase = false,
            hint = "in ${where(p, a.dir)}",
            check = { FileOps.validate(a.dir, it) },
            onDismiss = { p.fileAction = null },
        ) { p.newFolderIn(a.dir, it).exceptionOrNull()?.message }

        is FileAction.Rename -> NameDialog(
            title = if (a.target.isDirectory) "Rename Folder" else "Rename File", label = "New name", confirm = "Rename",
            initial = a.target.name, selectBase = !a.target.isDirectory,
            hint = null,
            check = { FileOps.validate(a.target.absoluteFile.parentFile, it, except = a.target) },
            onDismiss = { p.fileAction = null },
        ) { p.rename(a.target, it).exceptionOrNull()?.message }

        is FileAction.Delete -> DeleteDialog(p, a.target)
    }
}

/** "project/sub" for the hint line under a name field. */
private fun where(p: ProjectState, dir: File): String {
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
    /** Select the name without its extension, so typing replaces just the name. */
    selectBase: Boolean,
    hint: String?,
    check: (String) -> String?,
    onDismiss: () -> Unit,
    /** Runs the operation; returns an error to show, or null when done. */
    onConfirm: (String) -> String?,
) {
    val c = ide
    var value by remember {
        val end = if (selectBase && initial.contains('.')) initial.lastIndexOf('.') else initial.length
        mutableStateOf(TextFieldValue(initial, TextRange(0, end)))
    }
    var failed by remember { mutableStateOf<String?>(null) }
    val invalid = check(value.text)
    val error = failed ?: invalid.takeIf { value.text != initial || title.startsWith("New") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        if (invalid != null) return
        failed = onConfirm(value.text)
        if (failed == null) onDismiss()
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
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = invalid == null, modifier = Modifier.testTag("file-name-confirm")) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DeleteDialog(p: ProjectState, target: File) {
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
                failed = p.delete(f).exceptionOrNull()?.message
                if (failed == null) p.fileAction = null
            }, modifier = Modifier.testTag("file-delete-confirm")) { Text("Move to Trash", color = c.error) }
        },
        dismissButton = { TextButton(onClick = { p.fileAction = null }) { Text("Cancel") } },
    )
}

/** Shows [f] selected in Finder (or opens its folder elsewhere). */
internal fun revealInFinder(f: File) {
    runCatching {
        if (Dialogs.isMac) ProcessBuilder("open", "-R", f.absolutePath).start()
        else java.awt.Desktop.getDesktop().open(if (f.isDirectory) f else f.absoluteFile.parentFile)
    }
}
