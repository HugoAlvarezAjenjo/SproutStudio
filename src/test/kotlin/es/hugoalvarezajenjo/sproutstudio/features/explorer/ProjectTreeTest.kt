package es.hugoalvarezajenjo.sproutstudio.features.explorer

import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.TestRepos
import es.hugoalvarezajenjo.sproutstudio.services.git.JGitService
import es.hugoalvarezajenjo.sproutstudio.services.render.ExportFormat

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.features.explorer.FileAction
import es.hugoalvarezajenjo.sproutstudio.features.workspace.WorkspaceViewModel
import es.hugoalvarezajenjo.sproutstudio.features.workspace.Workspace
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The project tree: collapsible root and the new / rename / delete dialogs. */
@OptIn(ExperimentalTestApi::class)
class ProjectTreeTest {

    private lateinit var dir: File
    private val realTrash = TestGraph.graph.trash

    @BeforeTest fun setUp() {
        dir = Files.createTempDirectory("tree").toFile().canonicalFile
        File(dir, "a.puml").writeText("@startuml\nA -> B\n@enduml\n")
        File(dir, "docs").mkdirs(); File(dir, "docs/b.puml").writeText("@startuml\nB -> C\n@enduml\n")
        TestGraph.graph.trash = { it.deleteRecursively() }
    }

    @AfterTest fun tearDown() { TestGraph.graph.trash = realTrash; dir.deleteRecursively() }

    private fun count(tag: String, t: androidx.compose.ui.test.ComposeUiTest) =
        t.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test fun rootFolderCollapsesAndExpands() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        assertEquals(1, count("tree-row-a.puml", this))
        onNodeWithTag("tree-root", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(0, count("tree-row-a.puml", this), "collapsed root hides its children")
        onNodeWithTag("tree-root", useUnmergedTree = true).performClick()
        waitForIdle()
        assertEquals(1, count("tree-row-a.puml", this))
    }

    @Test fun newFolderFromTheDialogShowsUpEvenWhenEmpty() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir); explorer.showFileAction(FileAction.NewFolder(dir)) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        waitForIdle()
        run {
            val img = onAllNodes(isRoot()).fetchSemanticsNodes().let { onAllNodes(isRoot())[it.size - 1] }.captureToImage()
            File("build/ui-snapshots").mkdirs()
            File("build/ui-snapshots/tree-new-folder.png").writeBytes(
                org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        }
        onNodeWithTag("name-field").performTextClearance()
        onNodeWithTag("name-field").performTextInput("diagrams")
        onNodeWithTag("name-confirm").performClick()
        waitForIdle()
        assertTrue(File(dir, "diagrams").isDirectory)
        assertNull(p.explorer.fileAction)
        assertEquals(1, count("tree-row-diagrams", this))
    }

    @Test fun anExistingNameIsRefusedInTheDialog() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir); explorer.showFileAction(FileAction.NewFolder(dir)) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        onNodeWithTag("name-field").performTextClearance()
        onNodeWithTag("name-field").performTextInput("docs")
        waitForIdle()
        onNodeWithTag("name-confirm").performClick()
        waitForIdle()
        assertTrue(p.explorer.fileAction != null, "dialog stays open")
        assertTrue(onAllNodes(androidx.compose.ui.test.hasText("already exists", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun renameAndDeleteFromTheDialogs() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir); open(File(dir, "a.puml")); explorer.showFileAction(FileAction.Rename(File(dir, "a.puml"))) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        onNodeWithTag("name-field").performTextClearance()
        onNodeWithTag("name-field").performTextInput("order.puml")
        onNodeWithTag("name-confirm").performClick()
        waitForIdle()
        assertTrue(File(dir, "order.puml").exists())
        assertEquals("order.puml", p.active?.name)

        p.explorer.showFileAction(FileAction.Delete(File(dir, "order.puml")))
        waitForIdle()
        onNodeWithTag("file-delete-confirm").performClick()
        waitForIdle()
        assertFalse(File(dir, "order.puml").exists())
        assertTrue(p.docs.isEmpty())
        assertEquals(0, count("tree-row-order.puml", this))
    }

    private fun androidx.compose.ui.test.ComposeUiTest.center(tag: String) =
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

    /** Press on [from], move in steps to [to], release: a real mouse drag. */
    private fun androidx.compose.ui.test.ComposeUiTest.dragMouse(from: androidx.compose.ui.geometry.Offset, to: androidx.compose.ui.geometry.Offset, snapshot: String? = null) {
        onRoot().performMouseInput {
            moveTo(from); press()
            for (k in 1..10) moveTo(from + (to - from) * (k / 10f))
        }
        waitForIdle()
        snapshot?.let { name ->
            val img = onAllNodes(isRoot())[0].captureToImage()
            File("build/ui-snapshots").mkdirs()
            File("build/ui-snapshots/$name.png").writeBytes(
                org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        }
        onRoot().performMouseInput { release() }
        waitForIdle()
    }

    @Test fun draggingAFileOntoAFolderMovesIt() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir); open(File(dir, "a.puml")) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        waitForIdle()
        dragMouse(center("tree-row-a.puml"), center("tree-row-docs"), snapshot = "tree-drag")
        assertTrue(File(dir, "docs/a.puml").exists(), "moved on disk")
        assertFalse(File(dir, "a.puml").exists())
        assertEquals(File(dir, "docs/a.puml"), p.active?.file?.absoluteFile, "the open tab follows")
        assertEquals(1, count("tree-row-a.puml", this), "visible inside the expanded folder")
    }

    @Test fun draggingBackOntoTheProjectFolderRowMovesToTheRoot() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir); explorer.toggleDir(File(dir, "docs")) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        waitForIdle()
        dragMouse(center("tree-row-b.puml"), center("tree-root"))
        assertTrue(File(dir, "b.puml").exists())
        assertFalse(File(dir, "docs/b.puml").exists())
    }

    @Test fun aShortDragOrAClickDoesNotMoveAnything() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        waitForIdle()
        onNodeWithTag("tree-row-a.puml", useUnmergedTree = true).performClick()
        waitForIdle()
        assertTrue(File(dir, "a.puml").exists())
        assertEquals("a.puml", p.active?.name, "a click still opens the file")
        // Dropped on its own folder: nothing happens, no error.
        dragMouse(center("tree-row-a.puml"), center("tree-row-a.puml") + androidx.compose.ui.geometry.Offset(0f, 4f))
        assertTrue(File(dir, "a.puml").exists())
        assertEquals(0, count("tree-message", this))
    }

    @Test fun newDiagramWithATemplateChosenFromTheGallery() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir); explorer.showFileAction(FileAction.NewDiagram(dir)) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1100.dp, 760.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        onNodeWithTag("name-field").performTextClearance()
        onNodeWithTag("name-field").performTextInput("login")
        onNodeWithTag("choose-template").performClick()
        waitForIdle()
        onNodeWithTag("template-gallery")
        assertEquals(0, count("template-card-block-note", this), "blocks aren't offered for a new file")
        onNodeWithTag("template-card-seq").performClick()
        waitForIdle()
        // Back in the dialog: the typed name survived, the template is shown.
        assertTrue(onAllNodes(androidx.compose.ui.test.hasText("Sequence", substring = true)).fetchSemanticsNodes().isNotEmpty())
        run {
            val roots = onAllNodes(isRoot())
            val img = roots[roots.fetchSemanticsNodes().size - 1].captureToImage()
            File("build/ui-snapshots/new-diagram-template.png").writeBytes(
                org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        }
        onNodeWithTag("name-confirm").performClick()
        val f = File(dir, "login.puml")
        // The write runs on the IO dispatcher, which waitForIdle doesn't wait for.
        waitUntil(timeoutMillis = 5_000) { f.exists() }
        assertTrue(f.readText().startsWith("@startuml\nactor User"), f.readText())
    }

    @Test fun newDiagramWithoutATemplateIsEmpty() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir); explorer.showFileAction(FileAction.NewDiagram(dir)) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1100.dp, 760.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        onNodeWithTag("name-confirm").performClick()
        val f = File(dir, "new-diagram.puml")
        waitUntil(timeoutMillis = 5_000) { f.exists() }
        assertEquals("", f.readText())
    }

    private fun androidx.compose.ui.test.ComposeUiTest.rightClick(tag: String) {
        onNodeWithTag(tag).performMouseInput { moveTo(center); press(MouseButton.Secondary); release(MouseButton.Secondary) }
        waitForIdle()
    }

    @Test fun fileMenuOutsideARepositoryHasNoGitEntries() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        rightClick("tree-row-a.puml")
        listOf("Open", "Duplicate", "Copy Path", "Copy Relative Path", "Rename…", "Move to Trash…").forEach {
            assertEquals(1, count("menu-item:$it", this), it)
        }
        listOf("Show History", "Rollback…", "Add to .gitignore").forEach { assertEquals(0, count("menu-item:$it", this), it) }
    }

    @Test fun duplicateCopiesTheDiagramAndOpensIt() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        rightClick("tree-row-a.puml")
        onNodeWithTag("menu-item:Duplicate").performClick()
        val copy = File(dir, "a-copy.puml")
        waitUntil(timeoutMillis = 5_000) { p.active?.file?.absoluteFile == copy }
        assertEquals(File(dir, "a.puml").readText(), copy.readText())
        assertEquals(1, count("tree-row-a-copy.puml", this))
    }

    @Test fun expandAllOpensEveryFolderBelowAndCollapseAllClosesThem() = runComposeUiTest {
        File(dir, "docs/deep").mkdirs(); File(dir, "docs/deep/c.puml").writeText("@startuml\n@enduml\n")
        val p = TestGraph.workspace().apply { openRoot(dir) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        rightClick("tree-row-docs")
        onNodeWithTag("menu-item:Expand All").performClick()
        waitForIdle()
        assertEquals(1, count("tree-row-c.puml", this))
        rightClick("tree-row-docs")
        onNodeWithTag("menu-item:Collapse All").performClick()
        waitForIdle()
        assertEquals(0, count("tree-row-b.puml", this))
        assertEquals(1, count("tree-row-a.puml", this)) // outside docs: untouched
    }

    @Test fun relativePathsUseSlashesFromTheProjectFolder() {
        val p = TestGraph.workspace().apply { openRoot(dir) }
        assertEquals("docs/b.puml", p.explorer.relativePath(File(dir, "docs/b.puml")))
        assertEquals(".", p.explorer.relativePath(dir))
        assertNull(p.explorer.relativePath(dir.parentFile))
    }

    @Test fun inARepositoryAChangedFileOffersRollbackAndAFolderCanBeIgnored() {
        JGitService().init(dir).close()
        TestRepos.configure(dir, "T", "t@e")
        JGitService().find(dir)!!.use { it.commit(listOf(File(dir, "a.puml")), "one") }
        File(dir, "a.puml").appendText("' changed\n")
        val p = TestGraph.workspace().apply { openRoot(dir) }
        runComposeUiTest {
            setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
            waitUntil(timeoutMillis = 10_000) { p.git.status.head != null && p.git.changeOf(File(dir, "a.puml")) != null }
            rightClick("tree-row-a.puml")
            listOf("Show History", "Rollback…", "Add to .gitignore").forEach { assertEquals(1, count("menu-item:$it", this), it) }
            onNodeWithTag("menu-item:Rollback…").performClick()
            waitForIdle()
            assertEquals(0, count("context-menu", this))
            onNode(androidx.compose.ui.test.hasText("Cancel")).performClick() // the confirmation, not done yet
            waitForIdle()
            rightClick("tree-row-docs")
            onNodeWithTag("menu-item:Add to .gitignore").performClick()
            waitUntil(timeoutMillis = 10_000) { p.git.isIgnored(File(dir, "docs")) }
        }
        assertEquals("/docs/\n", File(dir, ".gitignore").readText())
        assertTrue(File(dir, "a.puml").readText().contains("changed")) // the rollback was cancelled
        p.git.close()
    }

    @Test fun exportSubmenuOffersCopyAndEveryFormat() = runComposeUiTest {
        val p = TestGraph.workspace().apply { openRoot(dir) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        rightClick("tree-row-a.puml")
        onNodeWithTag("menu-item:Export").performMouseInput { moveTo(center) }
        waitForIdle()
        listOf("Copy Diagram Image", "SVG (vector)", "PNG (image)", "PDF (vector)").forEach { assertEquals(1, count("menu-item:$it", this), it) }
        rightClick("tree-row-docs")
        assertEquals(0, count("menu-item:Export", this)) // folders have no diagram to export
    }

    @Test fun exportWritesTheFileThatIsNotOpen() = kotlinx.coroutines.runBlocking {
        val p = TestGraph.workspace().apply { openRoot(dir) }
        val out = File(dir, "out.svg")
        assertTrue(p.explorer.exportDiagram(File(dir, "a.puml"), ExportFormat.SVG, out).isSuccess)
        assertTrue(out.readText().contains("<svg"))
    }

    @Test fun exportUsesTheUnsavedTextOfAnOpenTab() = kotlinx.coroutines.runBlocking {
        val a = File(dir, "a.puml")
        val p = TestGraph.workspace().apply { openRoot(dir); open(a) }
        p.active!!.value = p.active!!.value.copy(text = "@startuml\nUnsaved -> Edit\n@enduml\n")
        val out = File(dir, "out.svg")
        p.explorer.exportDiagram(a, ExportFormat.SVG, out).getOrThrow()
        assertTrue(out.readText().contains("Unsaved"))
        assertFalse(a.readText().contains("Unsaved")) // nothing was saved
    }
}
