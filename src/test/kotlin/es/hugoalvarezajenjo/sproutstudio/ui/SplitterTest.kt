package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import es.hugoalvarezajenjo.sproutstudio.model.LayoutPrefs
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/** The panel divider must be grabbable a few pixels either side of its 1px line. */
@OptIn(ExperimentalTestApi::class)
class SplitterTest {

    private var savedWidth = 0f
    @BeforeTest fun save() { savedWidth = LayoutPrefs.sidebarWidth }
    @AfterTest fun restore() { LayoutPrefs.sidebarWidth = savedWidth } // don't leave the user's prefs changed

    /** Presses [dxFromLine] px from the sidebar's border line, drags 60px right, returns the new width. */
    private fun dragFrom(dxFromLine: Float): Float {
        val p = ProjectState().apply {
            openRoot(File("samples").absoluteFile)
            resizeSidebar(240f)
        }
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) {
                    Surface(Modifier.size(1200.dp, 700.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) }
                }
            }
            waitForIdle()
            val density = density.density
            // stripe (40) + its border (1) + sidebar (240) = the splitter's line.
            val lineX = (40f + 1f + 240f) * density
            onRoot().performMouseInput {
                moveTo(Offset(lineX + dxFromLine * density, 300f))
                press()
                repeat(6) { moveBy(Offset(10f * density, 0f)) }
                release()
            }
            waitForIdle()
        }
        return p.sidebarWidth
    }

    @Test fun grabbingRightOnTheLineResizes() { val w = dragFrom(0.5f); assertTrue(w > 270f, "width stayed at $w") }

    @Test fun grabbingFourPixelsRightOfTheLineResizes() { val w = dragFrom(4f); assertTrue(w > 270f, "width stayed at $w") }

    @Test fun grabbingFourPixelsLeftOfTheLineResizes() { val w = dragFrom(-4f); assertTrue(w > 270f, "width stayed at $w") }
}
