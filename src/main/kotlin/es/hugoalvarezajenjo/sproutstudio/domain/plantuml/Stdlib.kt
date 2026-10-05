package es.hugoalvarezajenjo.sproutstudio.domain.plantuml

/**
 * PlantUML's standard library, which ships inside the app (the PlantUML jar carries it), so
 * `!include <C4/C4_Container>` or `!include <awslib14/Compute/Lambda>` work fully offline.
 *
 * PlantUML has no public API to list the files of a library, so the paths offered after
 * `!include <` are a curated list of the most used ones. Every entry is checked against the
 * real library by StdlibTest, so a typo or a renamed file fails the build instead of the user.
 */
object Stdlib {

    data class Entry(val path: String, val detail: String)

    /** Every library in the bundled stdlib, read from PlantUML itself; empty if it can't be read. */
    val libraries: List<String> by lazy {
        runCatching { net.sourceforge.plantuml.preproc.Stdlib.getAllFolderNames().sorted() }.getOrDefault(emptyList())
    }

    val includes: List<Entry> = buildList {
        // C4 model (C4-PlantUML). Each level includes the ones above it.
        add(Entry("C4/C4_Context", "C4 · people & systems"))
        add(Entry("C4/C4_Container", "C4 · containers"))
        add(Entry("C4/C4_Component", "C4 · components"))
        add(Entry("C4/C4_Dynamic", "C4 · numbered interactions"))
        add(Entry("C4/C4_Deployment", "C4 · deployment nodes"))
        add(Entry("C4/C4_Sequence", "C4 · sequence"))

        // AWS architecture icons (aws-icons-for-plantuml v14). Include AWSCommon first.
        add(Entry("awslib14/AWSCommon", "AWS · required first"))
        add(Entry("awslib14/AWSSimplified", "AWS · simpler look"))
        listOf(
            "Compute/Lambda", "Compute/EC2",
            "Containers/ElasticContainerService", "Containers/ElasticKubernetesService", "Containers/Fargate",
            "Database/DynamoDB", "Database/RDS", "Database/Aurora",
            "Storage/SimpleStorageService",
            "ApplicationIntegration/APIGateway", "ApplicationIntegration/SimpleQueueService",
            "ApplicationIntegration/SimpleNotificationService", "ApplicationIntegration/EventBridge",
            "NetworkingContentDelivery/CloudFront",
            "SecurityIdentityCompliance/Cognito",
            "General/Users", "General/Client",
            "Groups/AWSCloud", "Groups/VPC",
        ).forEach { add(Entry("awslib14/$it", "AWS · ${it.substringAfterLast('/')}")) }

        // Azure icons (Azure-PlantUML). Include AzureCommon first.
        add(Entry("azure/AzureCommon", "Azure · required first"))
        add(Entry("azure/AzureSimplified", "Azure · simpler look"))
        listOf(
            "Compute/AzureFunction", "Compute/AzureVirtualMachine", "Compute/AzureAppService",
            "Containers/AzureKubernetesService",
            "Databases/AzureCosmosDb", "Databases/AzureSqlDatabase",
            "Storage/AzureBlobStorage",
            "Integration/AzureServiceBus", "Analytics/AzureEventHub",
            "Networking/AzureApplicationGateway",
            "Web/AzureWebApp",
            "Identity/AzureActiveDirectory",
        ).forEach { add(Entry("azure/$it", "Azure · ${it.substringAfterLast('/').removePrefix("Azure")}")) }
    }
}
