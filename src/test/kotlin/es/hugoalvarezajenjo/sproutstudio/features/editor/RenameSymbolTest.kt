package es.hugoalvarezajenjo.sproutstudio.features.editor

import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.RenameProblem
import es.hugoalvarezajenjo.sproutstudio.features.workspace.Workspace
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.ide
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** ⇧F6 on a real workspace over files on disk: the plan, the edits in every tab, undo. */
@OptIn(ExperimentalTestApi::class)
class RenameSymbolTest {
    private val dir: File = Files.createTempDirectory("sprout-rename").toFile()
    @AfterTest fun cleanup() { dir.deleteRecursively() }

    private val mainText = "!include lib/procs.puml\n@startuml\n\$call(A, B)\n@enduml\n"
    private val libText = "!procedure \$call(\$a, \$b)\n\$a -> \$b\n!endprocedure\n"

    private fun workspace() = TestGraph.workspace().apply {
        File(dir, "lib").mkdirs()
        File(dir, "lib/procs.puml").writeText(libText)
        File(dir, "main.puml").writeText(mainText)
        openRoot(dir)
        open(File(dir, "main.puml"))
    }

    @Test
    fun aProcedureIsRenamedInTheIncludedFileTooWhichOpensBehindAndEachTabUndoesItsOwnPart() {
        val p = workspace()
        val main = p.active!!
        main.value = main.value.copy(selection = TextRange(mainText.indexOf("\$call") + 2))
        p.renameSymbol()
        val plan = assertNotNull(p.renaming)
        assertEquals(2, plan.files)

        assertEquals(RenameProblem.MISSING_DOLLAR, p.rename("send"))
        assertNull(p.rename("\$send"))
        assertNull(p.renaming, "the dialog closes")

        assertEquals(main, p.active, "the included file opened behind, not in front")
        assertEquals("!include lib/procs.puml\n@startuml\n\$send(A, B)\n@enduml\n", main.text)
        val lib = p.docs.single { it.file?.name == "procs.puml" }
        assertEquals("!procedure \$send(\$a, \$b)\n\$a -> \$b\n!endprocedure\n", lib.text)
        assertEquals(libText, File(dir, "lib/procs.puml").readText(), "the change waits in the tab until it is saved, like any edit")

        main.undo()
        assertEquals(mainText, main.text)
        lib.undo()
        assertEquals(libText, lib.text)
        p.close()
    }

    @Test
    fun aPlanIsNotAppliedOnceAFileItCoversHasChanged() {
        val p = workspace()
        val main = p.active!!
        main.value = main.value.copy(selection = TextRange(mainText.indexOf("\$call") + 2))
        p.renameSymbol()
        main.value = main.value.copy(text = main.text + "' edited\n")
        assertEquals(RenameProblem.STALE, p.rename("\$send"))
        assertEquals(mainText + "' edited\n", main.text)
        p.close()
    }

    @Test
    fun offASymbolNothingOpens() {
        val p = workspace()
        p.active!!.value = p.active!!.value.copy(selection = TextRange(mainText.indexOf("@startuml")))
        p.renameSymbol()
        assertNull(p.renaming)
        p.close()
    }

    @Test
    fun theDialogTakesTheNewNameAndEnterAppliesIt() {
        val p = TestGraph.workspace().apply { openRoot(dir) }
        val text = "@startuml\nparticipant App\nUser -> App\n@enduml\n"
        File(dir, "seq.puml").writeText(text)
        p.open(File(dir, "seq.puml"))
        val doc = p.active!!
        doc.value = doc.value.copy(selection = TextRange(text.indexOf("App") + 1))
        p.renameSymbol()
        runComposeUiTest {
            setContent {
                PumlTheme(dark = true) { Surface(Modifier.size(1200.dp, 700.dp), color = ide.panel) { Workspace(p, onSave = {}, onCloseTab = {}) } }
            }
            onNodeWithTag("name-field").performTextReplacement("User")
            onNodeWithTag("name-confirm").performClick()
            waitForIdle()
            assertNotNull(p.renaming, "a taken name can't be confirmed")
            onNodeWithTag("name-field").performTextReplacement("Api")
            onNodeWithTag("name-confirm").performClick()
            waitUntil(timeoutMillis = 5_000) { p.renaming == null }
            assertEquals("@startuml\nparticipant Api\nUser -> Api\n@enduml\n", doc.text)
        }
        p.close()
    }
}
