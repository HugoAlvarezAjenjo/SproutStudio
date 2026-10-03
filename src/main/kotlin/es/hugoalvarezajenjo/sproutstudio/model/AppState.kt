package es.hugoalvarezajenjo.sproutstudio.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.prefs.Preferences

/** A window the app is showing. */
sealed class AppWindow {
    val id: Long = nextId++

    class QuickPreview(val file: File) : AppWindow()
    /** [file] at [left] next to [right] (by default: last commit vs now), from [project]'s repository. */
    class DiagramDiff(
        val project: ProjectState,
        val file: File,
        val left: es.hugoalvarezajenjo.sproutstudio.git.DiffSide = es.hugoalvarezajenjo.sproutstudio.git.DiffSide.Head,
        val right: es.hugoalvarezajenjo.sproutstudio.git.DiffSide = es.hugoalvarezajenjo.sproutstudio.git.DiffSide.WorkingCopy,
        val textFirst: Boolean = false,
    ) : AppWindow()
    /** Commits that touched [file], each viewable as a diagram or text diff. */
    class History(val project: ProjectState, val file: File) : AppWindow()
    class Project(val state: ProjectState, revealed: Boolean = true) : AppWindow() {
        /** False while composed-but-hidden (the boot welcome waiting to see if a file arrives). */
        var revealed by mutableStateOf(revealed)
    }

    private companion object { var nextId = 1L }
}

object AppState {
    val windows = mutableStateListOf<AppWindow>()

    /**
     * Welcome window for a plain launch. Compose quits if its first composition has no window,
     * so it is composed at once but kept HIDDEN: a Finder double-click on a cold start reaches us
     * as an Apple "open file" event that can arrive seconds after main(). If it does, the hidden
     * welcome is discarded and only the preview ever appears; otherwise [revealBootWelcome] shows it.
     */
    private var bootWelcome: AppWindow.Project? = null

    fun openBootWelcome() {
        val w = AppWindow.Project(ProjectState(), revealed = false)
        bootWelcome = w
        windows += w
    }

    /** Called once the app has settled with no file to open: show the welcome after all. */
    fun revealBootWelcome() {
        bootWelcome?.revealed = true
    }

    /** A file arrived: the boot welcome isn't wanted, unless the user already started using it. */
    private fun dropBootWelcome() {
        val w = bootWelcome ?: return
        bootWelcome = null
        val untouched = w.state.root == null && w.state.docs.isEmpty()
        if (untouched) windows.remove(w)
    }

    /** Double-click / single file: open the lightweight preview, or focus an open tab. */
    fun openFile(file: File) {
        val f = file.absoluteFile
        when {
            f.isDirectory -> openFolder(f)
            windows.any { it is AppWindow.QuickPreview && it.file == f } -> Unit
            else -> { windows += AppWindow.QuickPreview(f); dropBootWelcome() }
        }
    }

    fun openFolder(dir: File) {
        val d = dir.absoluteFile
        windows.filterIsInstance<AppWindow.Project>().firstOrNull { it.state.root == d }?.let { return }
        // Reuse an empty welcome window if there is one.
        val empty = windows.filterIsInstance<AppWindow.Project>().firstOrNull { it.state.root == null && it.state.docs.isEmpty() }
        if (empty != null) {
            empty.state.openRoot(d)
            empty.revealed = true
        } else {
            windows += AppWindow.Project(ProjectState().apply { openRoot(d) })
        }
    }

    fun newProjectWindow() {
        windows += AppWindow.Project(ProjectState())
    }

    /** "Edit" from Quick Preview: open the file's folder as a project with the file in a tab. */
    fun editFile(file: File, from: AppWindow?) {
        val dir = file.absoluteFile.parentFile
        val existing = windows.filterIsInstance<AppWindow.Project>().firstOrNull { it.state.root == dir }
        val target = existing?.state ?: ProjectState().apply { openRoot(dir) }.also { windows += AppWindow.Project(it) }
        target.open(file)
        if (from != null) windows.remove(from)
    }

    /** "Compare Diagram with HEAD": one window per file, reused if already open. */
    fun compareWithHead(project: ProjectState, file: File, textFirst: Boolean = false) {
        val f = file.absoluteFile
        if (windows.any { it is AppWindow.DiagramDiff && it.file == f && it.left == es.hugoalvarezajenjo.sproutstudio.git.DiffSide.Head }) return
        windows += AppWindow.DiagramDiff(project, f, textFirst = textFirst)
    }

    fun showHistory(project: ProjectState, file: File) {
        val f = file.absoluteFile
        if (windows.any { it is AppWindow.History && it.file == f }) return
        windows += AppWindow.History(project, f)
    }

    fun close(w: AppWindow) {
        windows.remove(w)
    }
}

/** Persisted list of recently opened folders. */
object Recents {
    private val prefs = Preferences.userRoot().node("es/hugoalvarezajenjo/sproutstudio")
    private const val KEY = "recentFolders"

    fun folders(): List<File> =
        prefs.get(KEY, "").split('\n').filter { it.isNotBlank() }.map(::File).filter { it.isDirectory }

    fun add(dir: File) {
        val list = (listOf(dir.absoluteFile) + folders()).distinct().take(8)
        prefs.put(KEY, list.joinToString("\n") { it.absolutePath })
    }
}

data class TreeNode(val file: File, val depth: Int, val isDir: Boolean)

@Stable
class ProjectState {
    var root by mutableStateOf<File?>(null)
        private set
    val docs = mutableStateListOf<Document>()
    var activeIndex by mutableStateOf(0)
    /** Editor only / editor + preview / preview only, like IntelliJ's split-editor switcher. */
    var layout by mutableStateOf(LayoutPrefs.editorLayout)
        private set

    val previewVisible: Boolean get() = layout != EditorLayout.EDITOR
    val editorVisible: Boolean get() = layout != EditorLayout.PREVIEW

    fun changeLayout(l: EditorLayout) {
        layout = l
        LayoutPrefs.editorLayout = l
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

    var sidebarVisible by mutableStateOf(LayoutPrefs.sidebarVisible)
        private set
    /** Project panel width in dp; dragged by the user, remembered across launches. */
    var sidebarWidth by mutableStateOf(LayoutPrefs.sidebarWidth)
        private set

    fun showSidebar(show: Boolean) {
        sidebarVisible = show
        LayoutPrefs.sidebarVisible = show
    }

    /** Bottom Problems panel: the 1-line header always shows; this is whether the list is open. */
    var problemsExpanded by mutableStateOf(LayoutPrefs.problemsExpanded)
        private set

    fun toggleProblems() = showProblems(!problemsExpanded)

    fun showProblems(open: Boolean) {
        problemsExpanded = open
        LayoutPrefs.problemsExpanded = open
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

    /** Bumped by ⌘K / "Commit…" to put the caret in the commit message. */
    var commitFocusTick by mutableStateOf(0)
        private set

    fun focusCommit() {
        showTool(SidebarTool.COMMIT)
        commitFocusTick++
    }

    /** Git status of the project folder; opened by the window when [root] changes. */
    val git = es.hugoalvarezajenjo.sproutstudio.git.GitState()

    fun resizeSidebar(widthDp: Float) {
        sidebarWidth = widthDp.coerceIn(SIDEBAR_MIN, SIDEBAR_MAX)
    }

    /** Called when a drag ends, so we don't write preferences on every pixel. */
    fun persistSidebarWidth() {
        LayoutPrefs.sidebarWidth = sidebarWidth
    }
    var previewFraction by mutableStateOf(0.5f)
    val expanded = mutableStateListOf<File>()
    var tree by mutableStateOf<List<TreeNode>>(emptyList())
        private set

    val active: Document? get() = docs.getOrNull(activeIndex)

    fun openRoot(dir: File) {
        root = dir
        expanded.clear()
        Recents.add(dir)
        refreshTree()
    }

    fun open(file: File) {
        val f = file.absoluteFile
        val i = docs.indexOfFirst { it.file?.absoluteFile == f }
        if (i >= 0) { activeIndex = i; return }
        docs += Document.open(f)
        activeIndex = docs.lastIndex
    }

    fun newDocument(): Document {
        val dir = root
        val doc = if (dir != null) {
            var n = 1
            var f = File(dir, "new-diagram.puml")
            while (f.exists()) f = File(dir, "new-diagram-${++n}.puml")
            f.writeText(Document.NEW_TEMPLATE)
            refreshTree()
            Document.open(f)
        } else Document(null, Document.NEW_TEMPLATE)
        docs += doc
        activeIndex = docs.lastIndex
        return doc
    }

    /**
     * Insert a gallery/sidebar template. With no document open, a whole-diagram template opens a
     * fresh document holding just it; otherwise it goes into the active editor (whole diagram into
     * a blank doc = full content, else at the caret). Reveals the editor and focuses it.
     */
    fun insertTemplate(template: es.hugoalvarezajenjo.sproutstudio.lang.Template) {
        val doc = active ?: if (template.whole) {
            newDocument().also { it.value = androidx.compose.ui.text.input.TextFieldValue("") }
        } else newDocument()
        revealEditor()
        doc.value = es.hugoalvarezajenjo.sproutstudio.editor.EditOps.insertTemplate(doc.value, template)
        doc.focusTick++
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
        val out = ArrayList<TreeNode>()
        fun walk(dir: File, depth: Int) {
            val children = dir.listFiles().orEmpty()
                .filter { !it.name.startsWith(".") && it.name !in IGNORED }
                .filter { it.isDirectory && containsDiagrams(it) || it.isDiagram() }
                .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            for (c in children) {
                out += TreeNode(c, depth, c.isDirectory)
                if (c.isDirectory && c in expanded) walk(c, depth + 1)
            }
        }
        walk(r, 0)
        tree = out
    }

    /** Every diagram under the root, expanded or not (for the command palette's "go to file"). */
    fun allDiagrams(limit: Int = 2000): List<File> {
        val r = root ?: return emptyList()
        val out = ArrayList<File>()
        fun walk(dir: File, depth: Int) {
            if (depth > 8 || out.size >= limit) return
            for (f in dir.listFiles().orEmpty().sortedBy { it.name.lowercase() }) {
                if (f.name.startsWith(".") || f.name in IGNORED) continue
                if (f.isDirectory) walk(f, depth + 1) else if (f.isDiagram() && out.size < limit) out += f.absoluteFile
            }
        }
        walk(r, 0)
        return out
    }

    private fun containsDiagrams(dir: File, depth: Int = 0): Boolean {
        if (depth > 6) return false
        return dir.listFiles().orEmpty().any { f ->
            !f.name.startsWith(".") && f.name !in IGNORED && (f.isDiagram() || (f.isDirectory && containsDiagrams(f, depth + 1)))
        }
    }

    companion object {
        const val SIDEBAR_MIN = 160f
        const val SIDEBAR_MAX = 560f
        private val IGNORED = setOf("node_modules", "build", "target", "out", "dist", ".git", ".gradle")
    }
}

/** Window layout the user tuned, persisted. */
object LayoutPrefs {
    private val prefs = Preferences.userRoot().node("es/hugoalvarezajenjo/sproutstudio")

    var sidebarVisible: Boolean
        get() = prefs.getBoolean("sidebarVisible", true)
        set(v) = prefs.putBoolean("sidebarVisible", v)

    var sidebarWidth: Float
        get() = prefs.getFloat("sidebarWidth", 240f).coerceIn(ProjectState.SIDEBAR_MIN, ProjectState.SIDEBAR_MAX)
        set(v) = prefs.putFloat("sidebarWidth", v)

    var editorLayout: EditorLayout
        get() = runCatching { EditorLayout.valueOf(prefs.get("editorLayout", EditorLayout.SPLIT.name)) }.getOrDefault(EditorLayout.SPLIT)
        set(v) = prefs.put("editorLayout", v.name)

    /** Whether the Problems panel at the bottom is expanded. The header bar always shows. */
    var problemsExpanded: Boolean
        get() = prefs.getBoolean("problemsExpanded", false)
        set(v) = prefs.putBoolean("problemsExpanded", v)
}

enum class SidebarTool { PROJECT, COMMIT, TEMPLATES }

/** How the active tab is shown. */
enum class EditorLayout(val label: String) {
    EDITOR("Editor Only"),
    SPLIT("Editor and Preview"),
    PREVIEW("Preview Only"),
}
