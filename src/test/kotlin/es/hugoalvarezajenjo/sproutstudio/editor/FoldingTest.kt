package es.hugoalvarezajenjo.sproutstudio.editor

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import es.hugoalvarezajenjo.sproutstudio.model.Document
import es.hugoalvarezajenjo.sproutstudio.ui.PumlTheme
import es.hugoalvarezajenjo.sproutstudio.ui.ide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class FoldingTest {
    private val seq = """
        @startuml
        actor User
        alt ok
          User -> App: hi
          App --> User: hey
        else no
          User -> App: bye
        end
        class Pizza {
          +size: Size
          +price(): Money
        }
        note left
          a note
        end note
        @enduml
    """.trimIndent()

    private fun kinds(text: String) = Folding.regions(text).map { it.kind to (it.startLine to it.endLine) }

    @Test
    fun findsDiagramBracesBlocksAndNotes() {
        val k = kinds(seq)
        assertTrue(FoldRegion.Kind.DIAGRAM to (0 to 15) in k)
        assertTrue(FoldRegion.Kind.BLOCK to (2 to 7) in k)
        assertTrue(FoldRegion.Kind.BRACES to (8 to 11) in k)
        assertTrue(FoldRegion.Kind.NOTE to (12 to 14) in k)
    }

    @Test
    fun activityIfAndCommentsFold() {
        val act = "@startuml\nif (a?) then\n  :x;\nelse\n  :y;\nendif\n/'\n multi\n line\n'/\n@enduml"
        val k = kinds(act)
        assertTrue(FoldRegion.Kind.BLOCK to (1 to 5) in k)
        assertTrue(FoldRegion.Kind.COMMENT to (6 to 9) in k)
    }

    @Test
    fun oneLinersAndEmptyBlocksAreNotFoldable() {
        val k = kinds("@startuml\nnote left: hi\nclass A {\n}\n@enduml")
        assertEquals(listOf(FoldRegion.Kind.DIAGRAM to (0 to 4)), k)
    }

    @Test
    fun strayClosersDoNotBreakOtherRegions() {
        val k = kinds("@startuml\nend\n}\nalt x\n  a -> b\nend\n@enduml")
        assertTrue(FoldRegion.Kind.BLOCK to (3 to 5) in k)
        assertTrue(FoldRegion.Kind.DIAGRAM to (0 to 6) in k)
    }

    @Test
    fun foldedViewKeepsOpenerAndCloserOnOneLine() {
        val r = Folding.regions(seq).first { it.kind == FoldRegion.Kind.BRACES }
        val (view, map) = Folding.transform(AnnotatedString(seq), listOf(r), SpanStyle())
        assertTrue("class Pizza { … }\n" in view.text, view.text)
        // Round trip outside the fold, and hidden offsets collapse onto the placeholder.
        assertEquals(r.hideStart, map.transformedToOriginal(map.originalToTransformed(r.hideStart)))
        assertEquals(r.hideEnd, map.transformedToOriginal(map.originalToTransformed(r.hideEnd)))
        assertEquals(seq.length, map.transformedToOriginal(view.length))
        assertEquals(view.length, map.originalToTransformed(seq.length))
    }

    @Test
    fun foldsFollowEditsAboveAndAreDroppedWhenTheirLineIsEdited() {
        val d = Document(null, seq)
        val r = d.folds.regions(d.text).first { it.kind == FoldRegion.Kind.BRACES }
        d.foldRegion(r)
        d.value = d.value.copy(text = "' top\n" + d.text, selection = TextRange(0))
        assertEquals(1, d.folds.active(d.text).size)
        assertEquals(r.hideStart + 6, d.folds.active(d.text).single().hideStart)
        // Typing on the opening line keeps the fold, still anchored at that line's end.
        val at = d.text.indexOf("Pizza {") + 5
        d.value = d.value.copy(text = d.text.substring(0, at) + "X" + d.text.substring(at), selection = TextRange(at + 1))
        assertEquals(d.text.indexOf("PizzaX {") + 8, d.folds.active(d.text).single().hideStart)
        // Deleting the opener's line break (the fold's anchor) forgets it rather than mis-anchoring.
        val nl = d.folds.active(d.text).single().hideStart
        d.value = d.value.copy(text = d.text.removeRange(nl, nl + 1), selection = TextRange(nl))
        assertTrue(d.folds.active(d.text).isEmpty())
    }

    @Test
    fun caretLandingInsideAFoldExpandsIt() {
        val d = Document(null, seq)
        val r = d.folds.regions(d.text).first { it.kind == FoldRegion.Kind.BRACES }
        d.foldRegion(r)
        d.value = d.value.copy(selection = TextRange(d.text.indexOf("+price")))
        assertFalse(d.folds.isFolded(r))
    }

    @Test
    fun foldingParksAHiddenCaretOnTheOpener() {
        val d = Document(null, seq)
        d.value = d.value.copy(selection = TextRange(d.text.indexOf("+size")))
        assertTrue(d.foldAtCaret())
        val r = d.folds.active(d.text).single()
        assertEquals(FoldRegion.Kind.BRACES, r.kind)
        assertEquals(r.hideStart, d.value.selection.start)
        assertTrue(d.unfoldAtCaret())
        assertTrue(d.folds.active(d.text).isEmpty())
    }

    @Test
    fun foldAllThenUnfoldAll() {
        val d = Document(null, seq)
        d.foldAll()
        assertEquals(Folding.regions(seq).size, d.folds.active(d.text).size)
        d.unfoldAll()
        assertTrue(d.folds.active(d.text).isEmpty())
    }

    @Test
    fun typingInTheEditorWithFoldsDoesNotCrash() {
        val d = Document(null, seq)
        d.folds.set(Folding.regions(seq).filter { it.kind != FoldRegion.Kind.DIAGRAM })
        d.value = d.value.copy(selection = TextRange(seq.length))
        runComposeUiTest {
            setContent { PumlTheme(dark = true) { Surface(color = ide.panel) { CodeEditor(d, errorLine = 5) } } }
            waitForIdle()
            val field = onNode(hasSetTextAction())
            for (ch in "\n' x") { field.performTextInput(ch.toString()); waitForIdle() }
        }
        assertTrue(d.text.endsWith("@enduml\n' x"), d.text)
        assertEquals(3, d.folds.active(d.text).size)
    }
}
