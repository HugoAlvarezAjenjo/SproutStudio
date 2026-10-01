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
    class Project(val state: ProjectState) : AppWindow()

    private companion object { var nextId = 1L }
}

object AppState {
    val windows = mutableStateListOf<AppWindow>()

    /** Welcome window opened on a plain launch, replaceable by an early open-file event. */
    private var bootWelcome: AppWindow.Project? = null
    private var bootAt = 0L

    fun openBootWelcome() {
        val w = AppWindow.Project(ProjectState())
        bootWelcome = w
        bootAt = System.currentTimeMillis()
        windows += w
    }

    /** Drop the boot welcome window if the user hasn't touched it and it's only just appeared. */
    private fun dropBootWelcome() {
        val w = bootWelcome ?: return
        bootWelcome = null
        val untouched = w.state.root == null && w.state.docs.isEmpty()
        if (untouched && System.currentTimeMillis() - bootAt < 3000) windows.remove(w)
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
        if (empty != null) empty.state.openRoot(d) else windows += AppWindow.Project(ProjectState().apply { openRoot(d) })
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
    var previewVisible by mutableStateOf(true)
    var sidebarVisible by mutableStateOf(true)
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

    private fun containsDiagrams(dir: File, depth: Int = 0): Boolean {
        if (depth > 6) return false
        return dir.listFiles().orEmpty().any { f ->
            !f.name.startsWith(".") && f.name !in IGNORED && (f.isDiagram() || (f.isDirectory && containsDiagrams(f, depth + 1)))
        }
    }

    companion object {
        private val IGNORED = setOf("node_modules", "build", "target", "out", "dist", ".git", ".gradle")
    }
}
