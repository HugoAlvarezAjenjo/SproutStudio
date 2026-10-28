package es.hugoalvarezajenjo.sproutstudio.features.workspace

import es.hugoalvarezajenjo.sproutstudio.TestGraph
import es.hugoalvarezajenjo.sproutstudio.features.palette.projectCommands
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.Action
import es.hugoalvarezajenjo.sproutstudio.ui.designsystem.text
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Each action is defined once and shows up the same in every place that lists it. */
class WorkspaceActionsTest {
    private val dir: File = Files.createTempDirectory("actions").toFile()
    private val file = File(dir, "a.puml").apply { writeText("@startuml\nA -> B\n@enduml\n") }

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    private fun all(p: WorkspaceViewModel): List<Action> = p.actions.all

    @Test fun `ids are unique`() {
        val ids = all(TestGraph.workspace()).map { it.id }
        assertEquals(ids.size, ids.toSet().size, ids.groupBy { it }.filter { it.value.size > 1 }.keys.toString())
    }

    @Test fun `the palette lists every action with its words, shortcut and state`() {
        val p = TestGraph.workspace().apply { openRoot(dir); open(file) }
        val palette = projectCommands(p, onSave = {}, onSaveAs = {}, onCloseTab = {}).associateBy { it.id }
        for (a in all(p)) {
            val c = palette[a.id] ?: error("${a.id} is not in the palette")
            assertEquals(text(a.title), c.title, a.id)
            assertEquals(a.shortcut?.hint, c.shortcut, a.id)
            assertEquals(a.enabled, c.enabled, a.id)
        }
    }

    @Test fun `editor actions are off without a document`() {
        val p = TestGraph.workspace()
        val a = p.actions
        listOf(a.find, a.replace, a.foldBlock, a.reformat, a.goToDeclaration, a.toggleComment).forEach { assertTrue(!it.enabled, it.id) }
        assertTrue(!a.findInFiles.enabled) // no project folder either
    }
}
