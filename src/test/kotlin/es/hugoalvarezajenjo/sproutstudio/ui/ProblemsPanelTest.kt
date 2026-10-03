package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.render.Problem
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bottom Problems panel: listing, click-to-jump, and collapse behaviour. */
@OptIn(ExperimentalTestApi::class)
class ProblemsPanelTest {

    private val sample = listOf(
        Problem(line = 7, message = "Syntax Error?", diagramIndex = 1),
        Problem(line = 12, message = "cannot be parsed", diagramIndex = 2),
        Problem(line = null, message = "No @enduml found", diagramIndex = 0),
    )

    @Test fun expandedListsEveryProblemAndClickJumps() = runComposeUiTest {
        var target: ProblemTarget? = null
        setContent {
            PumlTheme(dark = true) {
                Surface(Modifier.size(900.dp, 300.dp), color = ide.panel) {
                    ProblemsPanel(
                        problems = sample,
                        expanded = true,
                        multiBlock = true,
                        onToggle = {},
                        onGoTo = { target = it },
                    )
                }
            }
        }
        onNodeWithTag("problems-panel")
        // All three messages are listed, and the multi-block location shows the diagram.
        assertTrue(onAllNodes(hasText("Syntax Error?", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodes(hasText("No @enduml found", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertTrue(onAllNodes(hasText("Diagram 2 · line 7", substring = true)).fetchSemanticsNodes().isNotEmpty())

        waitForIdle()
        run {
            val img = onAllNodes(isRoot())[0].captureToImage()
            val bmp = org.jetbrains.skia.Image.makeFromBitmap(img.asSkiaBitmap())
            File("build/ui-snapshots").mkdirs()
            File("build/ui-snapshots/problems-panel.png").writeBytes(bmp.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        }

        // Clicking the first row jumps to its line and diagram block.
        onNodeWithTag("problem-row-0").performClick()
        waitForIdle()
        assertEquals(7, target?.line)
        assertEquals(1, target?.diagramIndex)

        // A line-less problem still jumps to its block, with a null line.
        onNodeWithTag("problem-row-2").performClick()
        waitForIdle()
        assertEquals(0, target?.diagramIndex)
        assertNull(target?.line)
    }

    @Test fun collapsedHidesTheListButKeepsTheCountBar() = runComposeUiTest {
        setContent {
            PumlTheme(dark = true) {
                Surface(Modifier.size(900.dp, 60.dp), color = ide.panel) {
                    ProblemsPanel(sample, expanded = false, multiBlock = true, onToggle = {}, onGoTo = {})
                }
            }
        }
        // Header still shows "Problems"; no rows are rendered.
        assertTrue(onAllNodes(hasText("Problems", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, onAllNodes(hasText("cannot be parsed", substring = true)).fetchSemanticsNodes().size)
    }

    @Test fun aCleanFileShowsNoProblems() = runComposeUiTest {
        setContent {
            PumlTheme(dark = true) {
                Surface(Modifier.size(900.dp, 60.dp), color = ide.panel) {
                    ProblemsPanel(emptyList(), expanded = false, multiBlock = false, onToggle = {}, onGoTo = {})
                }
            }
        }
        assertTrue(onAllNodes(hasText("No problems", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun singleBlockLocationOmitsTheDiagramLabel() = runComposeUiTest {
        setContent {
            PumlTheme(dark = true) {
                Surface(Modifier.size(900.dp, 300.dp), color = ide.panel) {
                    ProblemsPanel(
                        listOf(Problem(5, "oops", 0)),
                        expanded = true, multiBlock = false, onToggle = {}, onGoTo = {},
                    )
                }
            }
        }
        waitForIdle()
        assertTrue(onAllNodes(hasText("line 5", substring = true)).fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, onAllNodes(hasText("Diagram", substring = true)).fetchSemanticsNodes().size)
    }
}
