package es.hugoalvarezajenjo.sproutstudio.features.palette

import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.cat_insert
import es.hugoalvarezajenjo.sproutstudio.resources.cat_preview
import es.hugoalvarezajenjo.sproutstudio.resources.cat_symbol
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_usages
import es.hugoalvarezajenjo.sproutstudio.resources.menu_go_to_declaration
import es.hugoalvarezajenjo.sproutstudio.resources.menu_navigate_back
import es.hugoalvarezajenjo.sproutstudio.resources.menu_navigate_forward
import es.hugoalvarezajenjo.sproutstudio.resources.cat_recent_project
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_autosave_off
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_autosave_on
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_copy_image
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_dark_theme
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_duplicate_line
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_export_pdf
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_export_png
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_export_svg
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_font_bigger
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_font_reset
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_font_smaller
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_hide_commit_panel
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_hide_preview
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_hide_problems
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_hide_project_panel
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_insert_block
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_insert_template
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_light_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_light_theme
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_new_diagram_in_folder
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_new_folder
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_rename_file
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_toggle_comment
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_trash_file
import es.hugoalvarezajenjo.sproutstudio.resources.menu_close_project
import es.hugoalvarezajenjo.sproutstudio.resources.menu_close_tab
import es.hugoalvarezajenjo.sproutstudio.resources.menu_code
import es.hugoalvarezajenjo.sproutstudio.resources.menu_commit
import es.hugoalvarezajenjo.sproutstudio.resources.menu_compare_with_head
import es.hugoalvarezajenjo.sproutstudio.resources.menu_create_git_repo
import es.hugoalvarezajenjo.sproutstudio.resources.menu_dark_diagram_preview
import es.hugoalvarezajenjo.sproutstudio.resources.menu_edit
import es.hugoalvarezajenjo.sproutstudio.resources.menu_file
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_next
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_previous
import es.hugoalvarezajenjo.sproutstudio.resources.menu_fold_all
import es.hugoalvarezajenjo.sproutstudio.resources.menu_fold_block
import es.hugoalvarezajenjo.sproutstudio.resources.menu_git
import es.hugoalvarezajenjo.sproutstudio.resources.menu_new_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.menu_new_from_template
import es.hugoalvarezajenjo.sproutstudio.resources.menu_new_window
import es.hugoalvarezajenjo.sproutstudio.resources.menu_open_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.menu_open_folder
import es.hugoalvarezajenjo.sproutstudio.resources.menu_refresh_files
import es.hugoalvarezajenjo.sproutstudio.resources.menu_replace
import es.hugoalvarezajenjo.sproutstudio.resources.menu_rollback_lines
import es.hugoalvarezajenjo.sproutstudio.resources.menu_save
import es.hugoalvarezajenjo.sproutstudio.resources.menu_save_as
import es.hugoalvarezajenjo.sproutstudio.resources.menu_settings
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_commit_panel
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_diff_with_head
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_history
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_preview
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_problems
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_project_panel
import es.hugoalvarezajenjo.sproutstudio.resources.menu_stash_changes
import es.hugoalvarezajenjo.sproutstudio.resources.menu_undo_last_commit
import es.hugoalvarezajenjo.sproutstudio.resources.menu_unfold_all
import es.hugoalvarezajenjo.sproutstudio.resources.menu_unfold_block
import es.hugoalvarezajenjo.sproutstudio.resources.menu_view
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text

import es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction
import es.hugoalvarezajenjo.sproutstudio.features.workspace.SidebarTool
import es.hugoalvarezajenjo.sproutstudio.features.workspace.chooseFolder

import es.hugoalvarezajenjo.sproutstudio.features.workspace.openDiagramDialog

import es.hugoalvarezajenjo.sproutstudio.core.os.Os

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.EditOps
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SymbolIndex
import es.hugoalvarezajenjo.sproutstudio.features.editor.navigateTo
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Templates
import es.hugoalvarezajenjo.sproutstudio.features.document.PreviewAction
import es.hugoalvarezajenjo.sproutstudio.features.editor.EditorSettings
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
    cmd("file.new", text(Res.string.menu_new_diagram), text(Res.string.menu_file), shortcutHint("N")) { p.newDocument() }
    cmd("file.gallery", text(Res.string.menu_new_from_template), text(Res.string.menu_file), shortcutHint("N", shift = true), restoresFocus = false) { onOpenGallery() }
    cmd("file.openDiagram", text(Res.string.menu_open_diagram), text(Res.string.menu_file), shortcutHint("O", shift = true)) { p.openDiagramDialog() }
    cmd("file.openFolder", text(Res.string.menu_open_folder), text(Res.string.menu_file), shortcutHint("O")) { p.chooseFolder()?.let { p.openRoot(it) } }
    cmd("file.newWindow", text(Res.string.menu_new_window), text(Res.string.menu_file)) { p.deps.windows.newProjectWindow() }
    p.root?.let { root ->
        val dir = d?.file?.absoluteFile?.parentFile?.takeIf { it == root.absoluteFile || it.startsWith(root.absoluteFile) } ?: root
        cmd("file.newNamed", text(Res.string.cmd_new_diagram_in_folder), text(Res.string.menu_file), restoresFocus = false) { p.explorer.showFileAction(FileAction.NewDiagram(dir)) }
        cmd("file.newFolder", text(Res.string.cmd_new_folder), text(Res.string.menu_file), restoresFocus = false) { p.explorer.showFileAction(FileAction.NewFolder(dir)) }
        d?.file?.let { f ->
            cmd("file.rename", text(Res.string.cmd_rename_file), text(Res.string.menu_file), restoresFocus = false) { p.explorer.showFileAction(FileAction.Rename(f)) }
            cmd("file.trash", text(Res.string.cmd_trash_file), text(Res.string.menu_file), restoresFocus = false) { p.explorer.showFileAction(FileAction.Delete(f)) }
        }
    }
    cmd("file.save", text(Res.string.menu_save), text(Res.string.menu_file), shortcutHint("S"), enabled = has) { onSave() }
    cmd("file.saveAs", text(Res.string.menu_save_as), text(Res.string.menu_file), shortcutHint("S", shift = true), enabled = has) { onSaveAs() }
    cmd("file.autosave", if (p.deps.autoSaver.enabled) text(Res.string.cmd_autosave_off) else text(Res.string.cmd_autosave_on), text(Res.string.menu_file)) {
        p.setAutoSave(!p.deps.autoSaver.enabled)
    }
    cmd("file.closeTab", text(Res.string.menu_close_tab), text(Res.string.menu_file), shortcutHint("W"), enabled = has) { onCloseTab() }
    cmd("file.closeProject", text(Res.string.menu_close_project), text(Res.string.menu_file), enabled = p.root != null || has, restoresFocus = false) { onCloseProject() }

    // Edit
    cmd("edit.find", text(Res.string.menu_find), text(Res.string.menu_edit), shortcutHint("F"), enabled = has, restoresFocus = false) {
        d?.let { p.revealEditor(); it.find.open(replace = false, seed = it.selectedText()) }
    }
    cmd("edit.replace", text(Res.string.menu_replace), text(Res.string.menu_edit), shortcutHint("R"), enabled = has, restoresFocus = false) {
        d?.let { p.revealEditor(); it.find.open(replace = true, seed = it.selectedText()) }
    }
    cmd("edit.findNext", text(Res.string.menu_find_next), text(Res.string.menu_edit), shortcutHint("G"), enabled = has) { p.revealEditor(); d?.findNext(true) }
    cmd("edit.findPrev", text(Res.string.menu_find_previous), text(Res.string.menu_edit), shortcutHint("G", shift = true), enabled = has) { p.revealEditor(); d?.findNext(false) }
    cmd("edit.comment", text(Res.string.cmd_toggle_comment), text(Res.string.menu_edit), shortcutHint("/"), enabled = has) {
        d?.let { p.revealEditor(); it.value = EditOps.toggleComment(it.value) }
    }
    cmd("edit.duplicate", text(Res.string.cmd_duplicate_line), text(Res.string.menu_edit), shortcutHint("D"), enabled = has) {
        d?.let { p.revealEditor(); it.value = EditOps.duplicateLines(it.value) }
    }

    // Code
    cmd("code.fold", text(Res.string.menu_fold_block), text(Res.string.menu_code), shortcutHint("-"), enabled = has) { p.revealEditor(); d?.foldAtCaret() }
    cmd("code.unfold", text(Res.string.menu_unfold_block), text(Res.string.menu_code), shortcutHint("="), enabled = has) { p.revealEditor(); d?.unfoldAtCaret() }
    cmd("code.foldAll", text(Res.string.menu_fold_all), text(Res.string.menu_code), shortcutHint("-", shift = true), enabled = has) { p.revealEditor(); d?.foldAll() }
    cmd("code.unfoldAll", text(Res.string.menu_unfold_all), text(Res.string.menu_code), shortcutHint("=", shift = true), enabled = has) { p.revealEditor(); d?.unfoldAll() }

    // Code → Navigate
    val altCmd = if (Os.isMac) "⌥⌘" else "Ctrl+Alt+"
    cmd("code.declaration", text(Res.string.menu_go_to_declaration), text(Res.string.menu_code), shortcutHint("B"), enabled = has) { p.goToDeclaration() }
    cmd("code.usages", text(Res.string.menu_find_usages), text(Res.string.menu_code), if (Os.isMac) "⌥F7" else "Alt+F7", enabled = has) { p.findUsages() }
    cmd("code.back", text(Res.string.menu_navigate_back), text(Res.string.menu_code), "$altCmd←", enabled = d?.nav?.canGoBack == true) { p.navigateBack() }
    cmd("code.forward", text(Res.string.menu_navigate_forward), text(Res.string.menu_code), "$altCmd→", enabled = d?.nav?.canGoForward == true) { p.navigateForward() }

    // View
    EditorLayout.entries.forEach { l ->
        cmd("view.layout.${l.name}", text(l.label), text(Res.string.menu_view), shortcutHint("${l.ordinal + 1}", alt = true), enabled = p.layout != l) { p.changeLayout(l) }
    }
    cmd("view.preview", if (p.previewVisible) text(Res.string.cmd_hide_preview) else text(Res.string.menu_show_preview), text(Res.string.menu_view), shortcutHint("P")) { p.togglePreview() }
    cmd("view.problems", if (p.problemsExpanded) text(Res.string.cmd_hide_problems) else text(Res.string.menu_show_problems), text(Res.string.menu_view), shortcutHint("6"), enabled = has) { p.toggleProblems() }
    if (p.root != null) {
        cmd("view.sidebar", if (p.sidebarVisible) text(Res.string.cmd_hide_project_panel) else text(Res.string.menu_show_project_panel), text(Res.string.menu_view), shortcutHint("1")) {
            p.showSidebar(!p.sidebarVisible)
        }
        cmd("view.refresh", text(Res.string.menu_refresh_files), text(Res.string.menu_view), shortcutHint("Y", alt = true)) { p.explorer.refreshTree() }
        cmd("view.commitPanel", if (p.sidebarVisible && p.sidebarTool == SidebarTool.COMMIT) text(Res.string.cmd_hide_commit_panel) else text(Res.string.menu_show_commit_panel), text(Res.string.menu_view), shortcutHint("0")) {
            p.toggleTool(SidebarTool.COMMIT)
        }
        // Git
        cmd("git.commit", text(Res.string.menu_commit), text(Res.string.menu_git), shortcutHint("K"), restoresFocus = false) { p.focusCommit() }
        cmd("git.compare", text(Res.string.menu_compare_with_head), text(Res.string.menu_git), enabled = canCompareWithHead(p, d?.file)) {
            d?.file?.let { p.deps.windows.compareWithHead(p, it) }
        }
        cmd("git.rollbackLines", text(Res.string.menu_rollback_lines), text(Res.string.menu_git), shortcutHint("Z", alt = true), enabled = d?.let { it.changeAtCaret() } != null) {
            p.revealEditor(); d?.let { it.rollbackAtCaret() }
        }
        cmd("git.diff", text(Res.string.menu_show_diff_with_head), text(Res.string.menu_git), enabled = hasCommittedChanges(p, d?.file)) {
            d?.file?.let { p.deps.windows.compareWithHead(p, it, textFirst = true) }
        }
        cmd("git.history", text(Res.string.menu_show_history), text(Res.string.menu_git), enabled = p.git.status.head != null && d?.file != null) {
            d?.file?.let { p.deps.windows.showHistory(p, it) }
        }
        cmd("git.undo", text(Res.string.menu_undo_last_commit), text(Res.string.menu_git), enabled = (p.git.lastCommit?.parents ?: 0) > 0, restoresFocus = false) {
            p.gitFlow.undoLastCommit(showCommitPanel = true)
        }
        cmd("git.stash", text(Res.string.menu_stash_changes), text(Res.string.menu_git), enabled = p.git.status.head != null, restoresFocus = false) {
            p.showTool(SidebarTool.COMMIT); p.git.requestStashPopup()
        }
        if (!p.git.isRepo) cmd("git.init", text(Res.string.menu_create_git_repo), text(Res.string.menu_git)) {
            p.showTool(SidebarTool.COMMIT)
            p.gitFlow.init()
        }
    }
    cmd("view.theme", if (p.deps.theme.dark.value) text(Res.string.cmd_light_theme) else text(Res.string.cmd_dark_theme), text(Res.string.menu_view)) { p.deps.theme.toggle() }
    cmd("app.settings", text(Res.string.menu_settings), "SproutStudio", if (Os.isMac) "⌘," else "Ctrl+,", restoresFocus = false) { p.deps.windows.openSettings() }
    val editor = p.deps.editor
    cmd("view.fontBigger", text(Res.string.cmd_font_bigger), text(Res.string.menu_view), enabled = editor.currentFontSize < EditorSettings.MAX_FONT_SIZE) { editor.changeFontSize(1f) }
    cmd("view.fontSmaller", text(Res.string.cmd_font_smaller), text(Res.string.menu_view), enabled = editor.currentFontSize > EditorSettings.MIN_FONT_SIZE) { editor.changeFontSize(-1f) }
    cmd("view.fontReset", text(Res.string.cmd_font_reset), text(Res.string.menu_view), enabled = !editor.fontSize.isDefault) { editor.fontSize.reset() }
    cmd("view.diagramDark", if (p.deps.diagram.dark.value) text(Res.string.cmd_light_diagram) else text(Res.string.menu_dark_diagram_preview), text(Res.string.cat_preview)) { p.deps.diagram.toggle() }

    // Preview: export goes through the preview pane (it knows which diagram is showing).
    fun previewCmd(id: String, title: String, a: PreviewAction) = cmd(id, title, text(Res.string.cat_preview), enabled = has) {
        if (!p.previewVisible) p.changeLayout(EditorLayout.SPLIT)
        d?.requestPreview(a)
    }
    previewCmd("preview.copy", text(Res.string.cmd_copy_image), PreviewAction.COPY_IMAGE)
    previewCmd("preview.svg", text(Res.string.cmd_export_svg), PreviewAction.EXPORT_SVG)
    previewCmd("preview.png", text(Res.string.cmd_export_png), PreviewAction.EXPORT_PNG)
    previewCmd("preview.pdf", text(Res.string.cmd_export_pdf), PreviewAction.EXPORT_PDF)

    // Insert a template at the caret (whole set, including the blocks).
    Templates.all.forEach { t ->
        cmd("insert.${t.id}", text(if (t.whole) Res.string.cmd_insert_template else Res.string.cmd_insert_block, t.name), text(Res.string.cat_insert), enabled = has) {
            d?.let { p.revealEditor(); it.value = EditOps.insertTemplate(it.value, t) }
        }
    }

    // Symbols of this file: type part of a name to jump to its declaration.
    d?.let { doc ->
        SymbolIndex.of(doc.text).definitions.forEach { s ->
            out += Command(
                "symbol:${s.name}:${s.range.first}", s.name, text(Res.string.cat_symbol, "${s.kind} · ${s.line}"),
                searchOnly = true,
            ) { p.revealEditor(); doc.navigateTo(s.range) }
        }
    }

    // Files of this project: type part of a name to open it.
    val root = p.root
    p.explorer.allDiagrams().forEach { f ->
        val rel = root?.let { f.parentFile.relativeTo(it.absoluteFile).path }.orEmpty()
        out += Command(
            "open:${f.path}", f.name, if (rel.isEmpty()) "${root?.name ?: ""}/" else "$rel/",
            searchOnly = true,
        ) { p.open(f) }
    }
    p.deps.recents.folders().filter { it.absoluteFile != root?.absoluteFile }.forEach { dir ->
        out += Command("recent:${dir.path}", dir.name, text(Res.string.cat_recent_project), searchOnly = true) { p.deps.windows.openFolder(dir) }
    }
    return out
}
