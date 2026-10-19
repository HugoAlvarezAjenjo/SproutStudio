package es.hugoalvarezajenjo.sproutstudio.services.render

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PlantUmlLanguage
import net.sourceforge.plantuml.SourceStringReader
import java.io.File
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ThemesTest {

    init {
        System.setProperty("java.awt.headless", "true")
    }

    @Test fun `the theme menu lists exactly the themes the bundled PlantUML ships`() {
        val jar = File(SourceStringReader::class.java.protectionDomain.codeSource.location.toURI())
        val shipped = JarFile(jar).use { j ->
            j.entries().asSequence()
                .mapNotNull { Regex("""^themes/puml-theme-(.+)\.puml$""").find(it.name)?.groupValues?.get(1) }
                .filter { it != "_none_" }
                .sorted().toList()
        }
        assertEquals(shipped, PlantUmlLanguage.themes)
    }

    @Test fun `a bundled theme renders offline and changes the picture`() {
        val plain = PlantUmlEngine.renderBlocking("@startuml\nAlice -> Bob: hi\n@enduml\n", null)
        val themed = PlantUmlEngine.renderBlocking("@startuml\n!theme cerulean\nAlice -> Bob: hi\n@enduml\n", null)
        assertNull(themed.error, "unexpected error: ${themed.error}")
        assertFalse(plain.svg!!.contentEquals(themed.svg!!), "the theme made no difference")
    }
}
