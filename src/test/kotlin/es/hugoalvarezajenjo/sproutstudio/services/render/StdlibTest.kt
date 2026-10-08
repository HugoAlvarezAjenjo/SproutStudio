package es.hugoalvarezajenjo.sproutstudio.services.render

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The bundled PlantUML stdlib: C4 and the AWS / Azure sprites work offline via `!include <...>`. */
class StdlibTest {

    init { System.setProperty("java.awt.headless", "true") }

    private fun renders(src: String, vararg mustContain: String) {
        val r = PlantUmlEngine.renderBlocking(src, null)
        assertNull(r.error, "unexpected error: ${r.error}")
        // PlantUML's SVG puts each word in its own <text>, so check single words.
        val svg = String(r.svg!!)
        for (s in mustContain) assertTrue(svg.contains(s), "'$s' missing from the rendered diagram")
    }

    @Test fun `C4 container diagram`() = renders(
        """
        @startuml
        !include <C4/C4_Container>
        Person(user, "Customer")
        System_Boundary(shop, "Pizza Shop") {
          Container(app, "Web App", "Kotlin", "Takes orders")
          ContainerDb(db, "Orders DB", "PostgreSQL")
        }
        Rel(user, app, "Orders pizza")
        Rel(app, db, "Stores orders")
        @enduml
        """.trimIndent(),
        "Customer", "Web", "PostgreSQL", "pizza", "«container»",
    )

    @Test fun `AWS architecture icons`() = renders(
        """
        @startuml
        !include <awslib14/AWSCommon>
        !include <awslib14/Compute/Lambda>
        !include <awslib14/Database/DynamoDB>
        Lambda(fn, "Order handler", "")
        DynamoDB(table, "Orders", "")
        fn --> table
        @enduml
        """.trimIndent(),
        "handler", "«Lambda»", "«DynamoDB»",
    )

    @Test fun `Azure icons`() = renders(
        """
        @startuml
        !include <azure/AzureCommon>
        !include <azure/Compute/AzureFunction>
        AzureFunction(fn, "Order handler", "")
        @enduml
        """.trimIndent(),
        "handler", "«AzureFunction»",
    )

    @Test fun `every include offered by completion really exists`() {
        val bad = es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Stdlib.includes.mapNotNull { e ->
            val common = when {
                e.path.startsWith("awslib14/") && !e.path.endsWith("Common") -> "!include <awslib14/AWSCommon>\n"
                e.path.startsWith("azure/") && !e.path.endsWith("Common") -> "!include <azure/AzureCommon>\n"
                else -> ""
            }
            val r = PlantUmlEngine.renderBlocking("@startuml\n$common!include <${e.path}>\nrectangle x\n@enduml\n", null)
            r.error?.let { "${e.path}: ${it.message}" }
        }
        assertTrue(bad.isEmpty(), "broken include paths:\n" + bad.joinToString("\n"))
    }

    @Test fun `the library list comes from PlantUML itself`() {
        val libs = es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Stdlib.libraries.map { it.lowercase() }
        assertTrue(listOf("c4", "awslib14", "azure", "gcp", "kubernetes").all { it in libs }, "got $libs")
    }

    @Test fun `every whole template renders without errors`() {
        val bad = es.hugoalvarezajenjo.sproutstudio.domain.plantuml.Templates.wholeDiagrams.mapNotNull { t ->
            PlantUmlEngine.renderBlocking(t.previewSource, null).error?.let { "${t.id}: line ${it.line}: ${it.message}" }
        }
        assertTrue(bad.isEmpty(), "broken templates:\n" + bad.joinToString("\n"))
    }

    @Test fun `stdlib includes are not refused as remote`() {
        assertNull(OfflineGuard.check("!include <C4/C4_Context>"))
        assertNull(OfflineGuard.check("!include <awslib14/AWSCommon>"))
    }
}
