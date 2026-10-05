package es.hugoalvarezajenjo.sproutstudio.features.palette

import es.hugoalvarezajenjo.sproutstudio.features.workspace.chooseFolder

import es.hugoalvarezajenjo.sproutstudio.features.workspace.openDiagramDialog

import es.hugoalvarezajenjo.sproutstudio.core.os.Os

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.EditOps
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Templates
import es.hugoalvarezajenjo.sproutstudio.features.document.PreviewAction
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
import es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import kotlinx.coroutines.launch

/** Shortcut label in the platform's style: "⇧⌘F" on macOS, "Ctrl+Shift+F" elsewhere. */
internal fun shortcutHint(key: String, shift: Boolean = false, alt: Boolean = false): String =
    if (Os.isMac) buildString { if (alt) append('⌥'); if (shift) append('⇧'); append('⌘'); append(key) }
    else buildString { append("Ctrl+"); if (alt) append("Alt+"); if (shift) append("Shift+"); append(key) }

/**
 * Everything the command palette can do in a project window: the menu actions, templates,
 * the project's diagrams and recent projects. Built fresh each time the palette opens, so
 * titles and enabled states reflect the current window.
 */
internal fun projectCommands(
    p: WorkspaceViewModel,
    onSave: () -> Unit,
    onSaveAs: () -> Unit,
    onCloseTab: () -> Unit,
    onOpenGallery: () -> Unit = {},
    onCloseProject: () -> Unit = {},
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
    cmd("file.gallery", "New from Template…", "File", shortcutHint("N", shift = true), restoresFocus = false) { onOpenGallery() }
    cmd("file.openDiagram", "Open Diagram…", "File", shortcutHint("O", shift = true)) { p.openDiagramDialog() }
    cmd("file.openFolder", "Open Folder…", "File", shortcutHint("O")) { p.chooseFolder()?.let { p.openRoot(it) } }
    cmd("file.newWindow", "New Window", "File") { p.deps.windows.newProjectWindow() }
    p.root?.let { root ->
        val dir = d?.file?.absoluteFile?.parentFile?.takeIf { it == root.absoluteFile || it.startsWith(root.absoluteFile) } ?: root
        cmd("file.newNamed", "New Diagram in Folder…", "File", restoresFocus = false) { p.fileAction = es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction.NewDiagram(dir) }
        cmd("file.newFolder", "New Folder…", "File", restoresFocus = false) { p.fileAction = es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction.NewFolder(dir) }
        d?.file?.let { f ->
            cmd("file.rename", "Rename File…", "File", restoresFocus = false) { p.fileAction = es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction.Rename(f) }
            cmd("file.trash", "Move File to Trash…", "File", restoresFocus = false) { p.fileAction = es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction.Delete(f) }
        }
    }
    cmd("file.save", "Save", "File", shortcutHint("S"), enabled = has) { onSave() }
    cmd("file.saveAs", "Save As…", "File", shortcutHint("S", shift = true), enabled = has) { onSaveAs() }
    cmd("file.autosave", if (p.deps.autoSaver.enabled) "Turn Off Save Automatically" else "Turn On Save Automatically", "File") {
        p.deps.autoSaver.setEnabled(!p.deps.autoSaver.enabled)
        if (p.deps.autoSaver.enabled) p.scope.launch { p.deps.autoSaver.flush(p.docs.toList()) }
    }
    cmd("file.closeTab", "Close Tab", "File", shortcutHint("W"), enabled = has) { onCloseTab() }
    cmd("file.closeProject", "Close Project", "File", enabled = p.root != null || has, restoresFocus = false) { onCloseProject() }

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
    cmd("view.problems", if (p.problemsExpanded) "Hide Problems" else "Show Problems", "View", shortcutHint("6"), enabled = has) { p.toggleProblems() }
    if (p.root != null) {
        cmd("view.sidebar", if (p.sidebarVisible) "Hide Project Panel" else "Show Project Panel", "View", shortcutHint("1")) {
            p.showSidebar(!p.sidebarVisible)
        }
        cmd("view.refresh", "Refresh Files", "View", shortcutHint("Y", alt = true)) { p.refreshTree() }
        cmd("view.commitPanel", if (p.sidebarVisible && p.sidebarTool == es.hugoalvarezajenjo.sproutstudio.features.workspace.SidebarTool.COMMIT) "Hide Commit Panel" else "Show Commit Panel", "View", shortcutHint("0")) {
            p.toggleTool(es.hugoalvarezajenjo.sproutstudio.features.workspace.SidebarTool.COMMIT)
        }
        // Git
        cmd("git.commit", "Commit…", "Git", shortcutHint("K"), restoresFocus = false) { p.focusCommit() }
        cmd("git.compare", "Compare Diagram with HEAD", "Git", enabled = canCompareWithHead(p, d?.file)) {
            d?.file?.let { p.deps.windows.compareWithHead(p, it) }
        }
        cmd("git.rollbackLines", "Rollback Lines", "Git", shortcutHint("Z", alt = true), enabled = d?.let { it.changeAtCaret() } != null) {
            p.revealEditor(); d?.let { it.rollbackAtCaret() }
        }
        cmd("git.diff", "Show Diff with HEAD", "Git", enabled = hasCommittedChanges(p, d?.file)) {
            d?.file?.let { p.deps.windows.compareWithHead(p, it, textFirst = true) }
        }
        cmd("git.history", "Show History for Current File", "Git", enabled = p.git.status.head != null && d?.file != null) {
            d?.file?.let { p.deps.windows.showHistory(p, it) }
        }
        cmd("git.undo", "Undo Last Commit", "Git", enabled = (p.git.lastCommit?.parents ?: 0) > 0, restoresFocus = false) {
            kotlinx.coroutines.MainScope().launch {
                p.git.undoLastCommit()?.let { u ->
                    if (p.git.commitMessage.isBlank()) p.git.commitMessage = u.fullMessage
                    p.git.notice = es.hugoalvarezajenjo.sproutstudio.features.git.GitNotice.Undid(u.short)
                    p.showTool(es.hugoalvarezajenjo.sproutstudio.features.workspace.SidebarTool.COMMIT); p.refreshTree()
                }
            }
        }
        cmd("git.stash", "Stash Changes…", "Git", enabled = p.git.status.head != null, restoresFocus = false) {
            p.showTool(es.hugoalvarezajenjo.sproutstudio.features.workspace.SidebarTool.COMMIT); p.git.requestStashPopup()
        }
        if (!p.git.isRepo) cmd("git.init", "Create Git Repository", "Git") {
            p.showTool(es.hugoalvarezajenjo.sproutstudio.features.workspace.SidebarTool.COMMIT)
            kotlinx.coroutines.MainScope().launch { p.git.init() }
        }
    }
    cmd("view.theme", if (p.deps.theme.dark.value) "Switch to Light Theme" else "Switch to Dark Theme", "View") { p.deps.theme.toggle() }
    cmd("view.diagramDark", if (p.deps.diagram.dark.value) "Light Diagram Preview" else "Dark Diagram Preview", "Preview") { p.deps.diagram.toggle() }

    // Preview: export goes through the preview pane (it knows which diagram is showing).
    fun previewCmd(id: String, title: String, a: PreviewAction) = cmd(id, title, "Preview", enabled = has) {
        if (!p.previewVisible) p.changeLayout(EditorLayout.SPLIT)
        d?.requestPreview(a)
    }
    previewCmd("preview.copy", "Copy Diagram Image", PreviewAction.COPY_IMAGE)
    previewCmd("preview.svg", "Export Diagram as SVG…", PreviewAction.EXPORT_SVG)
    previewCmd("preview.png", "Export Diagram as PNG…", PreviewAction.EXPORT_PNG)
    previewCmd("preview.pdf", "Export Diagram as PDF…", PreviewAction.EXPORT_PDF)

    // Insert a template at the caret (whole set, including the blocks).
    Templates.all.forEach { t ->
        val suffix = if (t.whole) "Template" else "Block"
        cmd("insert.${t.id}", "Insert ${t.name} $suffix", "Insert", enabled = has) {
            d?.let { p.revealEditor(); it.value = EditOps.insertTemplate(it.value, t) }
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
    p.deps.recents.folders().filter { it.absoluteFile != root?.absoluteFile }.forEach { dir ->
        out += Command("recent:${dir.path}", dir.name, "Recent Project", searchOnly = true) { p.deps.windows.openFolder(dir) }
    }
    return out
}
