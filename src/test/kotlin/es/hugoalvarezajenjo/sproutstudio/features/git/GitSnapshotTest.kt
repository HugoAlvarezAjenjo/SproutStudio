package es.hugoalvarezajenjo.sproutstudio.features.git

import es.hugoalvarezajenjo.sproutstudio.TestGraph

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.features.git.BranchButton
import es.hugoalvarezajenjo.sproutstudio.features.git.DiagramDiffView
import es.hugoalvarezajenjo.sproutstudio.features.git.DiffView
import es.hugoalvarezajenjo.sproutstudio.features.git.HistoryView
import es.hugoalvarezajenjo.sproutstudio.features.workspace.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.features.workspace.SidebarTool
import es.hugoalvarezajenjo.sproutstudio.features.workspace.Workspace
import es.hugoalvarezajenjo.sproutstudio.services.git.JGitService
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat

/** Off-screen renders of the git UI on a temporary repo (Commit window, gutter, compare). */
class GitSnapshotTest {
    private val out = File("build/ui-snapshots").apply { mkdirs() }
    private val dir: File = Files.createTempDirectory("sprout-gitshot").toFile()
    private var sidebar = true
    private lateinit var layout: EditorLayout
    @BeforeTest fun save() { sidebar = TestGraph.graph.layout.sidebarVisible.value; layout = TestGraph.graph.layout.editorLayout.value }
    @AfterTest fun cleanup() { TestGraph.graph.layout.sidebarVisible.value = sidebar; TestGraph.graph.layout.editorLayout.value = layout; dir.deleteRecursively() }

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

    private fun repo(): WorkspaceViewModel {
        JGitService().init(dir).close()
        File(dir, ".git/config").appendText("[user]\n\tname = Hugo\n\temail = hugo@example.com\n[commit]\n\tgpgsign = false\n")
        File(dir, ".gitignore").writeText("archive/\n")
        val order = File(dir, "order.puml").apply {
            writeText("@startuml\nactor Customer\nparticipant Shop\nparticipant Kitchen\n\nCustomer -> Shop: order pizza\nShop -> Kitchen: bake\nKitchen --> Shop: ready\nShop --> Customer: deliver\n@enduml\n")
        }
        val classes = File(dir, "model/classes.puml").apply { parentFile.mkdirs(); writeText("@startuml\nclass Pizza\nclass Topping\nPizza o-- Topping\n@enduml\n") }
        JGitService().find(dir)!!.use { it.commit(listOf(order, classes, File(dir, ".gitignore")), "init") }
        // Now: edit a line, add two, delete one; a new file; an ignored folder.
        order.writeText("@startuml\nactor Customer\nparticipant Shop\nparticipant Kitchen\nparticipant Rider\n\nCustomer -> Shop: order pizza\nShop -> Kitchen: bake (12 min)\nKitchen --> Shop: ready\nShop -> Rider: pick up\nRider --> Customer: deliver\n@enduml\n")
        File(dir, "model/states.puml").writeText("@startuml\n[*] --> Ordered\nOrdered --> Baking\nBaking --> Delivered\n@enduml\n")
        File(dir, "archive").mkdirs(); File(dir, "archive/old.puml").writeText("@startuml\nA -> B\n@enduml\n")
        return TestGraph.workspace().apply {
            openRoot(dir)
            expanded.add(File(dir, "model")); expanded.add(File(dir, "archive")); refreshTree()
            open(order)
            changeLayout(EditorLayout.SPLIT)
        }
    }

    @Test fun commitWindowAndGutter() {
        val p = repo().apply { showTool(SidebarTool.COMMIT); git.editMessage("Add the rider to the delivery") }
        shoot("git-commit-window") { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test fun projectTreeColours() {
        val p = repo().apply { showTool(SidebarTool.PROJECT) }
        shoot("git-project-tree") { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test fun branchPopup() {
        val p = repo()
        JGitService().find(dir)!!.use { it.createBranch("feature/rider"); it.checkout("main"); it.createBranch("docs") }
        runBlocking { p.git.open(dir) }
        shoot("git-branch-popup") {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(end = 80.dp, bottom = 4.dp)) {
                    BranchButton(p, initiallyOpen = true)
                }
            }
        }
    }

    @Test fun textDiff() {
        val p = repo()
        runBlocking { p.git.open(dir) }
        shoot("git-text-diff") { DiffView(p, File(dir, "order.puml"), es.hugoalvarezajenjo.sproutstudio.domain.git.DiffSide.Head, es.hugoalvarezajenjo.sproutstudio.domain.git.DiffSide.WorkingCopy, textFirst = true) }
    }

    @Test fun history() {
        val p = repo()
        val order = File(dir, "order.puml")
        JGitService().find(dir)!!.use { it.commit(listOf(order), "Add the rider to the delivery") }
        order.writeText(order.readText().replace("pick up", "pick up the order"))
        JGitService().find(dir)!!.use { it.commit(listOf(order), "Clearer rider step") }
        runBlocking { p.git.open(dir) }
        shoot("git-history") { HistoryView(p, order) }
    }

    @Test fun commitWindowWithStashesAndLastCommit() {
        val p = repo()
        JGitService().find(dir)!!.use { it.stash("Try a second kitchen") }
        File(dir, "order.puml").appendText("' tweak\n")
        p.showTool(SidebarTool.COMMIT)
        shoot("git-commit-window-2") { Workspace(p, onSave = {}, onCloseTab = {}) }
    }

    @Test fun compareWithHead() {
        val p = repo()
        runBlocking { p.git.open(dir) }
        shoot("git-compare") { DiagramDiffView(p, File(dir, "order.puml")) }
    }
}
