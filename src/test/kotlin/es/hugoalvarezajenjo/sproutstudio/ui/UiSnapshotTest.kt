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
    fun workspaceLight() {
        val p = pizzaProject()
        shoot("workspace-light", listOf(400, 800, 800, 400), dark = false) { Workspace(p, onSave = {}, onCloseTab = {}) }
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
