package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SymbolRenameTest {

    /** Files by path, as an in-memory [IncludeReader] (paths resolve as written, ignoring the includer). */
    private fun reader(vararg files: Pair<String, String>) = IncludeReader { _, path ->
        files.toMap()[path]?.let { SourceFile(path, it) }
    }

    /** Rename the symbol at the first [at] of [text] to [to], in [text] only. */
    private fun renamed(text: String, at: String, to: String): String {
        val r = assertNotNull(SymbolRename.at(text, text.indexOf(at) + 1))
        return SymbolRename.apply(TextFieldValue(text), r.edits.single().ranges, to).text
    }

    @Test
    fun renamesEveryUsageInItsDiagramButNotLabelsCommentsOrOtherDiagrams() {
        val text = "@startuml\nparticipant App\nUser -> App : App starts\n' App\nApp --> User\n@enduml\n@startuml\nApp -> X\n@enduml\n"
        val expected = "@startuml\nparticipant Api\nUser -> Api : App starts\n' App\nApi --> User\n@enduml\n@startuml\nApp -> X\n@enduml\n"
        assertEquals(expected, renamed(text, "App", "Api"), "from the declaration")
        assertEquals(expected, renamed(text, "App :", "Api"), "from a usage")
    }

    @Test
    fun anAliasIsRenamedAndTheQuotedDisplayNameIsLeftAlone() {
        val text = "participant \"Long Name\" as L\nL -> B : hi\n"
        assertEquals("participant \"Long Name\" as Svc\nSvc -> B : hi\n", renamed(text, "L ->", "Svc"))
    }

    @Test
    fun aNameOnlyEverWrittenInQuotesMayGetSpacesAndABareOneMayNot() {
        val quoted = "participant \"Long Name\"\n\"Long Name\" -> B\n"
        val q = assertNotNull(SymbolRename.at(quoted, quoted.indexOf("Long") + 1))
        assertEquals(2, q.usages)
        assertNull(q.problem("Other Name"))
        assertEquals(RenameProblem.INVALID, q.problem("Say \"hi\""))
        assertEquals("participant \"Other Name\"\n\"Other Name\" -> B\n", SymbolRename.apply(TextFieldValue(quoted), q.edits.single().ranges, "Other Name").text)

        val bare = "actor Bob\nBob -> A\n"
        assertEquals(RenameProblem.INVALID, SymbolRename.at(bare, 7)!!.problem("Bob Smith"))
    }

    @Test
    fun newNamesThatWouldChangeTheDiagramAreRefused() {
        val text = "participant App\nparticipant Db\n!\$color = \"red\"\nApp -> Db\n"
        val app = assertNotNull(SymbolRename.at(text, 13))
        assertEquals(RenameProblem.BLANK, app.problem(" "))
        assertEquals(RenameProblem.UNCHANGED, app.problem("App"))
        assertEquals(RenameProblem.TAKEN, app.problem("Db"))
        assertEquals(RenameProblem.KEYWORD, app.problem("participant"))
        assertEquals(RenameProblem.UNEXPECTED_DOLLAR, app.problem("\$app"))
        assertEquals(RenameProblem.INVALID, app.problem("9lives"))
        assertNull(app.problem("Api.v2"))

        val color = assertNotNull(SymbolRename.at(text, text.indexOf("\$color") + 1))
        assertEquals(RenameProblem.MISSING_DOLLAR, color.problem("color"))
        assertNull(color.problem("\$tint"))
    }

    @Test
    fun theCaretAndSelectionStayOnTheSameCode() {
        val text = "participant App\nApp -> B\nB -> App\n"
        val r = SymbolRename.at(text, 13)!!.edits.single().ranges
        // Caret inside the first App (after "Ap"), a selection over "B -> " on the last line.
        val inName = SymbolRename.apply(TextFieldValue(text, TextRange(14)), r, "Service")
        assertEquals(14, inName.selection.start)
        val last = text.lastIndexOf("B ->")
        val sel = SymbolRename.apply(TextFieldValue(text, TextRange(last, last + 5)), r, "Service")
        assertEquals("B -> ", sel.text.substring(sel.selection.start, sel.selection.end))
        val atEnd = SymbolRename.apply(TextFieldValue(text, TextRange(15)), r, "S")
        assertEquals(13, atEnd.selection.start, "right after the name: right after the new one")
    }

    @Test
    fun aProcedureIsRenamedInEveryIncludedFileThatUsesIt() {
        val main = "!include lib.puml\n\$call(A, B)\nA -> B : \$call(x) and call\n"
        val r = reader(
            "lib.puml" to "!include deep.puml\n!procedure \$call(\$a, \$b)\n\$a -> \$b\n!endprocedure\n",
            "deep.puml" to "\$call(C, D)\n",
            "unrelated.puml" to "\$call(E, F)\n",
        )
        val plan = assertNotNull(SymbolRename.at(main, main.indexOf("\$call") + 1, "/p/main.puml", r))
        assertEquals(listOf(null, "lib.puml", "deep.puml"), plan.edits.map { it.path })
        assertEquals(4, plan.usages)
        assertEquals(3, plan.files)
        val edited = plan.edits.associate { e -> e.path to SymbolRename.apply(TextFieldValue(e.text), e.ranges, "\$send").text }
        assertEquals("!include lib.puml\n\$send(A, B)\nA -> B : \$send(x) and call\n", edited[null])
        assertEquals("!include deep.puml\n!procedure \$send(\$a, \$b)\n\$a -> \$b\n!endprocedure\n", edited["lib.puml"])
        assertEquals("\$send(C, D)\n", edited["deep.puml"])
    }

    /** A shared lib and the project around it, for renames that reach the diagrams including it. */
    private val lib = "!procedure \$call(\$a, \$b)\n\$a -> \$b\n!endprocedure\n"
    private val project = reader(
        "lib.puml" to lib,
        "main.puml" to "!include lib.puml\n!include helper.puml\n\$call(A, B)\n",
        "helper.puml" to "\$call(C, D)\n",
        "other.puml" to "!include main.puml\n\$call(E, F)\n",
        "second.puml" to "!include lib.puml\n\$call(G, H)\n",
        "loose.puml" to "\$call(I, J)\n",
        "unrelated.puml" to "!procedure \$call(\$x)\n!endprocedure\n\$call(K)\n",
    )
    private val diagrams = listOf("helper.puml", "lib.puml", "loose.puml", "main.puml", "other.puml", "second.puml", "unrelated.puml")

    @Test
    fun renamingInASharedFileReachesEveryDiagramThatIncludesItAndWhatTheyInclude() {
        val plan = assertNotNull(SymbolRename.at(lib, lib.indexOf("\$call") + 1, "lib.puml", project, diagrams))
        // helper.puml doesn't include lib.puml, but main.puml includes both: there it sees the procedure.
        // loose.puml and unrelated.puml include neither, so their $call is someone else's.
        assertEquals(listOf(null, "main.puml", "helper.puml", "other.puml", "second.puml"), plan.edits.map { it.path })
        assertEquals(5, plan.usages)
        val edited = plan.edits.associate { e -> e.path to SymbolRename.apply(TextFieldValue(e.text), e.ranges, "\$send").text }
        assertEquals("!include main.puml\n\$send(E, F)\n", edited["other.puml"])
        assertEquals("\$send(C, D)\n", edited["helper.puml"])
    }

    @Test
    fun renamingFromADiagramAlsoReachesTheOtherDiagramsSharingTheDeclaration() {
        val main = "!include lib.puml\n!include helper.puml\n\$call(A, B)\n"
        val plan = assertNotNull(SymbolRename.at(main, main.indexOf("\$call(A") + 1, "main.puml", project, diagrams))
        assertEquals(listOf(null, "lib.puml", "helper.puml", "other.puml", "second.puml"), plan.edits.map { it.path })
        val withoutProject = assertNotNull(SymbolRename.at(main, main.indexOf("\$call(A") + 1, "main.puml", project))
        assertEquals(listOf(null, "lib.puml", "helper.puml"), withoutProject.edits.map { it.path }, "only what it includes")
    }

    @Test
    fun aVariableDeclaredHereIsRenamedWithoutAnyReader() {
        val text = "!\$color = \"red\"\nparticipant A \$color\n"
        assertEquals("!\$tint = \"red\"\nparticipant A \$tint\n", renamed(text, "\$color", "\$tint"))
    }

    @Test
    fun thereIsNothingToRenameOffASymbolOrOnANameDeclaredNowhere() {
        val text = "Container(api)\nA -> B : text\n"
        assertNull(SymbolRename.at(text, text.indexOf("Container") + 1), "used, declared in no file we can read")
        assertNull(SymbolRename.at(text, text.indexOf("text") + 1), "label prose")
    }
}
