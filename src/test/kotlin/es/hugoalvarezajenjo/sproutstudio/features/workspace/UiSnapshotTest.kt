package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.Offscreen
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schema
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.MenuItem
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.menu
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.TestGraph

import androidx.compose.material3.Surface
import es.hugoalvarezajenjo.sproutstudio.features.palette.CommandPalette
import es.hugoalvarezajenjo.sproutstudio.features.palette.projectCommands
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.features.workspace.Welcome
import es.hugoalvarezajenjo.sproutstudio.features.workspace.Workspace
import es.hugoalvarezajenjo.sproutstudio.core.i18n.Language
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ProvideLanguage
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/** Renders the real screens off-screen so the UI can be checked without a display. */
class UiSnapshotTest {

    private val out = File("build/ui-snapshots").apply { mkdirs() }

    private fun shoot(
        name: String, frames: List<Long>, dark: Boolean = true, language: Language = Language.English,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        val bytes = Offscreen.render(1320, 840, frames, {
            ProvideLanguage(language) { PumlTheme(dark = dark) { Surface(color = ide.panel) { content() } } }
        }) { it.encodeToData(EncodedImageFormat.PNG)!!.bytes }
        File(out, "$name.png").writeBytes(bytes)
        Language.English.makeDefault() // the other tests read English
        assertTrue(bytes.size > 10_000)
    }

    private fun pizzaProject() = TestGraph.workspace().apply {
        openRoot(File("samples").absoluteFile)
        open(File("samples/pizza.puml").absoluteFile)
        open(File("samples/morning.puml").absoluteFile)
        selectTab(0)
    }

    @Test
    fun welcome() = shoot("welcome", listOf(100)) { Welcome(TestGraph.workspace()) }

    @Test
    fun workspace() {
        val p = pizzaProject()
        shoot("workspace", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test
    fun workspacePreviewOnly() {
        val saved = TestGraph.graph.layout.editorLayout.value
        try {
            val p = pizzaProject().apply { changeLayout(es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout.PREVIEW) }
            shoot("workspace-preview-only", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
        } finally {
            TestGraph.graph.layout.editorLayout.value = saved
        }
    }

    @Test
    fun workspaceWideSidebar() {
        val p = pizzaProject().apply { resizeSidebar(380f) }
        shoot("workspace-wide-sidebar", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test
    fun workspaceDarkDiagram() {
        val wasDark = TestGraph.graph.diagram.dark.value
        val savedLayout = TestGraph.graph.layout.editorLayout.value
        if (!wasDark) TestGraph.graph.diagram.toggle()
        try {
            val p = pizzaProject().apply { changeLayout(es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout.SPLIT) }
            shoot("workspace-dark-diagram", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
        } finally {
            if (TestGraph.graph.diagram.dark.value != wasDark) TestGraph.graph.diagram.toggle() // leave the user's choice as it was
            TestGraph.graph.layout.editorLayout.value = savedLayout
        }
    }

    @Test
    fun editorSoftWrap() {
        // A long line with the caret on it and an error below: number, current-line band and
        // error band must cover every row of the wrapped line, and the number shows only once.
        val long = "Alice -> Bob: " + "a rather long message that keeps going ".repeat(4)
        val text = "@startuml\n$long\nBob --> Alice: ok\nAlice -> : $long\n@enduml\n"
        val doc = TestGraph.document(null, text).apply {
            value = value.copy(selection = androidx.compose.ui.text.TextRange("@startuml\n".length + 20))
        }
        shoot("editor-soft-wrap", listOf(200, 400)) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(560.dp, 360.dp)) {
                es.hugoalvarezajenjo.sproutstudio.features.editor.CodeEditor(doc, errorLine = null, errorLines = setOf(4), softWrap = true)
            }
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
        d.find.query = es.hugoalvarezajenjo.sproutstudio.domain.text.FindQuery("pizza")
        d.find.replacement = "pie"
        shoot("workspace-find", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    private fun withPalette(name: String, query: String, language: Language = Language.English) {
        val p = pizzaProject()
        shoot(name, listOf(400, 800, 800, 400), language = language) {
            androidx.compose.foundation.layout.Box {
                Workspace(p, onSave = {}, onCloseTab = {})
                CommandPalette(projectCommands(p, {}, {}, {}), onDismiss = {}, onRun = {}, recentIds = listOf("view.theme", "preview.png"), initialQuery = query)
            }
        }
    }

    @Test fun commandPalette() = withPalette("command-palette", "")

    @Test fun welcomeSpanish() = shoot("welcome-es", listOf(100), language = Language.Spanish) { Welcome(TestGraph.workspace()) }

    @Test
    fun workspaceSpanish() {
        val p = pizzaProject()
        shoot("workspace-es", listOf(400, 800, 800, 400), language = Language.Spanish) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test fun commandPaletteSpanish() = withPalette("command-palette-es", "", Language.Spanish)

    @Test fun commandPaletteSearch() = withPalette("command-palette-search", "exp")

    @Test fun commandPaletteFile() = withPalette("command-palette-file", "morn")

    @Test
    fun workspaceFolded() {
        val p = pizzaProject()
        val d = p.active!!
        val rs = es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Folding.regions(d.text)
        d.folds.set(listOf(rs.first(), rs.first { it.kind == es.hugoalvarezajenjo.sproutstudio.domain.plantuml.FoldRegion.Kind.BRACES }))
        shoot("workspace-folded", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test
    fun workspaceWithError() {
        val p = TestGraph.workspace().apply {
            openRoot(File("samples").absoluteFile)
            open(File("samples/morning.puml").absoluteFile)
            val d = active!!
            d.value = d.value.copy(text = d.text.replace(":Go play;", ":Go play;\nthis is broken ((("))
        }
        shoot("workspace-error", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test
    fun workspaceProblemsPanel() {
        val saved = TestGraph.graph.layout.problemsExpanded.value
        val p = TestGraph.workspace().apply {
            openRoot(File("samples").absoluteFile)
            open(File("samples/morning.puml").absoluteFile)
            val d = active!!
            d.value = d.value.copy(text = d.text.replace(":Go play;", ":Go play;\nthis is broken ((("))
            showProblems(true)
        }
        try {
            shoot("workspace-problems-panel", listOf(400, 800, 800, 400)) { Workspace(p, onSave = {}, onCloseTab = {}) }
        } finally {
            TestGraph.graph.layout.problemsExpanded.value = saved
        }
    }

    /** The themed right-click menu, opened from the start, over the workspace. */
    private fun contextMenu(name: String, dark: Boolean) {
        val p = pizzaProject()
        val state = ContextMenuState().apply { status = ContextMenuState.Status.Open(Rect(120f, 160f, 120f, 160f)) }
        val items = menu {
            group { add(MenuItem("Open", Icons.Outlined.Schema)) }
            group {
                add(MenuItem("Copy Path"))
                add(MenuItem("Export", children = listOf(MenuItem("PNG"))))
                add(MenuItem("Rollback…", isEnabled = false))
            }
            group {
                add(MenuItem("Refresh Files", shortcut = "⌥⌘Y"))
                add(MenuItem("Move to Trash…", Icons.Outlined.Delete, danger = true))
            }
        }
        shoot(name, listOf(400, 800, 400), dark = dark) {
            Box {
                Workspace(p, onSave = {}, onCloseTab = {})
                ContextMenuArea(items = { items }, state = state) { Box(Modifier.size(1.dp)) }
            }
        }
    }

    @Test fun contextMenuDark() = contextMenu("context-menu-dark", dark = true)

    @Test fun contextMenuLight() = contextMenu("context-menu-light", dark = false)
}
