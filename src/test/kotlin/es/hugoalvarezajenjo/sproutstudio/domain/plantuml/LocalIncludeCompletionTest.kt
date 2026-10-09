package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Completion inside `!include path`: folders and PlantUML files next to the edited file. */
class LocalIncludeCompletionTest {

    private val file = "/p/main.puml"

    /** Folders by path relative to [file]'s folder; anything else can't be listed. */
    private val tree = mapOf(
        "" to listOf(
            PathEntry("common", true), PathEntry("styles.puml", false), PathEntry("shapes.iuml", false),
            PathEntry("notes.md", false), PathEntry("Sequence.puml", false),
        ),
        "common/" to listOf(PathEntry("styles.puml", false), PathEntry("procs.puml", false)),
        "../" to listOf(PathEntry("p", true), PathEntry("shared.puml", false)),
    )
    private val lister = PathLister { from, dir -> if (from == file) tree[dir] else null }

    private fun at(text: String) = CompletionEngine.complete(text, text.length, file = file, paths = lister)

    @Test fun `right after the space it lists the folder, folders first, only PlantUML files`() {
        val r = assertNotNull(at("@startuml\n!include "))
        assertEquals(listOf("common/", "Sequence.puml", "shapes.iuml", "styles.puml"), r.items.map { it.label })
        assertEquals("", r.prefix)
    }

    @Test fun `the name typed after the last slash filters that folder`() {
        val r = assertNotNull(at("!include common/pr"))
        assertEquals(listOf("procs.puml"), r.items.map { it.label })
        assertEquals("pr", r.prefix)
    }

    @Test fun `parent folders resolve`() {
        val labels = assertNotNull(at("!include ../")).items.map { it.label }
        assertEquals(listOf("p/", "shared.puml"), labels)
    }

    @Test fun `initials work on file names`() {
        val labels = assertNotNull(at("!include sp")).items.map { it.label }
        assertTrue("Sequence.puml" in labels, "got $labels")
    }

    @Test fun `applying replaces only the last segment`() {
        val text = "!include common/sty"
        val r = assertNotNull(at(text))
        val a = CompletionEngine.apply(text, r, r.items.first { it.label == "styles.puml" }, text.length)
        assertEquals("!include common/styles.puml", a.text)
    }

    @Test fun `include_once is covered, includeurl and urls are not`() {
        assertNotNull(at("!include_once sty"))
        assertNull(at("!includeurl sty"))
        assertNull(at("!include https://example.com/x"))
    }

    @Test fun `the stdlib bracket still goes to the stdlib`() {
        val labels = assertNotNull(at("!include <C4/C4_Con")).items.map { it.label }
        assertTrue(labels.all { it.startsWith("C4/") }, "got $labels")
    }

    @Test fun `an unknown folder or an unsaved text offers nothing`() {
        assertNull(at("!include nope/"))
        assertNull(CompletionEngine.complete("!include ", 9, paths = lister))
    }

    @Test fun `after the path the normal completion takes over`() {
        val r = at("!include styles.puml ")
        assertTrue(r == null || r.items.none { it.label == "styles.puml" })
    }
}
