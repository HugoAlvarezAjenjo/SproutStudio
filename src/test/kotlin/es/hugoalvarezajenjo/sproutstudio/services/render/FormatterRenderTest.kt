package es.hugoalvarezajenjo.sproutstudio.services.render

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.PlantUmlFormatter
import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Templates
import net.sourceforge.plantuml.FileFormat
import net.sourceforge.plantuml.FileFormatOption
import net.sourceforge.plantuml.SourceStringReader
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Formatting only moves whitespace PlantUML ignores: every diagram must draw exactly the same. */
class FormatterRenderTest {

    private fun svg(source: String, index: Int): String {
        val out = ByteArrayOutputStream()
        SourceStringReader(source).outputImage(out, index, FileFormatOption(FileFormat.SVG))
        // The SVG embeds its source; only the drawing counts.
        return out.toString(Charsets.UTF_8)
            .replace(Regex("""<\?plantuml-src[^?]*\?>"""), "")
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
    }

    private fun assertSameDrawing(name: String, source: String) {
        val formatted = PlantUmlFormatter.format(source)
        val blocks = Regex("""^\s*@start""", RegexOption.MULTILINE).findAll(source).count().coerceAtLeast(1)
        for (i in 0 until blocks) {
            assertEquals(svg(source, i), svg(formatted, i), "$name, diagram $i draws differently after formatting:\n$formatted")
        }
    }

    private fun wrap(body: String) = if (body.trimStart().startsWith("@start")) body else "@startuml\n$body\n@enduml\n"

    @Test
    fun templatesDrawTheSame() {
        PlantUmlEngine.configureOffline()
        assertTrue(Templates.all.isNotEmpty())
        for (t in Templates.all) assertSameDrawing(t.name, wrap(t.body.replace("\$0", "")))
    }

    @Test
    fun messyDiagramsDrawTheSame() {
        PlantUmlEngine.configureOffline()
        val sequence = """
            |@startuml
            |      participant "Pizza App" as App
            |Customer->App: order
            |  alt ok
            |App-->Customer: yes
            |      note over App
            |  first
            |      second, indented
            |    end note
            |   else  no
            |App->x Customer: no
            |end
            |   group things
            |  loop 2 times
            |Customer  ->  App : again
            |      end
            |  end
            |box "Inside" #LightBlue
            |participant Kitchen
            |    end box
            |@enduml
        """.trimMargin()
        val activity = """
            |@startuml
            |start
            |    if (Saturday?) then (yes)
            |:Pancakes;
            |      elseif (Sunday?) then (yes)
            |  :Sleep
            |   all day;
            |else (no)
            |:Coffee;
            |    endif
            |  fork
            |:a;
            |   fork again
            |:b;
            |    end fork
            |repeat
            |  :c;
            |      repeat while (more?)
            |  switch (x)
            |case (1)
            |:one;
            |    case (2)
            |:two;
            |endswitch
            |stop
            |@enduml
        """.trimMargin()
        val classes = """
            |@startuml
            |!procedure ${'$'}entity(${'$'}n)
            |class ${'$'}n {
            |+id: Long
            |}
            |  !endprocedure
            |${'$'}entity(Pedido)
            |${'$'}entity(Pago)
            |  package shop {
            |class Pizza {
            |      +size: Size
            |  +    toppings: List<Topping>
            |#  price(): Money
            |+    x
            |-  {static} count
            |  }
            |}
            |Pedido"1"*--"many"Pago
            |Pizza<|--Pedido
            |Pago..>Pizza : uses
            |   legend right
            |  a legend
            |    endlegend
            |@enduml
        """.trimMargin()
        assertSameDrawing("sequence", sequence)
        assertSameDrawing("activity", activity)
        assertSameDrawing("classes", classes)
    }
}
