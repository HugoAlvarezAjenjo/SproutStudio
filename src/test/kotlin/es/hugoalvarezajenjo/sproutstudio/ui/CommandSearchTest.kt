package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import es.hugoalvarezajenjo.sproutstudio.editor.EditOps
import es.hugoalvarezajenjo.sproutstudio.model.EditorLayout
import es.hugoalvarezajenjo.sproutstudio.model.LayoutPrefs
import es.hugoalvarezajenjo.sproutstudio.model.PreviewAction
import es.hugoalvarezajenjo.sproutstudio.model.ProjectState
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CommandSearchTest {

    private fun c(title: String, category: String = "View", id: String = title, enabled: Boolean = true, searchOnly: Boolean = false) =
        Command(id, title, category, enabled = enabled, searchOnly = searchOnly) {}

    private val commands = listOf(
        c("Find…", "Edit"),
        c("Fold All", "Code"),
        c("Switch to Light Theme"),
        c("Export Diagram as PNG…", "Preview"),
        c("Preview Only"),
        c("Save", "File", enabled = false),
        c("pizza.puml", "samples/", id = "open:pizza", searchOnly = true),
    )
    private fun titles(q: String, recents: List<String> = emptyList()) = CommandSearch.search(q, commands, recents).map { it.command.title }

    @Test fun prefixBeatsWordStartBeatsSubstring() {
        // "f": Find… and Fold All start with it; everything else only contains it.
        assertEquals(listOf("Find…", "Fold All"), titles("f").take(2))
        assertEquals("Preview Only", titles("only").first()) // word start
    }

    @Test fun acronymsMatch() {
        assertEquals("Switch to Light Theme", titles("stlt").first())
        assertEquals("Export Diagram as PNG…", titles("edp").first())
    }

    @Test fun everyWordMustMatchInAnyOrder() {
        assertEquals(listOf("Export Diagram as PNG…"), titles("png export"))
        assertEquals(emptyList(), titles("png theme"))
    }

    @Test fun categoryMatchesTooButRanksLower() {
        assertEquals(listOf("Fold All"), titles("code fold"))
        // Typing just the category lists its commands.
        assertTrue("Fold All" in titles("code"))
    }

    @Test fun highlightsTheMatchedCharacters() {
        val hit = CommandSearch.search("theme", commands).first()
        assertEquals("Switch to Light Theme", hit.command.title)
        assertEquals((16..20).toSet(), hit.positions)
    }

    @Test fun disabledCommandsAreHidden() {
        assertFalse("Save" in titles("save"))
    }

    @Test fun emptyQueryListsActionsRecentFirstButNotFiles() {
        val list = titles("", recents = listOf("Preview Only", "Fold All"))
        assertEquals(listOf("Preview Only", "Fold All"), list.take(2))
        assertFalse("pizza.puml" in list)
        assertFalse("Save" in list)
    }

    @Test fun filesShowUpWhenSearched() {
        assertEquals("pizza.puml", titles("piz").first())
    }

    @Test fun recentBreaksTies() {
        assertEquals("Fold All", titles("f", recents = listOf("Fold All")).first())
    }
}

class DoubleShiftTest {
    private var now = 10_000L // a real clock is never 0
    private val d = DoubleShiftDetector(windowMs = 400) { now }
    private fun tap(): Boolean { d.onKey(true, down = true); return d.onKey(true, down = false) }

    @Test fun twoQuickTapsTrigger() {
        assertFalse(tap()); now += 200; assertTrue(tap())
        now += 100; assertFalse(tap()) // a third tap starts over
    }

    @Test fun slowTapsDont() {
        assertFalse(tap()); now += 600; assertFalse(tap())
    }

    @Test fun typingACapitalInBetweenDoesNotTrigger() {
        assertFalse(tap())
        now += 100
        d.onKey(true, down = true); d.onKey(false, down = true); d.onKey(false, down = false) // Shift+A
        assertFalse(d.onKey(true, down = false))
        now += 100; assertFalse(tap())
    }
}

class InsertSnippetTest {
    @Test fun caretLandsOnTheMarkerAndTemplateGoesOnItsOwnLine() {
        val v = TextFieldValue("abc", TextRange(3))
        val r = EditOps.insertSnippet(v, "@startuml\nA -> \$0B\n@enduml\n")
        assertEquals("abc\n@startuml\nA -> B\n@enduml\n", r.text)
        assertEquals("abc\n@startuml\nA -> ".length, r.selection.start)
    }

    @Test fun atLineStartNoExtraNewline() {
        val r = EditOps.insertSnippet(TextFieldValue("x\n", TextRange(2)), "y")
        assertEquals("x\ny", r.text)
        assertEquals(3, r.selection.start)
    }
}

/** The real command list of a project window. */
class ProjectCommandsTest {
    private lateinit var savedLayout: EditorLayout
    @BeforeTest fun save() { savedLayout = LayoutPrefs.editorLayout }
    @AfterTest fun restore() { LayoutPrefs.editorLayout = savedLayout }

    private fun project() = ProjectState().apply {
        openRoot(File("samples").absoluteFile)
        open(File("samples/pizza.puml"))
        changeLayout(EditorLayout.SPLIT)
    }
    private fun ProjectState.commands() = projectCommands(this, {}, {}, {})
    private fun ProjectState.run(query: String) =
        CommandSearch.search(query, commands()).first().command.also { it.run() }

    @Test fun everyCommandHasAUniqueId() {
        val ids = project().commands().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun layoutCommandsSwitchTheEditor() {
        val p = project()
        assertEquals("Preview Only", p.run("preview only").title)
        assertEquals(EditorLayout.PREVIEW, p.layout)
        // The current layout isn't offered again.
        assertFalse(p.commands().first { it.title == "Preview Only" }.enabled)
    }

    @Test fun typingAFileNameOpensIt() {
        val p = project()
        assertEquals("morning.puml", p.run("morning").title)
        assertEquals("morning.puml", p.active?.name)
    }

    @Test fun findOpensTheFindBarAndRevealsTheEditor() {
        val p = project()
        p.changeLayout(EditorLayout.PREVIEW)
        p.run("find…")
        assertTrue(p.active!!.find.visible)
        assertEquals(EditorLayout.SPLIT, p.layout)
    }

    @Test fun insertTemplateAddsItAtTheCaret() {
        val p = project()
        val doc = p.active!!
        doc.value = doc.value.copy(selection = TextRange(doc.text.length))
        val before = doc.text
        p.run("insert sequence")
        assertTrue(doc.text.startsWith(before) && doc.text.length > before.length)
        assertTrue(doc.text.substring(before.length).contains("participant"))
        // Only in memory: nothing writes the sample back to disk here.
        assertTrue(doc.dirty)
    }

    @Test fun exportFromEditorOnlyOpensThePreviewAndQueuesTheRequest() {
        val p = project()
        p.changeLayout(EditorLayout.EDITOR)
        p.run("export png")
        assertEquals(EditorLayout.SPLIT, p.layout)
        assertNotNull(p.active?.previewRequest)
        assertEquals(PreviewAction.EXPORT_PNG, p.active?.previewRequest)
    }
}
