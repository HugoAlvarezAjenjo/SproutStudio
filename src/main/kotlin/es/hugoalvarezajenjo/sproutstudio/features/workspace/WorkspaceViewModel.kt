package es.hugoalvarezajenjo.sproutstudio.features.workspace

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction
import es.hugoalvarezajenjo.sproutstudio.features.explorer.delete
import es.hugoalvarezajenjo.sproutstudio.features.explorer.rename
import java.io.File
import es.hugoalvarezajenjo.sproutstudio.core.viewmodel.ViewModel
import es.hugoalvarezajenjo.sproutstudio.features.git.GitViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One project window: its folder, open tabs, layout and tool panels, and the actions on them.
 * Every service it needs comes in [deps]; child ViewModels (git) share its lifetime.
 */
@Stable
class WorkspaceViewModel(val deps: WorkspaceDeps) : ViewModel(deps.dispatchers.main) {
    private val layoutSettings get() = deps.layout

    var root by mutableStateOf<File?>(null)
        private set
    val docs = mutableStateListOf<Document>()
    var activeIndex by mutableStateOf(0)
    /** Editor only / editor + preview / preview only, like IntelliJ's split-editor switcher. */
    var layout by mutableStateOf(deps.layout.editorLayout.value)
        private set

    val previewVisible: Boolean get() = layout != EditorLayout.EDITOR
    val editorVisible: Boolean get() = layout != EditorLayout.PREVIEW

    fun changeLayout(l: EditorLayout) {
        layout = l
        layoutSettings.editorLayout.value = l
    }

    /** ⌘P: show the preview next to the code, or hide it. */
    fun togglePreview() = changeLayout(if (layout == EditorLayout.EDITOR) EditorLayout.SPLIT else EditorLayout.EDITOR)

    /**
     * Something needs the code on screen (find, fold, jump to an error): from preview-only,
     * bring the editor back next to the preview rather than taking the preview away.
     */
    fun revealEditor() {
        if (layout == EditorLayout.PREVIEW) changeLayout(EditorLayout.SPLIT)
    }

    var sidebarVisible by mutableStateOf(deps.layout.sidebarVisible.value)
        private set
    /** Project panel width in dp; dragged by the user, remembered across launches. */
    var sidebarWidth by mutableStateOf(LayoutSettings.clampWidth(deps.layout.sidebarWidth.value))
        private set

    fun showSidebar(show: Boolean) {
        sidebarVisible = show
        layoutSettings.sidebarVisible.value = show
    }

    /** Bottom Problems panel: the 1-line header always shows; this is whether the list is open. */
    var problemsExpanded by mutableStateOf(deps.layout.problemsExpanded.value)
        private set

    fun toggleProblems() = showProblems(!problemsExpanded)

    fun showProblems(open: Boolean) {
        problemsExpanded = open
        layoutSettings.problemsExpanded.value = open
    }

    /** Which tool window the left panel shows: the file tree or the Commit window. */
    var sidebarTool by mutableStateOf(SidebarTool.PROJECT)
        private set

    /** Stripe button / ⌘1 / ⌘0: show [tool], or hide the panel if it's already showing it. */
    fun toggleTool(tool: SidebarTool) {
        if (sidebarVisible && sidebarTool == tool) showSidebar(false)
        else { sidebarTool = tool; showSidebar(true) }
    }

    /** Show [tool] (never hides), e.g. ⌘K always lands in the Commit window. */
    fun showTool(tool: SidebarTool) {
        sidebarTool = tool
        showSidebar(true)
    }

    /** ⌘K / "Commit…" asks the Commit panel to put the caret in the message. */
    private val commitFocus = kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    val commitFocusRequests: kotlinx.coroutines.flow.Flow<Unit> = commitFocus.receiveAsFlow()

    fun focusCommit() {
        showTool(SidebarTool.COMMIT)
        commitFocus.trySend(Unit)
    }

    /** Git status of the project folder; opened by the window when [root] changes. */
    val git = GitViewModel(deps.git, deps.dispatchers, parent = scope.coroutineContext[Job])

    fun resizeSidebar(widthDp: Float) {
        sidebarWidth = widthDp.coerceIn(SIDEBAR_MIN, SIDEBAR_MAX)
    }

    /** Called when a drag ends, so we don't write preferences on every pixel. */
    fun persistSidebarWidth() {
        layoutSettings.sidebarWidth.value = sidebarWidth
    }
    var previewFraction by mutableStateOf(0.5f)
    val expanded = mutableStateListOf<File>()
    /** The project folder's own row is collapsible too, like every folder under it. */
    var rootCollapsed by mutableStateOf(false)
    /** New / rename / delete dialog the project tree is showing, if any. */
    var fileAction by mutableStateOf<FileAction?>(null)
    var tree by mutableStateOf<List<es.hugoalvarezajenjo.sproutstudio.services.files.TreeNode>>(emptyList())
        private set

    val active: Document? get() = docs.getOrNull(activeIndex)

    fun openRoot(dir: File) {
        root = dir
        rootCollapsed = false
        expanded.clear()
        deps.recents.add(dir)
        refreshTree()
    }

    /**
     * File → Close Project: back to the welcome screen in this same window. Callers handle unsaved
     * tabs first (the window asks, like on quit) and reset [git] (a suspend call).
     */
    fun closeProject() {
        docs.clear()
        activeIndex = 0
        root = null
        expanded.clear()
        rootCollapsed = false
        fileAction = null
        tree = emptyList()
    }

    fun open(file: File) {
        val f = file.absoluteFile
        val i = docs.indexOfFirst { it.file?.absoluteFile == f }
        if (i >= 0) { activeIndex = i; return }
        docs += Document.open(f, deps.documents)
        activeIndex = docs.lastIndex
    }

    fun newDocument(): Document {
        val dir = root
        val doc = if (dir != null) {
            var n = 1
            var f = File(dir, "new-diagram.puml")
            while (f.exists()) f = File(dir, "new-diagram-${++n}.puml")
            f.writeText("") // empty: content only comes from a template you pick
            refreshTree()
            Document.open(f, deps.documents)
        } else Document(null, "", deps.documents)
        docs += doc
        activeIndex = docs.lastIndex
        return doc
    }

    /**
     * Insert a gallery/sidebar template. With no document open, a whole-diagram template opens a
     * fresh document holding just it; otherwise it goes into the active editor (whole diagram into
     * a blank doc = full content, else at the caret). Reveals the editor and focuses it.
     */
    fun insertTemplate(template: es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Template) {
        val doc = active ?: newDocument()
        revealEditor()
        doc.value = es.hugoalvarezajenjo.sproutstudio.domain.plantuml.EditOps.insertTemplate(doc.value, template)
        doc.requestFocus()
    }

    fun closeDoc(doc: Document) {
        val i = docs.indexOf(doc)
        if (i < 0) return
        docs.removeAt(i)
        if (activeIndex >= docs.size) activeIndex = (docs.size - 1).coerceAtLeast(0)
        else if (i < activeIndex) activeIndex--
    }

    fun toggleDir(dir: File) {
        if (dir in expanded) expanded.remove(dir) else expanded.add(dir)
        refreshTree()
    }

    fun refreshTree() {
        git.requestRefresh()
        val r = root ?: run { tree = emptyList(); return }
        tree = deps.files.tree(r, expanded)
    }

    /** Every diagram under the root, expanded or not (for the command palette's "go to file"). */
    fun allDiagrams(limit: Int = 2000): List<File> {
        val r = root ?: return emptyList()
        return deps.files.allDiagrams(r, limit)
    }

    /** Closes the window's state: its git ViewModel and anything still running. */
    override fun close() {
        git.close()
        super.close()
    }

    companion object {
        const val SIDEBAR_MIN = es.hugoalvarezajenjo.sproutstudio.features.workspace.LayoutSettings.SIDEBAR_MIN
        const val SIDEBAR_MAX = es.hugoalvarezajenjo.sproutstudio.features.workspace.LayoutSettings.SIDEBAR_MAX
    }
}
