package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.editor.changeAtCaret
import es.hugoalvarezajenjo.sproutstudio.editor.rollbackAtCaret
import es.hugoalvarezajenjo.sproutstudio.git.GitRepo
import es.hugoalvarezajenjo.sproutstudio.git.LineChange
import es.hugoalvarezajenjo.sproutstudio.model.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.model.LayoutPrefs
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import androidx.compose.ui.text.TextRange
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Rollback of one changed block from the gutter (click the bar) or at the caret (⌥⌘Z). */
@OptIn(ExperimentalTestApi::class)
class RollbackLinesUiTest {
    private val dir: File = Files.createTempDirectory("sprout-rollback").toFile()
    private lateinit var layout: EditorLayout
    @BeforeTest fun save() { layout = LayoutPrefs.editorLayout }
    @AfterTest fun cleanup() { LayoutPrefs.editorLayout = layout; dir.deleteRecursively() }

    private val committed = "@startuml\nA -> B\nB -> C\nC -> D\n@enduml\n"
    private val a get() = File(dir, "a.puml")

    private fun project(): ProjectState {
        GitRepo.init(dir).close()
        File(dir, ".git/config").appendText("[user]\n\tname = T\n\temail = t@e\n[commit]\n\tgpgsign = false\n")
        a.writeText(committed)
        GitRepo.find(dir)!!.use { it.commit(listOf(a), "init") }
        return ProjectState().apply { openRoot(dir); open(a); changeLayout(EditorLayout.EDITOR) }
    }

    @Test fun clickingTheGutterBarRollsThatBlockBackAndUndoRestoresIt() {
        val p = project()
        val doc = p.active!!
        // Line 3 modified, a line added after line 4.
        val edited = committed.replace("B -> C", "B --> C : changed").replace("C -> D\n", "C -> D\nD -> E\n")
        doc.value = androidx.compose.ui.text.input.TextFieldValue(edited)
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) } }
            }
            waitUntil(timeoutMillis = 10_000) { doc.gitLines.changes.size == 2 }
            // Click the change bar of line 3 (0-based 2): gutter is 58dp wide, bar at its right edge;
            // text has 10dp top padding and 21sp lines (density 1 in tests -> px == dp).
            onNodeWithTag("editor-gutter").performMouseInput {
                val lineH = 21.sp.toPx()
                click(Offset(56.dp.toPx(), 10.dp.toPx() + lineH * 2 + lineH / 2))
            }
            waitForIdle()
            onNodeWithTag("change-popup") // exists
            // Screenshot of the popup for a visual check.
            run {
                val img = onAllNodes(androidx.compose.ui.test.isRoot())[1].captureToImage()
                val bmp = org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap())
                File("build/ui-snapshots").mkdirs()
                File("build/ui-snapshots/git-change-popup.png").writeBytes(bmp.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
            }
            assertEquals(1, onAllNodes(hasText("B -> C")).fetchSemanticsNodes().size) // old text shown
            assertEquals(1, onAllNodes(hasText("B --> C : changed")).fetchSemanticsNodes().size) // and the new one
            assertEquals(0, onAllNodes(hasText("Show Diff")).fetchSemanticsNodes().size) // no detour to the diff window
            // Laid over the block: the diff rows start on line 3 (gutter top + 10dp padding + 2 lines of
            // 21sp, give or take the first line's trimmed top) and each row is one editor line tall.
            val gutterTop = onNodeWithTag("editor-gutter").fetchSemanticsNode().boundsInWindow.top
            val diffTop = onNodeWithTag("change-popup-diff", useUnmergedTree = true).fetchSemanticsNode().boundsInWindow.top
            val newRow = onNode(hasText("B --> C : changed"), useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
            with(density) {
                val line3 = gutterTop + 10.dp.toPx() + 21.sp.toPx() * 2
                assert(diffTop in (line3 - 8f)..(line3 + 2f)) { "diff rows at $diffTop, line 3 at about $line3" }
                assert(kotlin.math.abs(newRow.height - 21.sp.toPx()) < 1.5f) { "row height ${newRow.height}" }
            }
            onNode(hasText("Rollback") and hasClickAction()).performClick()
            waitForIdle()
            assertEquals(committed.replace("C -> D\n", "C -> D\nD -> E\n"), doc.text) // only that block
            // Undo from the toast puts the edit back.
            onNode(hasText("Undo") and hasClickAction()).performClick()
            waitForIdle()
            assertEquals(edited, doc.text)
            assertNull(doc.rollbackUndo)
            assertEquals(1, onAllNodes(hasSetTextAction()).fetchSemanticsNodes().size)
        }
    }

    @Test fun rollbackAtCaretPicksTheBlockUnderIt() {
        val p = project()
        val doc = p.active!!
        val edited = committed.replace("C -> D\n", "") // deletion above @enduml
        doc.value = androidx.compose.ui.text.input.TextFieldValue(edited)
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) { Surface(Modifier.size(1000.dp, 600.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) } }
            }
            waitUntil(timeoutMillis = 10_000) { doc.gitLines.changes.size == 1 }
            assertEquals(LineChange.Type.DELETED, doc.gitLines.changes.single().type)
            // Caret on "B -> C" (just above the deleted line): ⌥⌘Z restores it.
            doc.value = doc.value.copy(selection = TextRange(edited.indexOf("B -> C")))
            waitForIdle()
            assertEquals(LineChange.Type.DELETED, doc.changeAtCaret()?.type)
            doc.rollbackAtCaret()
            waitForIdle()
            assertEquals(committed, doc.text)
            waitUntil(timeoutMillis = 10_000) { doc.gitLines.changes.isEmpty() }
        }
    }
}
