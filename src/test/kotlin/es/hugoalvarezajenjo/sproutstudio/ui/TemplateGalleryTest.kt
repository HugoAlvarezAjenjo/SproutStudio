package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.editor.EditOps
import es.hugoalvarezajenjo.sproutstudio.lang.Template
import es.hugoalvarezajenjo.sproutstudio.lang.Templates
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The template gallery overlay and the insert behaviour behind it. */
@OptIn(ExperimentalTestApi::class)
class TemplateGalleryTest {

    @Test fun showsCardsFiltersAndPicksOne() = runComposeUiTest {
        var picked: Template? = null
        var dismissed = false
        setContent {
            PumlTheme(dark = true) {
                Surface(Modifier.size(1100.dp, 760.dp), color = ide.panel) {
                    TemplateGallery(onDismiss = { dismissed = true }, onPick = { picked = it })
                }
            }
        }
        onNodeWithTag("template-gallery")
        // Every category heading and a few cards are present.
        assertTrue(onAllNodes(hasText("Sequence")).fetchSemanticsNodes().isNotEmpty())
        onNodeWithTag("template-card-c4-context") // the C4 one exists

        // Snapshot for a visual check.
        waitForIdle()
        run {
            val img = onAllNodes(isRoot())[1].captureToImage()
            val bmp = org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap())
            File("build/ui-snapshots").mkdirs()
            File("build/ui-snapshots/template-gallery.png").writeBytes(bmp.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        }

        // Filtering to "gantt" hides the sequence card.
        onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("gantt")
        waitForIdle()
        assertEquals(0, onAllNodes(hasText("Sequence")).fetchSemanticsNodes().size)

        // Clicking a card reports the pick.
        onNodeWithTag("template-card-gantt").performClick()
        assertEquals("gantt", picked?.id)
    }

    @Test fun wholeTemplateReplacesABlankDocumentButInsertsIntoANonBlankOne() {
        val seq = Templates.all.first { it.id == "seq" }
        // Blank doc -> becomes the whole template, caret on the marker.
        val blank = EditOps.insertTemplate(TextFieldValue(""), seq)
        assertTrue(blank.text.startsWith("@startuml\nactor User"))
        assertTrue("\$0" !in blank.text)
        assertEquals("request", blank.text.substring(blank.selection.start, blank.selection.start + 7))

        // Non-blank doc -> inserted at the caret, old content kept.
        val existing = TextFieldValue("@startuml\n@enduml\n", androidx.compose.ui.text.TextRange(9))
        val merged = EditOps.insertTemplate(existing, seq)
        assertTrue(merged.text.contains("@enduml"))
        assertTrue(merged.text.contains("actor User"))
    }

    @Test fun blockTemplateAlwaysInsertsAtCaretEvenInABlankDoc() {
        val note = Templates.all.first { it.id == "block-note" }
        val out = EditOps.insertTemplate(TextFieldValue(""), note)
        assertTrue(out.text.trimStart().startsWith("note"))
        assertTrue("@start" !in out.text)
    }

    @Test fun sidebarListsTemplatesAndPicksOne() = runComposeUiTest {
        var picked: Template? = null
        setContent {
            PumlTheme(dark = true) {
                Surface(Modifier.size(300.dp, 700.dp), color = ide.panel) {
                    TemplateSidebar(onPick = { picked = it })
                }
            }
        }
        onNodeWithTag("template-sidebar")
        onNodeWithTag("template-row-seq")
        onNodeWithTag("template-row-c4-context")
        waitForIdle()
        run {
            val img = onNode(isRoot()).captureToImage()
            val bmp = org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap())
            File("build/ui-snapshots").mkdirs()
            File("build/ui-snapshots/template-sidebar.png").writeBytes(bmp.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        }
        onNodeWithTag("template-row-gantt").performClick()
        assertEquals("gantt", picked?.id)
    }

    @Test fun modelInsertTemplateOpensADocWhenNoneIsActive() {
        val p = ProjectState()
        assertNull(p.active)
        p.insertTemplate(Templates.all.first { it.id == "seq" })
        assertTrue(p.active != null)
        assertTrue(p.active!!.text.startsWith("@startuml\nactor User"))
    }
}
