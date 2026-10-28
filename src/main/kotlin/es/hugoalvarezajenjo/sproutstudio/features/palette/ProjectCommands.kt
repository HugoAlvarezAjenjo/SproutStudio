package es.hugoalvarezajenjo.sproutstudio.features.palette

import es.hugoalvarezajenjo.sproutstudio.core.os.shortcutHint
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.cat_insert
import es.hugoalvarezajenjo.sproutstudio.resources.cat_preview
import es.hugoalvarezajenjo.sproutstudio.resources.cat_symbol
import es.hugoalvarezajenjo.sproutstudio.resources.cat_recent_project
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_autosave_off
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_autosave_on
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_copy_image
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_dark_theme
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_duplicate_line
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_redo
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_undo
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_move_line_down
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_move_line_up
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
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_toggle_block_comment
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_trash_file
import es.hugoalvarezajenjo.sproutstudio.resources.menu_close_project
import es.hugoalvarezajenjo.sproutstudio.resources.menu_close_tab
import es.hugoalvarezajenjo.sproutstudio.resources.menu_code
import es.hugoalvarezajenjo.sproutstudio.resources.menu_commit
import es.hugoalvarezajenjo.sproutstudio.resources.menu_create_git_repo
import es.hugoalvarezajenjo.sproutstudio.resources.menu_dark_diagram_preview
import es.hugoalvarezajenjo.sproutstudio.resources.menu_soft_wrap
import es.hugoalvarezajenjo.sproutstudio.resources.menu_edit
import es.hugoalvarezajenjo.sproutstudio.resources.menu_file
import es.hugoalvarezajenjo.sproutstudio.resources.menu_git
import es.hugoalvarezajenjo.sproutstudio.resources.menu_new_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.menu_new_from_template
import es.hugoalvarezajenjo.sproutstudio.resources.menu_new_window
import es.hugoalvarezajenjo.sproutstudio.resources.menu_open_diagram
import es.hugoalvarezajenjo.sproutstudio.resources.menu_open_folder
import es.hugoalvarezajenjo.sproutstudio.resources.menu_refresh_files
import es.hugoalvarezajenjo.sproutstudio.resources.menu_save
import es.hugoalvarezajenjo.sproutstudio.resources.menu_save_as
import es.hugoalvarezajenjo.sproutstudio.resources.menu_settings
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_commit_panel
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_preview
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_problems
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_project_panel
import es.hugoalvarezajenjo.sproutstudio.resources.menu_stash_changes
import es.hugoalvarezajenjo.sproutstudio.resources.menu_undo_last_commit
import es.hugoalvarezajenjo.sproutstudio.resources.menu_view
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text

import es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction
import es.hugoalvarezajenjo.sproutstudio.features.workspace.SidebarTool
import es.hugoalvarezajenjo.sproutstudio.features.workspace.chooseFolder

import es.hugoalvarezajenjo.sproutstudio.features.workspace.openDiagramDialog


import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.BlockComment
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.EditOps
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.MoveLines
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SymbolIndex
import es.hugoalvarezajenjo.sproutstudio.features.editor.navigateTo
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Templates
import es.hugoalvarezajenjo.sproutstudio.features.document.PreviewAction
import es.hugoalvarezajenjo.sproutstudio.features.editor.EditorSettings
import es.hugoalvarezajenjo.sproutstudio.features.editor.findNext
import es.hugoalvarezajenjo.sproutstudio.features.editor.foldAll
import es.hugoalvarezajenjo.sproutstudio.features.editor.unfoldAll
import es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Action

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
    val a = p.actions
    fun action(x: Action, category: String) = cmd(x.id, text(x.title), category, x.shortcut?.hint, x.enabled, x.restoresFocus, x.run)

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
    cmd("edit.undo", text(Res.string.cmd_undo), text(Res.string.menu_edit), shortcutHint("Z"), enabled = d?.canUndo == true) { p.revealEditor(); d?.undo() }
    cmd("edit.redo", text(Res.string.cmd_redo), text(Res.string.menu_edit), shortcutHint("Z", shift = true), enabled = d?.canRedo == true) { p.revealEditor(); d?.redo() }
    action(a.find, text(Res.string.menu_edit))
    action(a.replace, text(Res.string.menu_edit))
    action(a.findNext, text(Res.string.menu_edit))
    action(a.findPrevious, text(Res.string.menu_edit))
    action(a.findInFiles, text(Res.string.menu_edit))
    action(a.toggleComment, text(Res.string.menu_edit))
    cmd("edit.blockComment", text(Res.string.cmd_toggle_block_comment), text(Res.string.menu_edit), shortcutHint("/", alt = true), enabled = has) {
        d?.let { p.revealEditor(); it.value = BlockComment.toggle(it.value) }
    }
    cmd("edit.duplicate", text(Res.string.cmd_duplicate_line), text(Res.string.menu_edit), shortcutHint("D"), enabled = has) {
        d?.let { p.revealEditor(); it.value = EditOps.duplicateLines(it.value) }
    }
    cmd("edit.moveLineUp", text(Res.string.cmd_move_line_up), text(Res.string.menu_edit), shortcutHint("↑", alt = true, command = false), enabled = has) {
        d?.let { p.revealEditor(); it.value = MoveLines.up(it.value) }
    }
    cmd("edit.moveLineDown", text(Res.string.cmd_move_line_down), text(Res.string.menu_edit), shortcutHint("↓", alt = true, command = false), enabled = has) {
        d?.let { p.revealEditor(); it.value = MoveLines.down(it.value) }
    }

    // Code
    action(a.foldBlock, text(Res.string.menu_code))
    action(a.unfoldBlock, text(Res.string.menu_code))
    action(a.foldAll, text(Res.string.menu_code))
    action(a.unfoldAll, text(Res.string.menu_code))

    action(a.reformat, text(Res.string.menu_code))

    // Code → Navigate
    action(a.goToDeclaration, text(Res.string.menu_code))
    action(a.findUsages, text(Res.string.menu_code))
    action(a.renameSymbol, text(Res.string.menu_code))
    action(a.navigateBack, text(Res.string.menu_code))
    action(a.navigateForward, text(Res.string.menu_code))

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
        action(a.compareWithHead, text(Res.string.menu_git))
        action(a.rollbackLines, text(Res.string.menu_git))
        action(a.showDiff, text(Res.string.menu_git))
        action(a.showHistory, text(Res.string.menu_git))
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
    cmd("app.settings", text(Res.string.menu_settings), "SproutStudio", shortcutHint(","), restoresFocus = false) { p.deps.windows.openSettings() }
    val editor = p.deps.editor
    cmd("view.fontBigger", text(Res.string.cmd_font_bigger), text(Res.string.menu_view), enabled = editor.currentFontSize < EditorSettings.MAX_FONT_SIZE) { editor.changeFontSize(1f) }
    cmd("view.fontSmaller", text(Res.string.cmd_font_smaller), text(Res.string.menu_view), enabled = editor.currentFontSize > EditorSettings.MIN_FONT_SIZE) { editor.changeFontSize(-1f) }
    cmd("view.fontReset", text(Res.string.cmd_font_reset), text(Res.string.menu_view), enabled = !editor.fontSize.isDefault) { editor.fontSize.reset() }
    cmd("view.softWrap", text(Res.string.menu_soft_wrap), text(Res.string.menu_view), shortcutHint("Z", alt = true, command = false)) { editor.toggleSoftWrap() }
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
