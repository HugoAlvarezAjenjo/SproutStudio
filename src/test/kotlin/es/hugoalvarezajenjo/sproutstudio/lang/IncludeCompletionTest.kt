package es.hugoalvarezajenjo.sproutstudio.lang

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Completion inside `!include <…`: the bundled C4 / AWS / Azure libraries. */
class IncludeCompletionTest {

    private fun at(text: String) = CompletionEngine.complete(text, text.length)

    @Test fun `opening angle bracket offers the stdlib right away`() {
        val r = assertNotNull(at("@startuml\n!include <"))
        val labels = r.items.map { it.label }
        assertTrue("C4/C4_Container" in labels && "awslib14/AWSCommon" in labels && "azure/AzureCommon" in labels, "got $labels")
    }

    @Test fun `typing a path with slashes narrows it down`() {
        val r = assertNotNull(at("!include <awslib14/Comp"))
        assertTrue(r.items.isNotEmpty())
        assertTrue(r.items.all { it.label.startsWith("awslib14/Compute") }, "got ${r.items.map { it.label }}")
        assertEquals("awslib14/Comp", r.prefix)
    }

    @Test fun `contains match works too`() {
        val labels = assertNotNull(at("!include <lambda")).items.map { it.label }
        assertEquals(listOf("awslib14/Compute/Lambda"), labels)
    }

    @Test fun `applying replaces the typed path and closes the bracket`() {
        val text = "@startuml\n!include <C4/C4_Con"
        val r = assertNotNull(CompletionEngine.complete(text, text.length))
        val item = r.items.first { it.label == "C4/C4_Container" }
        val a = CompletionEngine.apply(text, r, item, text.length)
        assertEquals("@startuml\n!include <C4/C4_Container>", a.text)
        assertEquals(a.text.length, a.caret)
    }

    @Test fun `an existing closing bracket is not doubled`() {
        val text = "!include <C4/C4_Con>\n"
        val caret = text.indexOf('>')
        val r = assertNotNull(CompletionEngine.complete(text, caret))
        val a = CompletionEngine.apply(text, r, r.items.first { it.label == "C4/C4_Container" }, caret)
        assertEquals("!include <C4/C4_Container>\n", a.text)
    }

    @Test fun `other libraries are offered by name`() {
        val labels = assertNotNull(at("!include <kub")).items.map { it.label }
        assertTrue("kubernetes/" in labels, "got $labels")
    }

    @Test fun `a local include is left alone`() {
        assertNull(CompletionEngine.complete("!include common/sty", "!include common/sty".length)?.items?.firstOrNull { it.label.startsWith("C4/") })
    }
}
