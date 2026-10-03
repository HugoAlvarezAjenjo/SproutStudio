package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import es.hugoalvarezajenjo.sproutstudio.git.GitRepo
import es.hugoalvarezajenjo.sproutstudio.model.LayoutPrefs
import es.hugoalvarezajenjo.sproutstudio.model.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import es.hugoalvarezajenjo.sproutstudio.model.SidebarTool
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** Off-screen renders of the git UI on a temporary repo (Commit window, gutter, compare). */
class GitSnapshotTest {
    private val out = File("build/ui-snapshots").apply { mkdirs() }
    private val dir: File = Files.createTempDirectory("sprout-gitshot").toFile()
    private var sidebar = true
    private lateinit var layout: EditorLayout
    @BeforeTest fun save() { sidebar = LayoutPrefs.sidebarVisible; layout = LayoutPrefs.editorLayout }
    @AfterTest fun cleanup() { LayoutPrefs.sidebarVisible = sidebar; LayoutPrefs.editorLayout = layout; dir.deleteRecursively() }

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(1320, 840, Density(1f)) { PumlTheme(dark = true) { Surface(color = ide.panel) { content() } } }
        scene.render(0)
        var img = scene.render(0)
        for (t in listOf(400L, 800, 800, 800, 400)) { Thread.sleep(t); img = scene.render(System.nanoTime()) }
        val bytes = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(out, "$name.png").writeBytes(bytes)
        scene.close()
        assertTrue(bytes.size > 10_000)
    }

    private fun repo(): ProjectState {
        GitRepo.init(dir).close()
        File(dir, ".git/config").appendText("[user]\n\tname = Hugo\n\temail = hugo@example.com\n[commit]\n\tgpgsign = false\n")
        File(dir, ".gitignore").writeText("archive/\n")
        val order = File(dir, "order.puml").apply {
            writeText("@startuml\nactor Customer\nparticipant Shop\nparticipant Kitchen\n\nCustomer -> Shop: order pizza\nShop -> Kitchen: bake\nKitchen --> Shop: ready\nShop --> Customer: deliver\n@enduml\n")
        }
        val classes = File(dir, "model/classes.puml").apply { parentFile.mkdirs(); writeText("@startuml\nclass Pizza\nclass Topping\nPizza o-- Topping\n@enduml\n") }
        GitRepo.find(dir)!!.use { it.commit(listOf(order, classes, File(dir, ".gitignore")), "init") }
        // Now: edit a line, add two, delete one; a new file; an ignored folder.
        order.writeText("@startuml\nactor Customer\nparticipant Shop\nparticipant Kitchen\nparticipant Rider\n\nCustomer -> Shop: order pizza\nShop -> Kitchen: bake (12 min)\nKitchen --> Shop: ready\nShop -> Rider: pick up\nRider --> Customer: deliver\n@enduml\n")
        File(dir, "model/states.puml").writeText("@startuml\n[*] --> Ordered\nOrdered --> Baking\nBaking --> Delivered\n@enduml\n")
        File(dir, "archive").mkdirs(); File(dir, "archive/old.puml").writeText("@startuml\nA -> B\n@enduml\n")
        return ProjectState().apply {
            openRoot(dir)
            expanded.add(File(dir, "model")); expanded.add(File(dir, "archive")); refreshTree()
            open(order)
            changeLayout(EditorLayout.SPLIT)
        }
    }

    @Test fun commitWindowAndGutter() {
        val p = repo().apply { showTool(SidebarTool.COMMIT); git.commitMessage = "Add the rider to the delivery" }
        shoot("git-commit-window") { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test fun projectTreeColours() {
        val p = repo().apply { showTool(SidebarTool.PROJECT) }
        shoot("git-project-tree") { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test fun compareWithHead() {
        val p = repo()
        runBlocking { p.git.open(dir) }
        shoot("git-compare") { DiagramDiffView(p, File(dir, "order.puml")) }
    }
}
