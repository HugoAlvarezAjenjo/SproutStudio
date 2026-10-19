package es.hugoalvarezajenjo.sproutstudio.services.render

import es.hugoalvarezajenjo.sproutstudio.domain.plantuml.DiagramBlocks
import net.sourceforge.plantuml.SourceStringReader
import kotlin.test.Test
import kotlin.test.assertEquals

/** The preview numbers diagrams with PlantUML's own block list; [DiagramBlocks] must agree with it. */
class DiagramBlocksAgreeWithPlantUmlTest {

    private val cases = listOf(
        "@startuml\nA -> B\n@enduml\n@startmindmap\n* a\n@endmindmap\n",
        "  @startuml\nA -> B\n  @enduml\n\t@startuml\nC -> D\n\t@enduml\n",
        "@startuml\nA -> B\n@startuml\nC -> D\n@enduml\n",
        "@startuml\nA -> B\n@endmindmap\n@startuml\nC -> D\n@enduml\n",
        "@StartUml\nA -> B\n@EndUml\n",
        "@start\nA -> B\n@end\n",
        "@startuml\nA -> B\n",
        "@startuml\nA -> B\n@enduml\n@startuml\nC -> D\n",
        "/'\n@startuml\nX -> Y\n@enduml\n'/\n@startuml\nA -> B\n@enduml\n",
        "@startuml\nA -> B\n' @startuml\n@enduml\n",
    )

    @Test fun `counts the same blocks as PlantUML`() {
        for (text in cases) {
            assertEquals(SourceStringReader(text).blocks.size, DiagramBlocks.of(text).size, text)
        }
    }
}
