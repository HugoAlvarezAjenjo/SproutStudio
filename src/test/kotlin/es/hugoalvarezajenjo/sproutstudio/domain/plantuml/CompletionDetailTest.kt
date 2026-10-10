package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.CompletionDetail.Tag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** The engine says what each suggestion is as a [CompletionDetail]; the view picks the words. */
class CompletionDetailTest {

    private fun item(text: String, label: String, paths: PathLister = PathLister.NONE): Completion =
        assertNotNull(CompletionEngine.complete(text, text.length, force = true, file = "/p/main.puml", paths = paths))
            .items.first { it.label == label }

    @Test fun `fixed kinds are tags`() {
        assertEquals(Tag.THEME, item("!theme ", "cerulean").detail)
        assertEquals(Tag.SKINPARAM, item("skinparam ", "backgroundColor").detail)
        assertEquals(Tag.PREPROCESSOR, item("!inc", "!include").detail)
        val lister = PathLister { _, _ -> listOf(PathEntry("lib", true), PathEntry("a.puml", false)) }
        assertEquals(Tag.FOLDER, item("!include ", "lib/", lister).detail)
        assertEquals(Tag.FILE, item("!include ", "a.puml", lister).detail)
    }

    @Test fun `keywords carry their diagram kind`() {
        val text = "@startuml\nparticipant Alice\npar"
        assertEquals(CompletionDetail.Keyword(DiagramKind.SEQUENCE), item(text, "participant").detail)
    }

    @Test fun `symbols show the declaring keyword as is, and the rest as tags`() {
        val text = "@startuml\n!\$color = \"red\"\nparticipant Alice\n\n@enduml"
        val caret = text.indexOf("\n@enduml")
        val items = assertNotNull(CompletionEngine.complete(text, caret, force = true)).items
        assertEquals(CompletionDetail.Verbatim("participant"), items.first { it.label == "Alice" }.detail)
        assertEquals(Tag.VARIABLE, items.first { it.label == "\$color" }.detail)
    }
}
