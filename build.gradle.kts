import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

group = "es.hugoalvarezajenjo"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    // Vector icons (compiled into the app, no runtime download).
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

    // PlantUML engine, MIT-licensed build. Includes the Smetana layout engine,
    // so no Graphviz / dot binary is needed. Rendering is 100% in-process.
    implementation("net.sourceforge.plantuml:plantuml-mit:1.2026.8")

    testImplementation(kotlin("test"))
    testImplementation(compose.desktop.uiTestJUnit4)
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "es.hugoalvarezajenjo.sproutstudio.MainKt"
        // Skiko loads its native renderer; declare it so newer JDKs do not warn.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "SproutStudio"
            packageVersion = "1.0.0"
            description = "A friendly, offline PlantUML studio"
            vendor = "Hugo Alvarez Ajenjo"

            // java.desktop for AWT/Desktop (open-file events), java.xml for SVG,
            // java.logging + jdk.unsupported used by PlantUML / Skiko.
            modules("java.desktop", "java.xml", "java.logging", "java.naming", "jdk.unsupported")

            fileAssociation(
                mimeType = "text/x-plantuml",
                extension = "puml",
                description = "PlantUML diagram",
            )
            fileAssociation(
                mimeType = "text/x-plantuml",
                extension = "plantuml",
                description = "PlantUML diagram",
            )
            fileAssociation(
                mimeType = "text/x-plantuml",
                extension = "pu",
                description = "PlantUML diagram",
            )

            macOS {
                bundleID = "es.hugoalvarezajenjo.sproutstudio"
                appCategory = "public.app-category.developer-tools"
            }
        }
    }
}
