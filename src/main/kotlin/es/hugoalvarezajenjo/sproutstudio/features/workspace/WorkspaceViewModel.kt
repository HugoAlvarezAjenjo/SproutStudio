package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.EditOps
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PlantUmlFormatter
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Template
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import es.hugoalvarezajenjo.sproutstudio.features.document.Document
import es.hugoalvarezajenjo.sproutstudio.features.editor.FileJump
import es.hugoalvarezajenjo.sproutstudio.features.editor.IncludeFiles
import es.hugoalvarezajenjo.sproutstudio.features.editor.findUsages
import es.hugoalvarezajenjo.sproutstudio.features.editor.goToDeclaration
import es.hugoalvarezajenjo.sproutstudio.features.editor.navigateBack
import es.hugoalvarezajenjo.sproutstudio.features.editor.navigateForward
import es.hugoalvarezajenjo.sproutstudio.features.editor.reveal
import es.hugoalvarezajenjo.sproutstudio.features.editor.selectedText
import es.hugoalvarezajenjo.sproutstudio.features.explorer.ExplorerViewModel
import java.io.File
import es.hugoalvarezajenjo.sproutstudio.core.viewmodel.ViewModel
import es.hugoalvarezajenjo.sproutstudio.features.git.GitViewModel
import es.hugoalvarezajenjo.sproutstudio.features.search.SearchViewModel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
        private set

    /** Clicking a tab. Switching tabs also autosaves (see [startAutoSave]). */
    fun selectTab(i: Int) {
        if (i in docs.indices) activeIndex = i
    }
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
    private val commitFocus = Channel<Unit>(Channel.CONFLATED)
    val commitFocusRequests: Flow<Unit> = commitFocus.receiveAsFlow()

    fun focusCommit() {
        showTool(SidebarTool.COMMIT)
        commitFocus.trySend(Unit)
    }

    /** Git status of the project folder; opened by the window when [root] changes. */
    val git = GitViewModel(deps.git, deps.dispatchers, parent = scope.coroutineContext[Job])

    /** Find in Files over the project's diagrams, open tabs read as they are on screen. */
    val search = SearchViewModel(
        deps.files, deps.dispatchers,
        root = { root },
        openTexts = { docs.mapNotNull { d -> d.file?.let { it.absoluteFile to d.text } }.toMap() },
        parent = scope.coroutineContext[Job],
    )

    /** ⇧⌘F: the Find in Files panel, seeded with the active tab's selection. */
    fun findInFiles() {
        showTool(SidebarTool.SEARCH)
        search.open(active?.selectedText())
    }

    fun resizeSidebar(widthDp: Float) {
        sidebarWidth = widthDp.coerceIn(SIDEBAR_MIN, SIDEBAR_MAX)
    }

    /** Called when a drag ends, so we don't write preferences on every pixel. */
    fun persistSidebarWidth() {
        layoutSettings.sidebarWidth.value = sidebarWidth
    }
    /** Share of the split editor area the preview takes. */
    var previewFraction by mutableStateOf(0.5f)
        private set

    /** Dragging the editor/preview splitter by [delta] of the area's width. */
    fun resizePreview(delta: Float) {
        previewFraction = (previewFraction - delta).coerceIn(0.2f, 0.8f)
    }

    /** The project tree: its rows, open folders, pending dialog and file operations. */
    val explorer = ExplorerViewModel(this)

    /** Git operations that also touch the tabs and the tree. */
    val gitFlow = GitWorkflow(this)

    // ── Saving and closing ──────────────────────────────────────────────────────────────────

    private var autoSaveJob: Job? = null

    /**
     * Autosave for this window: 1 s after you stop typing, and whenever you switch tab. The
     * window starts it once it's on screen; it stops with this ViewModel.
     */
    fun startAutoSave() {
        if (autoSaveJob != null) return
        autoSaveJob = scope.launch {
            launch { deps.autoSaver.watch({ docs.toList() }) }
            snapshotFlow { activeIndex }.collect { deps.autoSaver.flush(docs.toList()) }
        }
    }

    /** Autosave now (the window lost focus, autosave was just switched on). */
    fun flushAutoSave(): Job = scope.launch { deps.autoSaver.flush(docs.toList()) }

    fun setAutoSave(on: Boolean) {
        deps.autoSaver.setEnabled(on)
        if (on) flushAutoSave()
    }

    /** Every edited file-backed tab, written now (before a commit or a checkout). */
    suspend fun saveDirty() {
        docs.filter { it.file != null && it.dirty }.forEach { it.save() }
    }

    /** Save; an untitled diagram asks where first. False if cancelled or the write failed. */
    suspend fun saveOrAsk(doc: Document): Boolean {
        if (doc.file != null) return doc.save()
        val target = chooseSaveTarget(doc.name, root) ?: return false
        return doc.save(target).also { explorer.refreshTreeNow() }
    }

    fun save(doc: Document?): Job? = doc?.let { d -> scope.launch { saveOrAsk(d) } }

    fun saveAs(doc: Document): Job? {
        val target = chooseSaveTarget(doc.name, doc.dir ?: root) ?: return null
        return scope.launch { doc.save(target); explorer.refreshTreeNow() }
    }

    /** The "unsaved changes" question the window is asking, if any. */
    var pendingClose by mutableStateOf<PendingClose?>(null)
        private set

    /**
     * Close [docs] (a tab, the project, the window), then run [then]. With autosave on only
     * untitled diagrams are still dirty; if any are, [pendingClose] asks first.
     */
    fun requestClose(docs: List<Document>, then: () -> Unit): Job = scope.launch {
        deps.autoSaver.flush(docs)
        val dirty = docs.filter { it.dirty }
        if (dirty.isEmpty()) then() else pendingClose = PendingClose(dirty, then)
    }

    fun requestCloseTab(doc: Document): Job = requestClose(listOf(doc)) { closeDoc(doc) }

    /** File → Close Project: back to the welcome screen, after the usual question. */
    fun requestCloseProject(): Job = requestClose(docs.toList()) {
        closeProject()
        git.openIn(null)
    }

    fun cancelClose() { pendingClose = null }

    fun discardAndClose() {
        val pc = pendingClose ?: return
        pendingClose = null
        pc.then()
    }

    fun saveAndClose(): Job? {
        val pc = pendingClose ?: return null
        pendingClose = null
        return scope.launch {
            pc.docs.forEach { saveOrAsk(it) }
            if (pc.docs.none { it.dirty }) pc.then()
        }
    }

    val active: Document? get() = docs.getOrNull(activeIndex)

    fun openRoot(dir: File) {
        root = dir
        search.clear()
        deps.recents.add(dir)
        explorer.onProjectOpened()
    }

    /**
     * Back to the welcome screen in this same window, dropping the tabs as they are. Use
     * [requestCloseProject], which asks about unsaved tabs and resets [git] too.
     */
    fun closeProject() {
        docs.clear()
        activeIndex = 0
        root = null
        explorer.clear()
        search.clear()
    }

    fun open(file: File) {
        val f = file.absoluteFile
        val i = docs.indexOfFirst { it.file?.absoluteFile == f }
        if (i >= 0) { activeIndex = i; return }
        docs += Document.open(f, deps.documents)
        activeIndex = docs.lastIndex
    }

    /** Open [jump]'s file and select its range, if it has one. */
    fun open(jump: FileJump) {
        open(jump.file)
        jump.range?.let { r -> active?.reveal(r) }
    }

    /** `!include`d files for navigation, read from the open tab when there is one. */
    val includes = IncludeFiles { f -> docs.firstOrNull { it.file?.absoluteFile?.normalize() == f }?.text }

    /** ⌥⌘L: reformat the active tab, or only its selected lines. */
    fun reformatCode() { active?.let { revealEditor(); it.value = PlantUmlFormatter.format(it.value) } }

    // ── Code → Navigate: on the active tab; ⌘B into an included file opens it in a tab ──

    fun goToDeclaration() { active?.let { revealEditor(); it.goToDeclaration(includes = includes)?.let { j -> open(j) } } }
    fun findUsages() { active?.let { revealEditor(); it.findUsages() } }
    fun navigateBack() { active?.let { revealEditor(); it.navigateBack() } }
    fun navigateForward() { active?.let { revealEditor(); it.navigateForward() } }

    fun newDocument(): Document {
        val dir = root
        val doc = if (dir != null) {
            var n = 1
            var f = File(dir, "new-diagram.puml")
            while (f.exists()) f = File(dir, "new-diagram-${++n}.puml")
            deps.documents.writeNow(f, "") // empty: content only comes from a template you pick
            explorer.refreshTree()
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
    fun insertTemplate(template: Template) {
        val doc = active ?: newDocument()
        revealEditor()
        doc.value = EditOps.insertTemplate(doc.value, template)
        doc.requestFocus()
    }

    fun closeDoc(doc: Document) {
        val i = docs.indexOf(doc)
        if (i < 0) return
        docs.removeAt(i)
        if (activeIndex >= docs.size) activeIndex = (docs.size - 1).coerceAtLeast(0)
        else if (i < activeIndex) activeIndex--
    }

    /** Closes the window's state: its git ViewModel and anything still running. */
    override fun close() {
        git.close()
        search.close()
        super.close()
    }

    companion object {
        const val SIDEBAR_MIN = LayoutSettings.SIDEBAR_MIN
        const val SIDEBAR_MAX = LayoutSettings.SIDEBAR_MAX
    }
}
