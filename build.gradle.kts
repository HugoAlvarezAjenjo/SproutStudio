import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

group = "es.hugoalvarezajenjo"

// App version. The release workflow passes it from the git tag (v1.2.0 -> -PappVersion=1.2.0);
// local builds fall back to 1.0.0. macOS requires MAJOR.MINOR.PATCH with MAJOR > 0.
val appVersion = (findProperty("appVersion") as String?) ?: "1.0.0"
require(Regex("""[1-9]\d*\.\d+\.\d+""").matches(appVersion)) {
    "appVersion must be MAJOR.MINOR.PATCH with MAJOR > 0 (macOS rule), got '$appVersion'"
}
version = appVersion

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
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4:1.12.1")
}

tasks.test {
    useJUnitPlatform()
}

// Finder litter in src/main/resources must not end up inside the app jar.
tasks.processResources { exclude("**/.DS_Store") }

// ---- App icon -------------------------------------------------------------------------------
// Single source of truth: src/main/resources/icon.png (square, ideally 1024x1024).
// At runtime it is the Dock/window icon; for the packaged .app we turn it into an .icns
// with the macOS built-ins `sips` + `iconutil` (no download, no extra tool).
val iconPng = layout.projectDirectory.file("src/main/resources/icon.png")
val iconIcns = layout.buildDirectory.file("generated/icon/SproutStudio.icns")

val generateMacIcon by tasks.registering {
    description = "Builds SproutStudio.icns from src/main/resources/icon.png"
    val src = iconPng.asFile
    val out = iconIcns.get().asFile
    inputs.file(src).optional()
    outputs.file(out)
    onlyIf { src.exists() }
    doLast {
        fun run(vararg cmd: String) {
            val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
            val log = p.inputStream.bufferedReader().readText()
            check(p.waitFor() == 0) { "${cmd.first()} failed:\n$log" }
        }
        val set = File(temporaryDir, "SproutStudio.iconset").apply { deleteRecursively(); mkdirs() }
        for (size in listOf(16, 32, 128, 256, 512)) {
            for (scale in listOf(1, 2)) {
                val px = size * scale
                val name = if (scale == 1) "icon_${size}x${size}.png" else "icon_${size}x${size}@2x.png"
                run("sips", "-s", "format", "png", "-z", "$px", "$px", src.absolutePath, "--out", File(set, name).absolutePath)
            }
        }
        out.parentFile.mkdirs()
        run("iconutil", "-c", "icns", set.absolutePath, "-o", out.absolutePath)
    }
}

// Every native packaging task needs the .icns first.
tasks.matching { it.name.startsWith("createDistributable") || it.name.startsWith("package") }
    .configureEach { dependsOn(generateMacIcon) }

compose.desktop {
    application {
        mainClass = "es.hugoalvarezajenjo.sproutstudio.MainKt"
        // Skiko loads its native renderer; declare it so newer JDKs do not warn.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "SproutStudio"
            packageVersion = appVersion
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
                // Generated from src/main/resources/icon.png; without it, the default icon.
                if (iconPng.asFile.exists()) iconFile.set(iconIcns)
            }
        }
    }
}
