package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Renders the real screens off-screen so the UI can be checked without a display. */
class UiSnapshotTest {

    private val out = File("build/ui-snapshots").apply { mkdirs() }

    private fun shoot(name: String, frames: List<Long>, dark: Boolean = true, content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(1320, 840, Density(1f)) {
            PumlTheme(dark = dark) { Surface(color = ide.panel) { content() } }
        }
        var img = scene.render(0)
        // Let async work (renders, debounces) settle across a few frames of real time.
        for (t in frames) { Thread.sleep(t); img = scene.render(System.nanoTime()) }
        val bytes = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(out, "$name.png").writeBytes(bytes)
        scene.close()
        assertTrue(bytes.size > 10_000)
    }

    private fun pizzaProject() = ProjectState().apply {
        openRoot(File("samples").absoluteFile)
        open(File("samples/pizza.puml").absoluteFile)
        open(File("samples/morning.puml").absoluteFile)
        activeIndex = 0
    }

    @Test
    fun welcome() = shoot("welcome", listOf(100)) { Welcome(ProjectState()) }

    @Test
    fun workspace() {
        val p = pizzaProject()
        shoot("workspace", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test
    fun workspacePreviewOnly() {
        val saved = es.hugoalvarezajenjo.sproutstudio.model.LayoutPrefs.editorLayout
        try {
            val p = pizzaProject().apply { changeLayout(es.hugoalvarezajenjo.sproutstudio.model.EditorLayout.PREVIEW) }
            shoot("workspace-preview-only", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
        } finally {
            es.hugoalvarezajenjo.sproutstudio.model.LayoutPrefs.editorLayout = saved
        }
    }

    @Test
    fun workspaceWideSidebar() {
        val p = pizzaProject().apply { resizeSidebar(380f) }
        shoot("workspace-wide-sidebar", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test
    fun workspaceDarkDiagram() {
        val wasDark = DiagramPrefs.dark
        val savedLayout = es.hugoalvarezajenjo.sproutstudio.model.LayoutPrefs.editorLayout
        if (!wasDark) DiagramPrefs.toggle()
        try {
            val p = pizzaProject().apply { changeLayout(es.hugoalvarezajenjo.sproutstudio.model.EditorLayout.SPLIT) }
            shoot("workspace-dark-diagram", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
        } finally {
            if (DiagramPrefs.dark != wasDark) DiagramPrefs.toggle() // leave the user's choice as it was
            es.hugoalvarezajenjo.sproutstudio.model.LayoutPrefs.editorLayout = savedLayout
        }
    }

    @Test
    fun workspaceLight() {
        val p = pizzaProject()
        shoot("workspace-light", listOf(400, 800, 800, 400), dark = false) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test
    fun workspaceFindReplace() {
        val p = pizzaProject()
        val d = p.active!!
        d.find.open(replace = true, seed = null)
        d.find.query = es.hugoalvarezajenjo.sproutstudio.editor.FindQuery("pizza")
        d.find.replacement = "pie"
        shoot("workspace-find", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    private fun withPalette(name: String, query: String) {
        val p = pizzaProject()
        shoot(name, listOf(400, 800, 800, 400)) {
            androidx.compose.foundation.layout.Box {
                Workspace(p, onSave = {}, onCloseTab = {})
                CommandPalette(projectCommands(p, {}, {}, {}), onDismiss = {}, onRun = {}, recentIds = listOf("view.theme", "preview.png"), initialQuery = query)
            }
        }
    }

    @Test fun commandPalette() = withPalette("command-palette", "")

    @Test fun commandPaletteSearch() = withPalette("command-palette-search", "exp")

    @Test fun commandPaletteFile() = withPalette("command-palette-file", "morn")

    @Test
    fun workspaceFolded() {
        val p = pizzaProject()
        val d = p.active!!
        val rs = es.hugoalvarezajenjo.sproutstudio.editor.Folding.regions(d.text)
        d.folds.set(listOf(rs.first(), rs.first { it.kind == es.hugoalvarezajenjo.sproutstudio.editor.FoldRegion.Kind.BRACES }))
        shoot("workspace-folded", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test
    fun workspaceWithError() {
        val p = ProjectState().apply {
            openRoot(File("samples").absoluteFile)
            open(File("samples/morning.puml").absoluteFile)
            val d = active!!
            d.value = d.value.copy(text = d.text.replace(":Go play;", ":Go play;\nthis is broken ((("))
        }
        shoot("workspace-error", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }
}
