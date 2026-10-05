package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Commit
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PlantUmlLanguage
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.features.editor.CodeEditor
import es.hugoalvarezajenjo.sproutstudio.features.explorer.ProjectTree
import es.hugoalvarezajenjo.sproutstudio.features.git.CommitPanel
import es.hugoalvarezajenjo.sproutstudio.features.git.rememberLineChanges
import es.hugoalvarezajenjo.sproutstudio.features.palette.shortcutHint
import es.hugoalvarezajenjo.sproutstudio.features.preview.PreviewPane
import es.hugoalvarezajenjo.sproutstudio.features.preview.rememberPreviewViewModel
import es.hugoalvarezajenjo.sproutstudio.features.problems.ProblemsPanel
import es.hugoalvarezajenjo.sproutstudio.features.templates.TemplateSidebar
import es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.features.workspace.SidebarTool
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.EmptyState
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.HLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Splitter
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ToolButton
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.VLine
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide

@Composable
internal fun Workspace(p: WorkspaceViewModel, onSave: (Document) -> Unit, onCloseTab: (Document) -> Unit) {
    var caretLine by remember { mutableStateOf(1) }
    var caretCol by remember { mutableStateOf(1) }
    var status by remember { mutableStateOf<StatusInfo?>(null) }
    val c = ide

    // Git: find the repository of the folder, and re-read status on demand / after each save.
    LaunchedEffect(p.root) { p.git.openIn(p.root) }
    LaunchedEffect(p) { snapshotFlow { p.docs.map { it.savedText } }.collect { p.git.requestRefresh() } }

    Column(Modifier.fillMaxSize().background(c.panel)) {
        Row(Modifier.weight(1f)) {
            if (p.root != null) {
                ToolStripe(p)
                VLine()
                if (p.sidebarVisible) {
                    when (p.sidebarTool) {
                        SidebarTool.COMMIT -> CommitPanel(p)
                        SidebarTool.TEMPLATES -> TemplateSidebar(p.deps.thumbnails, onPick = { p.insertTemplate(it) })
                        SidebarTool.PROJECT -> ProjectTree(p)
                    }
                    SidebarSplitter(p)
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().background(c.editor)) {
                TabsBar(p, onCloseTab)
                val doc = p.active
                if (doc == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(Icons.Outlined.Schema, "No file open", "Pick a diagram on the left, or press ⌘N")
                    }
                } else {
                    key(doc) {
                        val preview = rememberPreviewViewModel(p.deps, doc.text, doc.dir)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            EditorAndPreview(p, doc, preview, onCaret = { l, col -> caretLine = l; caretCol = col })
                        }
                        HLine()
                        ProblemsPanel(
                            problems = preview.problems,
                            expanded = p.problemsExpanded,
                            multiBlock = preview.diagramCount > 1,
                            onToggle = { p.toggleProblems() },
                            onGoTo = { target ->
                                p.revealEditor()
                                if (target.diagramIndex != preview.index && target.diagramIndex < preview.diagramCount) {
                                    preview.showDiagram(target.diagramIndex)
                                }
                                target.line?.let { doc.jumpTo(it) }
                            },
                            // Expanded: capped height so the editor keeps most of the window. Collapsed: just the bar.
                            modifier = if (p.problemsExpanded) Modifier.height(200.dp) else Modifier,
                        )
                        val info = StatusInfo(
                            kind = PlantUmlLanguage.kindAt(doc.text, doc.value.selection.start).label,
                            diagrams = preview.diagramCount,
                            errorLine = preview.error?.let { it.line ?: 0 },
                            problems = preview.problems.size,
                        )
                        SideEffect { status = info }
                    }
                }
            }
        }
        HLine()
        StatusBar(p, caretLine, caretCol, status)
    }
}

/** What the active editor knows, for the status bar. */
internal data class StatusInfo(val kind: String, val diagrams: Int, val errorLine: Int?, val problems: Int)

@Composable
private fun EditorAndPreview(p: WorkspaceViewModel, doc: Document, preview: es.hugoalvarezajenjo.sproutstudio.features.preview.PreviewViewModel, onCaret: (Int, Int) -> Unit) {
    val c = ide
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val total = maxWidth
        val density = LocalDensity.current
        val split = p.layout == EditorLayout.SPLIT
        Row(Modifier.fillMaxSize()) {
            if (p.editorVisible) {
                CodeEditor(
                    doc,
                    // Red lines come from the Problems list (every block), not only the error of the
                    // diagram the preview happens to show; it's also computed before the image.
                    errorLine = null,
                    errorLines = preview.problems.mapNotNull { it.line }.toSet(),
                    lineChanges = rememberLineChanges(p.git, doc.file, doc.text),
                    modifier = Modifier.weight(if (split) 1f - p.previewFraction else 1f).fillMaxHeight(),
                    onCaretMoved = onCaret,
                )
            }
            if (split) {
                Splitter(onDrag = { deltaPx ->
                    val deltaFrac = with(density) { deltaPx.toDp() } / total
                    p.resizePreview(deltaFrac)
                })
            }
            if (p.previewVisible) {
                PreviewPane(
                    preview = preview,
                    baseDir = doc.dir,
                    baseName = doc.baseName,
                    modifier = Modifier.weight(if (split) p.previewFraction else 1f).fillMaxHeight(),
                    // An error link needs the code: from preview-only, open the editor beside it.
                    onJumpToLine = { p.revealEditor(); doc.jumpTo(it) },
                    requests = doc.previewCommandFlow,
                    leadingTools = {
                        Text("Preview", style = MaterialTheme.typography.labelLarge, color = c.text, modifier = Modifier.padding(start = 6.dp, end = 4.dp))
                    },
                )
            }
        }
    }
}

/**
 * JetBrains-style tool window stripe: always visible on the left, its button toggles the
 * Project panel, so a collapsed panel is one click (or ⌘1) away.
 */
@Composable
private fun ToolStripe(p: WorkspaceViewModel) {
    Column(
        Modifier.width(40.dp).fillMaxHeight().background(ide.panel).padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val project = p.sidebarVisible && p.sidebarTool == SidebarTool.PROJECT
        val commit = p.sidebarVisible && p.sidebarTool == SidebarTool.COMMIT
        ToolButton(
            Icons.Outlined.Folder,
            if (project) "Hide Project (${shortcutHint("1")})" else "Show Project (${shortcutHint("1")})",
            selected = project,
        ) { p.toggleTool(SidebarTool.PROJECT) }
        ToolButton(
            Icons.Outlined.Commit,
            if (commit) "Hide Commit (${shortcutHint("0")})" else "Commit (${shortcutHint("0")})",
            selected = commit,
        ) { p.toggleTool(SidebarTool.COMMIT) }
        val templates = p.sidebarVisible && p.sidebarTool == SidebarTool.TEMPLATES
        ToolButton(
            Icons.Outlined.Dashboard,
            if (templates) "Hide Templates" else "Templates",
            selected = templates,
        ) { p.toggleTool(SidebarTool.TEMPLATES) }
    }
}

/** Drag to resize the Project panel, double-click to collapse it. */
@Composable
private fun SidebarSplitter(p: WorkspaceViewModel) {
    val density = LocalDensity.current
    Splitter(
        onDrag = { deltaPx -> p.resizeSidebar(p.sidebarWidth + with(density) { deltaPx.toDp() }.value) },
        onDragStopped = { p.persistSidebarWidth() },
        onDoubleClick = { p.showSidebar(false) },
    )
}
