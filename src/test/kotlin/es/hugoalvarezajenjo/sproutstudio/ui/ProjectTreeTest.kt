package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.model.FileAction
import es.hugoalvarezajenjo.sproutstudio.model.FileOps
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
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
    private val realTrash = FileOps.trash

    @BeforeTest fun setUp() {
        dir = Files.createTempDirectory("tree").toFile().canonicalFile
        File(dir, "a.puml").writeText("@startuml\nA -> B\n@enduml\n")
        File(dir, "docs").mkdirs(); File(dir, "docs/b.puml").writeText("@startuml\nB -> C\n@enduml\n")
        FileOps.trash = { it.deleteRecursively() }
    }

    @AfterTest fun tearDown() { FileOps.trash = realTrash; dir.deleteRecursively() }

    private fun count(tag: String, t: androidx.compose.ui.test.ComposeUiTest) =
        t.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test fun rootFolderCollapsesAndExpands() = runComposeUiTest {
        val p = ProjectState().apply { openRoot(dir) }
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
        val p = ProjectState().apply { openRoot(dir); fileAction = FileAction.NewFolder(dir) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        waitForIdle()
        run {
            val img = onAllNodes(isRoot()).fetchSemanticsNodes().let { onAllNodes(isRoot())[it.size - 1] }.captureToImage()
            File("build/ui-snapshots").mkdirs()
            File("build/ui-snapshots/tree-new-folder.png").writeBytes(
                org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        }
        onNodeWithTag("file-name-field").performTextClearance()
        onNodeWithTag("file-name-field").performTextInput("diagrams")
        onNodeWithTag("file-name-confirm").performClick()
        waitForIdle()
        assertTrue(File(dir, "diagrams").isDirectory)
        assertNull(p.fileAction)
        assertEquals(1, count("tree-row-diagrams", this))
    }

    @Test fun anExistingNameIsRefusedInTheDialog() = runComposeUiTest {
        val p = ProjectState().apply { openRoot(dir); fileAction = FileAction.NewFolder(dir) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        onNodeWithTag("file-name-field").performTextClearance()
        onNodeWithTag("file-name-field").performTextInput("docs")
        waitForIdle()
        onNodeWithTag("file-name-confirm").performClick()
        waitForIdle()
        assertTrue(p.fileAction != null, "dialog stays open")
        assertTrue(onAllNodes(androidx.compose.ui.test.hasText("already exists", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun renameAndDeleteFromTheDialogs() = runComposeUiTest {
        val p = ProjectState().apply { openRoot(dir); open(File(dir, "a.puml")); fileAction = FileAction.Rename(File(dir, "a.puml")) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        onNodeWithTag("file-name-field").performTextClearance()
        onNodeWithTag("file-name-field").performTextInput("order.puml")
        onNodeWithTag("file-name-confirm").performClick()
        waitForIdle()
        assertTrue(File(dir, "order.puml").exists())
        assertEquals("order.puml", p.active?.name)

        p.fileAction = FileAction.Delete(File(dir, "order.puml"))
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
        val p = ProjectState().apply { openRoot(dir); open(File(dir, "a.puml")) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        waitForIdle()
        dragMouse(center("tree-row-a.puml"), center("tree-row-docs"), snapshot = "tree-drag")
        assertTrue(File(dir, "docs/a.puml").exists(), "moved on disk")
        assertFalse(File(dir, "a.puml").exists())
        assertEquals(File(dir, "docs/a.puml"), p.active?.file?.absoluteFile, "the open tab follows")
        assertEquals(1, count("tree-row-a.puml", this), "visible inside the expanded folder")
    }

    @Test fun draggingBackOntoTheProjectFolderRowMovesToTheRoot() = runComposeUiTest {
        val p = ProjectState().apply { openRoot(dir); toggleDir(File(dir, "docs")) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        waitForIdle()
        dragMouse(center("tree-row-b.puml"), center("tree-root"))
        assertTrue(File(dir, "b.puml").exists())
        assertFalse(File(dir, "docs/b.puml").exists())
    }

    @Test fun aShortDragOrAClickDoesNotMoveAnything() = runComposeUiTest {
        val p = ProjectState().apply { openRoot(dir) }
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
        val p = ProjectState().apply { openRoot(dir); fileAction = FileAction.NewDiagram(dir) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1100.dp, 760.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        onNodeWithTag("file-name-field").performTextClearance()
        onNodeWithTag("file-name-field").performTextInput("login")
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
        onNodeWithTag("file-name-confirm").performClick()
        waitForIdle()
        val f = File(dir, "login.puml")
        assertTrue(f.readText().startsWith("@startuml\nactor User"), f.readText())
    }

    @Test fun newDiagramWithoutATemplateIsEmpty() = runComposeUiTest {
        val p = ProjectState().apply { openRoot(dir); fileAction = FileAction.NewDiagram(dir) }
        setContent { PumlTheme(dark = true) { Surface(Modifier.size(1100.dp, 760.dp), color = ide.panel) { Workspace(p, {}, {}) } } }
        onNodeWithTag("file-name-confirm").performClick()
        waitForIdle()
        assertEquals("", File(dir, "new-diagram.puml").readText())
    }
}
