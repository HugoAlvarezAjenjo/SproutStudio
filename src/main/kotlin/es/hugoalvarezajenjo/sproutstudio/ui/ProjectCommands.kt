package es.hugoalvarezajenjo.sproutstudio.ui

import es.hugoalvarezajenjo.sproutstudio.editor.EditOps
import es.hugoalvarezajenjo.sproutstudio.editor.changeAtCaret
import es.hugoalvarezajenjo.sproutstudio.editor.rollbackAtCaret
import es.hugoalvarezajenjo.sproutstudio.editor.foldAll
import es.hugoalvarezajenjo.sproutstudio.editor.foldAtCaret
import es.hugoalvarezajenjo.sproutstudio.editor.findNext
import es.hugoalvarezajenjo.sproutstudio.editor.selectedText
import es.hugoalvarezajenjo.sproutstudio.editor.unfoldAll
import es.hugoalvarezajenjo.sproutstudio.editor.unfoldAtCaret
import es.hugoalvarezajenjo.sproutstudio.lang.CompletionEngine
import es.hugoalvarezajenjo.sproutstudio.model.AppState
import es.hugoalvarezajenjo.sproutstudio.model.AutoSave
import es.hugoalvarezajenjo.sproutstudio.model.AutoSavePrefs
import es.hugoalvarezajenjo.sproutstudio.model.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.model.PreviewAction
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import es.hugoalvarezajenjo.sproutstudio.model.Recents
import kotlinx.coroutines.launch

/** Shortcut label in the platform's style: "⇧⌘F" on macOS, "Ctrl+Shift+F" elsewhere. */
internal fun shortcutHint(key: String, shift: Boolean = false, alt: Boolean = false): String =
    if (Dialogs.isMac) buildString { if (alt) append('⌥'); if (shift) append('⇧'); append('⌘'); append(key) }
    else buildString { append("Ctrl+"); if (alt) append("Alt+"); if (shift) append("Shift+"); append(key) }

/**
 * Everything the command palette can do in a project window: the menu actions, templates,
 * the project's diagrams and recent projects. Built fresh each time the palette opens, so
 * titles and enabled states reflect the current window.
 */
internal fun projectCommands(
    p: ProjectState,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onCloseTab: () -> Unit,
): List<Command> {
    val d = p.active
    val has = d != null
    val out = ArrayList<Command>()
    fun cmd(
        id: String, title: String, category: String, shortcut: String? = null,
        enabled: Boolean = true, restoresFocus: Boolean = true, run: () -> Unit,
    ) { out += Command(id, title, category, shortcut, enabled, restoresFocus, run = run) }

    // File
    cmd("file.new", "New Diagram", "File", shortcutHint("N")) { p.newDocument() }
    cmd("file.openDiagram", "Open Diagram…", "File", shortcutHint("O", shift = true)) { Dialogs.openDiagram()?.let { AppState.openFile(it) } }
    cmd("file.openFolder", "Open Folder…", "File", shortcutHint("O")) { Dialogs.openFolder()?.let { p.openRoot(it) } }
    cmd("file.newWindow", "New Window", "File") { AppState.newProjectWindow() }
    cmd("file.save", "Save", "File", shortcutHint("S"), enabled = has) { onSave() }
    cmd("file.saveAs", "Save As…", "File", shortcutHint("S", shift = true), enabled = has) { onSaveAs() }
    cmd("file.autosave", if (AutoSavePrefs.enabled) "Turn Off Save Automatically" else "Turn On Save Automatically", "File") {
        AutoSavePrefs.enabled = !AutoSavePrefs.enabled
        if (AutoSavePrefs.enabled) AutoSave.flush(p.docs.toList())
    }
    cmd("file.closeTab", "Close Tab", "File", shortcutHint("W"), enabled = has) { onCloseTab() }

    // Edit
    cmd("edit.find", "Find…", "Edit", shortcutHint("F"), enabled = has, restoresFocus = false) {
        d?.let { p.revealEditor(); it.find.open(replace = false, seed = it.selectedText()) }
    }
    cmd("edit.replace", "Replace…", "Edit", shortcutHint("R"), enabled = has, restoresFocus = false) {
        d?.let { p.revealEditor(); it.find.open(replace = true, seed = it.selectedText()) }
    }
    cmd("edit.findNext", "Find Next", "Edit", shortcutHint("G"), enabled = has) { p.revealEditor(); d?.findNext(true) }
    cmd("edit.findPrev", "Find Previous", "Edit", shortcutHint("G", shift = true), enabled = has) { p.revealEditor(); d?.findNext(false) }
    cmd("edit.comment", "Comment / Uncomment Lines", "Edit", shortcutHint("/"), enabled = has) {
        d?.let { p.revealEditor(); it.value = EditOps.toggleComment(it.value) }
    }
    cmd("edit.duplicate", "Duplicate Line", "Edit", shortcutHint("D"), enabled = has) {
        d?.let { p.revealEditor(); it.value = EditOps.duplicateLines(it.value) }
    }

    // Code
    cmd("code.fold", "Fold Block", "Code", shortcutHint("-"), enabled = has) { p.revealEditor(); d?.foldAtCaret() }
    cmd("code.unfold", "Unfold Block", "Code", shortcutHint("="), enabled = has) { p.revealEditor(); d?.unfoldAtCaret() }
    cmd("code.foldAll", "Fold All", "Code", shortcutHint("-", shift = true), enabled = has) { p.revealEditor(); d?.foldAll() }
    cmd("code.unfoldAll", "Unfold All", "Code", shortcutHint("=", shift = true), enabled = has) { p.revealEditor(); d?.unfoldAll() }

    // View
    EditorLayout.entries.forEach { l ->
        cmd("view.layout.${l.name}", l.label, "View", shortcutHint("${l.ordinal + 1}", alt = true), enabled = p.layout != l) { p.changeLayout(l) }
    }
    cmd("view.preview", if (p.previewVisible) "Hide Preview" else "Show Preview", "View", shortcutHint("P")) { p.togglePreview() }
    if (p.root != null) {
        cmd("view.sidebar", if (p.sidebarVisible) "Hide Project Panel" else "Show Project Panel", "View", shortcutHint("1")) {
            p.showSidebar(!p.sidebarVisible)
        }
        cmd("view.refresh", "Refresh Files", "View", shortcutHint("Y", alt = true)) { p.refreshTree() }
        cmd("view.commitPanel", if (p.sidebarVisible && p.sidebarTool == es.hugoalvarezajenjo.sproutstudio.model.SidebarTool.COMMIT) "Hide Commit Panel" else "Show Commit Panel", "View", shortcutHint("0")) {
            p.toggleTool(es.hugoalvarezajenjo.sproutstudio.model.SidebarTool.COMMIT)
        }
        // Git
        cmd("git.commit", "Commit…", "Git", shortcutHint("K"), restoresFocus = false) { p.focusCommit() }
        cmd("git.compare", "Compare Diagram with HEAD", "Git", enabled = canCompareWithHead(p, d?.file)) {
            d?.file?.let { AppState.compareWithHead(p, it) }
        }
        cmd("git.rollbackLines", "Rollback Lines", "Git", shortcutHint("Z", alt = true), enabled = d?.let { it.changeAtCaret() } != null) {
            p.revealEditor(); d?.let { it.rollbackAtCaret() }
        }
        cmd("git.diff", "Show Diff with HEAD", "Git", enabled = hasCommittedChanges(p, d?.file)) {
            d?.file?.let { AppState.compareWithHead(p, it, textFirst = true) }
        }
        cmd("git.history", "Show History for Current File", "Git", enabled = p.git.status.head != null && d?.file != null) {
            d?.file?.let { AppState.showHistory(p, it) }
        }
        cmd("git.undo", "Undo Last Commit", "Git", enabled = (p.git.lastCommit?.parents ?: 0) > 0, restoresFocus = false) {
            kotlinx.coroutines.MainScope().launch {
                p.git.undoLastCommit()?.let { u ->
                    if (p.git.commitMessage.isBlank()) p.git.commitMessage = u.fullMessage
                    p.git.lastResult = "Undid ${u.short}: its changes are back in the list"
                    p.showTool(es.hugoalvarezajenjo.sproutstudio.model.SidebarTool.COMMIT); p.refreshTree()
                }
            }
        }
        cmd("git.stash", "Stash Changes…", "Git", enabled = p.git.status.head != null, restoresFocus = false) {
            p.showTool(es.hugoalvarezajenjo.sproutstudio.model.SidebarTool.COMMIT); p.git.stashPopupTick++
        }
        if (!p.git.isRepo) cmd("git.init", "Create Git Repository", "Git") {
            p.showTool(es.hugoalvarezajenjo.sproutstudio.model.SidebarTool.COMMIT)
            kotlinx.coroutines.MainScope().launch { p.git.init() }
        }
    }
    cmd("view.theme", if (ThemePrefs.dark) "Switch to Light Theme" else "Switch to Dark Theme", "View") { ThemePrefs.toggle() }
    cmd("view.diagramDark", if (DiagramPrefs.dark) "Light Diagram Preview" else "Dark Diagram Preview", "Preview") { DiagramPrefs.toggle() }

    // Preview: export goes through the preview pane (it knows which diagram is showing).
    fun previewCmd(id: String, title: String, a: PreviewAction) = cmd(id, title, "Preview", enabled = has) {
        if (!p.previewVisible) p.changeLayout(EditorLayout.SPLIT)
        d?.previewRequest = a
    }
    previewCmd("preview.copy", "Copy Diagram Image", PreviewAction.COPY_IMAGE)
    previewCmd("preview.svg", "Export Diagram as SVG…", PreviewAction.EXPORT_SVG)
    previewCmd("preview.png", "Export Diagram as PNG…", PreviewAction.EXPORT_PNG)

    // Insert a template at the caret.
    CompletionEngine.snippets.forEach { s ->
        val name = s.detail.ifEmpty { s.label }
        cmd("insert.${s.label}", "Insert $name Template", "Insert", enabled = has) {
            d?.let { p.revealEditor(); it.value = EditOps.insertSnippet(it.value, s.insert) }
        }
    }

    // Files of this project: type part of a name to open it.
    val root = p.root
    p.allDiagrams().forEach { f ->
        val rel = root?.let { f.parentFile.relativeTo(it.absoluteFile).path }.orEmpty()
        out += Command(
            "open:${f.path}", f.name, if (rel.isEmpty()) "${root?.name ?: ""}/" else "$rel/",
            searchOnly = true,
        ) { p.open(f) }
    }
    Recents.folders().filter { it.absoluteFile != root?.absoluteFile }.forEach { dir ->
        out += Command("recent:${dir.path}", dir.name, "Recent Project", searchOnly = true) { AppState.openFolder(dir) }
    }
    return out
}
