package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.EditOps
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PlantUmlFormatter
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.RenameProblem
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SymbolRename
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.SymbolRenaming
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
import kotlinx.coroutines.withContext
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

    /** What the menus, the palette and the editor's right-click menu can do; each action defined once. */
    val actions = WorkspaceActions(this)

    /** The tabs to reopen with this project next time (Settings → Restore session). */
    private val session = WorkspaceSession(this, deps.session)
    private var sessionJob: Job? = null

    /** Record the tabs as they change. The window starts it once it's on screen. */
    fun startSessionTracking() {
        if (sessionJob == null) sessionJob = scope.launch { session.track() }
    }

    /** Record the tabs now: ⌘Q quits without closing windows, and the tracker debounces. */
    fun saveSession() = session.save()

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

    fun requestCloseTab(doc: Document): Job = requestCloseTabs(listOf(doc))

    /** Close several tabs at once, asking a single question about the unsaved ones among them. */
    fun requestCloseTabs(tabs: List<Document>): Job = requestClose(tabs) { tabs.forEach(::closeDoc) }

    // ── Tab menu: [doc] is the right-clicked tab, made active first as in IntelliJ and VS Code ──

    fun closeOtherTabs(doc: Document): Job = closeAround(doc) { i, at -> i != at }
    fun closeTabsToTheLeft(doc: Document): Job = closeAround(doc) { i, at -> i < at }
    fun closeTabsToTheRight(doc: Document): Job = closeAround(doc) { i, at -> i > at }
    fun closeAllTabs(): Job = requestCloseTabs(docs.toList())

    /** Close the tabs at the indices [which] picks, given where [doc] is. */
    private fun closeAround(doc: Document, which: (i: Int, at: Int) -> Boolean): Job {
        val at = docs.indexOf(doc)
        if (at < 0) return Job().apply { complete() }
        selectTab(at)
        return requestCloseTabs(docs.filterIndexed { i, _ -> which(i, at) })
    }

    /** Dragging a tab from [from] to [to]; the active tab stays the active one. */
    fun moveTab(from: Int, to: Int) {
        if (from !in docs.indices || to !in docs.indices || from == to) return
        val current = active
        docs.add(to, docs.removeAt(from))
        activeIndex = docs.indexOf(current).coerceAtLeast(0)
    }

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
        if (docs.isEmpty()) session.reopen(dir)?.let { (tabs, active) -> docs += tabs; activeIndex = active }
    }

    /**
     * Back to the welcome screen in this same window, dropping the tabs as they are. Use
     * [requestCloseProject], which asks about unsaved tabs and resets [git] too.
     */
    fun closeProject() {
        session.save() // reopening this project later brings these tabs back
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

    // ── Code → Rename Symbol (⇧F6) ──

    /** The rename the dialog is asking a new name for; null when it's closed. */
    var renaming by mutableStateOf<SymbolRenaming?>(null)
        private set

    /** Working out the plan for the dialog, off the UI thread; null when idle. */
    private var planning: Job? = null

    /**
     * ⇧F6: plan renaming the symbol at the active tab's caret, then open the dialog. Nothing
     * opens off a symbol, or when another tab is active by the time the plan is ready.
     *
     * For a procedure or `$variable` the plan reads every diagram of the project, so that happens
     * off the UI thread, from the tabs' texts as they were at ⇧F6. The plan holds those texts:
     * an edit made meanwhile makes [rename] refuse it rather than apply it to a different text.
     */
    fun renameSymbol(): Job {
        planning?.cancel()
        val d = active ?: return Job().apply { complete() }
        revealEditor()
        d.nav.closeUsages()
        val text = d.text
        val caret = d.value.selection.start
        val file = d.file?.absoluteFile?.normalize()?.path
        val open = docs.mapNotNull { o -> o.file?.let { it.absoluteFile.normalize() to o.text } }.toMap()
        return scope.launch {
            val plan = withContext(deps.dispatchers.io) {
                val project = explorer.allDiagrams().map { it.absoluteFile.normalize().path }
                SymbolRename.at(text, caret, file, IncludeFiles { open[it] }, project)
            }
            if (active === d) renaming = plan
        }.also { planning = it }
    }

    fun dismissRename() { renaming = null }

    /**
     * Rename to [newName] in every file of the plan, or in none: returns why not. An included
     * file that isn't open gets a tab (in the background) so the change is visible, saved like
     * any edit, and undoable there with ⌘Z.
     */
    fun rename(newName: String): RenameProblem? {
        val plan = renaming ?: return null
        plan.problem(newName)?.let { return it }
        val current = active ?: return RenameProblem.STALE
        val targets = plan.edits.map { e -> e to (if (e.path == null) current else tabFor(File(e.path))) }
        if (targets.any { (e, doc) -> doc.text != e.text }) return RenameProblem.STALE
        for ((e, doc) in targets) doc.value = SymbolRename.apply(doc.value, e.ranges, newName)
        renaming = null
        return null
    }

    /** The tab showing [file], opened behind the active one when there's none yet. */
    private fun tabFor(file: File): Document {
        val f = file.absoluteFile.normalize()
        docs.firstOrNull { it.file?.absoluteFile?.normalize() == f }?.let { return it }
        return Document.open(f, deps.documents).also { docs += it }
    }

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
        session.save()
        git.close()
        search.close()
        super.close()
    }

    companion object {
        const val SIDEBAR_MIN = LayoutSettings.SIDEBAR_MIN
        const val SIDEBAR_MAX = LayoutSettings.SIDEBAR_MAX
    }
}
