package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Navigation
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.features.document.PreviewAction
import es.hugoalvarezajenjo.sproutstudio.features.editor.changeAtCaret
import es.hugoalvarezajenjo.sproutstudio.features.preview.label
import es.hugoalvarezajenjo.sproutstudio.resources.Res
import es.hugoalvarezajenjo.sproutstudio.resources.cmd_copy_image
import es.hugoalvarezajenjo.sproutstudio.resources.tree_export
import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Action
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.MenuItem
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.menu
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.toMenuItem

/**
 * What the workspace adds to the editor's right-click menu (the editor puts Cut/Copy/Paste
 * after it). Entries are [WorkspaceActions], the same ones the menu bar and the palette show. Entries that would do nothing here are left out: navigation off a symbol,
 * Rollback Lines outside a change, git outside a repository.
 */
internal class EditorMenu(private val p: WorkspaceViewModel) {

    fun items(d: Document): List<ContextMenuItem> = menu {
        val a = p.actions
        val onSymbol = Navigation.occurrencesAt(d.text, d.value.selection.start).isNotEmpty()
        group { if (onSymbol) add(a.goToDeclaration, a.findUsages, a.renameSymbol) }
        group { add(a.toggleComment, a.reformat, a.foldBlock, a.unfoldBlock) }
        group { add(a.find, a.replace); if (p.root != null) add(a.findInFiles) }
        group {
            if (d.changeAtCaret() != null) add(a.rollbackLines)
            if (d.file != null && p.git.status.head != null) {
                if (a.compareWithHead.enabled) add(a.compareWithHead)
                add(a.showHistory)
            }
        }
        group {
            add(MenuItem(text(Res.string.cmd_copy_image), Icons.Outlined.Image) { preview(d, PreviewAction.COPY_IMAGE) })
            add(MenuItem(text(Res.string.tree_export), Icons.Outlined.FileDownload, children = ExportFormat.entries.map { f ->
                MenuItem(f.label()) { preview(d, f.action) }
            }))
        }
    }

    private fun MutableList<ContextMenuItem>.add(vararg actions: Action) = actions.forEach { add(it.toMenuItem()) }

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
