package es.hugoalvarezajenjo.sproutstudio.features.editor

import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.TestGraph
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Go to Declaration, Find Usages and Back / Forward on a document (no UI). */
class NavigationActionsTest {

    private val text = "@startuml\nactor User\nparticipant App\nUser -> App\nApp -> User\nUser -> App\n@enduml\n"

    private fun doc(caretAt: Int, t: String = text, file: File? = null) =
        TestGraph.document(file, t).also { it.value = it.value.copy(selection = TextRange(caretAt)) }

    @Test
    fun goToDeclarationSelectsTheNameAndBackReturns() {
        val ref = text.indexOf("User -> App\nApp") + 1
        val d = doc(ref)
        assertNull(d.goToDeclaration())
        val decl = text.indexOf("User")
        assertEquals(TextRange(decl, decl + 4), d.value.selection)
        assertTrue(d.nav.canGoBack)

        assertTrue(d.navigateBack())
        assertEquals(TextRange(ref), d.value.selection)
        assertFalse(d.nav.canGoBack)
        assertTrue(d.navigateForward())
        assertEquals(TextRange(decl), d.value.selection)
    }

    @Test
    fun onTheDeclarationItListsTheUsagesAndEnterGoesToTheSelectedOne() {
        val decl = text.indexOf("App")
        val d = doc(decl + 1)
        d.goToDeclaration()
        val u = assertNotNull(d.nav.usages)
        assertEquals("App", u.symbol)
        assertEquals(listOf(4, 5, 6), u.items.map { it.line })

        d.nav.moveSelection(1)
        d.nav.moveSelection(-2) // wraps to the last row
        d.openSelectedUsage()
        assertNull(d.nav.usages)
        val last = text.lastIndexOf("App")
        assertEquals(TextRange(last, last + 3), d.value.selection)
    }

    @Test
    fun findUsagesFromAReferenceIncludesTheDeclaration() {
        val d = doc(text.indexOf("App ->") + 1)
        assertTrue(d.findUsages())
        assertEquals(listOf(3, 4, 6), assertNotNull(d.nav.usages).items.map { it.line })
        assertFalse(doc(0).findUsages(), "@startuml is no symbol")
    }

    @Test
    fun anIncludeNamesTheFileToOpenWhenItExists() {
        val dir = Files.createTempDirectory("nav").toFile()
        try {
            File(dir, "parts").mkdirs()
            val style = File(dir, "parts/style.puml").apply { writeText("skinparam monochrome true\n") }
            val t = "!include parts/style.puml\n!include parts/missing.puml\n"
            val main = File(dir, "main.puml")
            assertEquals(style.canonicalFile, doc(t.indexOf("style"), t, main).goToDeclaration()?.canonicalFile)
            assertNull(doc(t.indexOf("missing"), t, main).goToDeclaration())
            assertNull(doc(t.indexOf("style"), t, null).goToDeclaration(), "an unsaved diagram has no folder")
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun aNewJumpDropsTheForwardTrail() {
        val d = doc(text.indexOf("User -> App\nApp") + 1)
        d.goToDeclaration()
        d.navigateBack()
        d.value = d.value.copy(selection = TextRange(text.lastIndexOf("App") + 1))
        d.goToDeclaration()
        assertFalse(d.nav.canGoForward)
    }
}
