package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Difference
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import es.hugoalvarezajenjo.sproutstudio.core.os.shortcutHint
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.EditOps
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Navigation
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.features.editor.changeAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.editor.foldAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.editor.rollbackAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.editor.selectedText
import es.hugoalvarezajenjo.sproutstudio.features.editor.unfoldAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.git.canCompareWithHead
import es.hugoalvarezajenjo.sproutstudio.features.document.PreviewAction
import es.hugoalvarezajenjo.sproutstudio.features.preview.label
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_copy_image
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_toggle_comment
import es.hugoalvarezajenjo.sproutstudio.resources.menu_compare_with_head
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_in_files
import es.hugoalvarezajenjo.sproutstudio.resources.menu_find_usages
import es.hugoalvarezajenjo.sproutstudio.resources.menu_fold_block
import es.hugoalvarezajenjo.sproutstudio.resources.menu_go_to_declaration
import es.hugoalvarezajenjo.sproutstudio.resources.menu_reformat_code
import es.hugoalvarezajenjo.sproutstudio.resources.menu_rename_symbol
import es.hugoalvarezajenjo.sproutstudio.resources.menu_replace
import es.hugoalvarezajenjo.sproutstudio.resources.menu_rollback_lines
import es.hugoalvarezajenjo.sproutstudio.resources.menu_show_history
import es.hugoalvarezajenjo.sproutstudio.resources.menu_unfold_block
import es.hugoalvarezajenjo.sproutstudio.resources.tree_export
import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.MenuItem
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.menu
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text

/**
 * What the workspace adds to the editor's right-click menu (the editor puts Cut/Copy/Paste
 * after it). Each entry runs the same action as the menu bar and the palette, with the same
 * shortcut shown. Entries that would do nothing here are left out: navigation off a symbol,
 * Rollback Lines outside a change, git outside a repository.
 */
internal class EditorMenu(private val p: WorkspaceViewModel) {

    fun items(d: Document): List<ContextMenuItem> = menu {
        val onSymbol = Navigation.occurrencesAt(d.text, d.value.selection.start).isNotEmpty()
        group {
            if (onSymbol) {
                add(MenuItem(text(Res.string.menu_go_to_declaration), shortcut = shortcutHint("B")) { p.goToDeclaration() })
                add(MenuItem(text(Res.string.menu_find_usages), Icons.Outlined.Search, shortcut = shortcutHint("F7", alt = true, command = false)) { p.findUsages() })
                add(MenuItem(text(Res.string.menu_rename_symbol), Icons.Outlined.DriveFileRenameOutline, shortcut = shortcutHint("F6", shift = true, command = false)) { p.renameSymbol() })
            }
        }
        group {
            add(MenuItem(text(Res.string.cmd_toggle_comment), shortcut = shortcutHint("/")) { d.value = EditOps.toggleComment(d.value) })
            add(MenuItem(text(Res.string.menu_reformat_code), Icons.AutoMirrored.Outlined.FormatAlignLeft, shortcut = shortcutHint("L", alt = true)) { p.reformatCode() })
            add(MenuItem(text(Res.string.menu_fold_block), Icons.Outlined.UnfoldLess, shortcut = shortcutHint("-")) { d.foldAtCaret() })
            add(MenuItem(text(Res.string.menu_unfold_block), Icons.Outlined.UnfoldMore, shortcut = shortcutHint("=")) { d.unfoldAtCaret() })
        }
        group {
            add(MenuItem(text(Res.string.menu_find), Icons.Outlined.Search, shortcut = shortcutHint("F")) { d.find.open(replace = false, seed = d.selectedText()) })
            add(MenuItem(text(Res.string.menu_replace), Icons.Outlined.FindReplace, shortcut = shortcutHint("R")) { d.find.open(replace = true, seed = d.selectedText()) })
            if (p.root != null) add(MenuItem(text(Res.string.menu_find_in_files), Icons.Outlined.TravelExplore, shortcut = shortcutHint("F", shift = true)) { p.findInFiles() })
        }
        group {
            if (d.changeAtCaret() != null) {
                add(MenuItem(text(Res.string.menu_rollback_lines), Icons.AutoMirrored.Outlined.Undo, shortcut = shortcutHint("Z", alt = true)) { d.rollbackAtCaret() })
            }
            val f = d.file
            if (f != null && p.git.status.head != null) {
                if (canCompareWithHead(p, f)) add(MenuItem(text(Res.string.menu_compare_with_head), Icons.Outlined.Difference) { p.deps.windows.compareWithHead(p, f) })
                add(MenuItem(text(Res.string.menu_show_history), Icons.Outlined.History) { p.deps.windows.showHistory(p, f) })
            }
        }
        group {
            add(MenuItem(text(Res.string.cmd_copy_image), Icons.Outlined.Image) { preview(d, PreviewAction.COPY_IMAGE) })
            add(MenuItem(text(Res.string.tree_export), Icons.Outlined.FileDownload, children = ExportFormat.entries.map { f ->
                MenuItem(f.label()) { preview(d, f.action) }
            }))
        }
    }

    /** Copy and export go through the preview: it knows which diagram it shows. */
    private fun preview(d: Document, a: PreviewAction) {
        if (!p.previewVisible) p.changeLayout(EditorLayout.SPLIT)
        d.requestPreview(a)
    }

    private val ExportFormat.action get() = when (this) {
        ExportFormat.SVG -> PreviewAction.EXPORT_SVG
        ExportFormat.PNG -> PreviewAction.EXPORT_PNG
        ExportFormat.PDF -> PreviewAction.EXPORT_PDF
    }
}
