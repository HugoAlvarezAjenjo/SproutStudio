import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

plugins {
    kotlin("jvm") version "2.4.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

group = "es.hugoalvarezajenjo"

// App version. The release workflow passes it from the git tag (v1.2.0 -> -PappVersion=1.2.0);
// local builds fall back to the current release. macOS requires MAJOR.MINOR.PATCH with MAJOR > 0.
val appVersion = (findProperty("appVersion") as String?) ?: "1.3.0"
require(Regex("""[1-9]\d*\.\d+\.\d+""").matches(appVersion)) {
    "appVersion must be MAJOR.MINOR.PATCH with MAJOR > 0 (macOS rule), got '$appVersion'"
}
version = appVersion

// The installers' names. On Linux, jpackage names the menu entry "<debian name>-<app name>.desktop",
// and GNOME/KDE match a window to that entry (dock icon, grouping) by its WM_CLASS. The app gets
// the same id at run time (see linuxDesktopId below), so the two cannot drift apart.
val appName = "SproutStudio"
val linuxPackageName = "sproutstudio" // Debian package names are lowercase
val linuxDesktopId = "$linuxPackageName-$appName"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    // Dispatchers.Main = the Swing UI thread, for ViewModel scopes (same version Compose uses).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    // Vector icons (compiled into the app, no runtime download).
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")
    // Translated texts: src/main/composeResources/values[-es]/strings.xml -> generated Res.string.*
    implementation("org.jetbrains.compose.components:components-resources:1.12.1")

    // PlantUML engine, MIT-licensed build. Includes the Smetana layout engine,
    // so no Graphviz / dot binary is needed. Rendering is 100% in-process.
    implementation("net.sourceforge.plantuml:plantuml-mit:1.2026.8")
    // PDF export: PlantUML draws PDFs itself through OpenPDF (vector, real text). Without it
    // FileFormat.PDF fails with NoClassDefFoundError. LGPL-2.1 / MPL-2.0, ~1.5 MB.
    implementation("com.github.librepdf:openpdf:3.0.5") {
        // Only used when *reading* Brotli-compressed PDFs; writing never touches it, and it
        // drags in JNI natives.
        exclude(group = "com.aayushatharva.brotli4j")
    }

    // Git, in-process (pure Java, EDL licence): no `git` binary needed on the user's machine.
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.8.0.202609011348-r")
    // JGit logs through SLF4J; without a provider it prints a warning on every launch.
    implementation("org.slf4j:slf4j-nop:2.0.17")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4:1.12.1")
    // Virtual time for ViewModel and service tests (same version ui-test already brings in).
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

tasks.test {
    useJUnitPlatform()
    // Tests never read or write the user's real preferences (window layout, recents...).
    systemProperty("sproutstudio.settings", "memory")
    // Tests assert English texts, whatever language the machine running them uses.
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    // A failure prints its whole stack (causes and suppressed exceptions too) in the log, so a CI
    // failure can be read there instead of downloading the report.
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }
}

// Finder litter in src/main/resources must not end up inside the app jar.
tasks.processResources { exclude("**/.DS_Store") }

// The app reads its own version at runtime (welcome screen) from a generated resource, so it is
// always the one being built: -PappVersion in releases, the fallback above locally.
val generateBuildInfo = tasks.register("generateBuildInfo") {
    description = "Writes sproutstudio/build-info.properties with the app version"
    val out = layout.buildDirectory.dir("generated/buildinfo")
    val version = appVersion
    inputs.property("version", version)
    outputs.dir(out)
    doLast {
        val file = out.get().file("sproutstudio/build-info.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$version\n")
    }
}
sourceSets.main { resources.srcDir(generateBuildInfo) }

// ---- App icon -------------------------------------------------------------------------------
// Single source of truth: src/main/resources/icon.png (square, ideally 1024x1024).
// At runtime it is the Dock/window icon. For the packaged app we turn it into an .icns (macOS,
// with the built-ins `sips` + `iconutil`) or an .ico (Windows, plain Java ImageIO), so no
// download and no extra tool. Each is built only on its own OS. Linux uses the PNG directly.
val iconPng = layout.projectDirectory.file("src/main/resources/icon.png")
val iconIcns = layout.buildDirectory.file("generated/icon/SproutStudio.icns")
val iconIco = layout.buildDirectory.file("generated/icon/SproutStudio.ico")
val hostOs: String = System.getProperty("os.name").lowercase()
val onMac = "mac" in hostOs
val onWindows = "windows" in hostOs

val generateMacIcon = tasks.register("generateMacIcon") {
    description = "Builds SproutStudio.icns from src/main/resources/icon.png"
    val src = iconPng.asFile
    val out = iconIcns.get().asFile
    inputs.file(src).optional()
    outputs.file(out)
    onlyIf { src.exists() && onMac }
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

val generateWindowsIcon = tasks.register("generateWindowsIcon") {
    description = "Builds SproutStudio.ico from src/main/resources/icon.png"
    val src = iconPng.asFile
    val out = iconIco.get().asFile
    inputs.file(src).optional()
    outputs.file(out)
    onlyIf { src.exists() && onWindows }
    doLast {
        // An .ico is a directory of images; since Windows Vista each entry may be a whole PNG.
        val original = ImageIO.read(src) ?: error("${src.name} is not a readable image")
        val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
        val pngs = sizes.map { px ->
            val img = BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB)
            img.createGraphics().apply {
                setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
                setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
                drawImage(original, 0, 0, px, px, null)
                dispose()
            }
            ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
        }
        val header = ByteBuffer.allocate(6 + 16 * sizes.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(sizes.size.toShort()) // reserved, type 1 = icon, count
        var offset = header.capacity()
        sizes.zip(pngs).forEach { (px, png) ->
            val dim = if (px >= 256) 0 else px // 0 means 256
            header.put(dim.toByte()).put(dim.toByte()).put(0).put(0) // width, height, palette, reserved
            header.putShort(1).putShort(32).putInt(png.size).putInt(offset) // planes, bits, size, offset
            offset += png.size
        }
        out.parentFile.mkdirs()
        out.outputStream().use { o -> o.write(header.array()); pngs.forEach(o::write) }
    }
}

// Every native packaging task needs the icon for its OS first.
tasks.matching { it.name.startsWith("createDistributable") || it.name.startsWith("package") }
    .configureEach { dependsOn(generateMacIcon, generateWindowsIcon) }

compose.resources {
    packageOfResClass = "es.hugoalvarezajenjo.sproutstudio.resources"
    publicResClass = false
}

compose.desktop {
    application {
        mainClass = "es.hugoalvarezajenjo.sproutstudio.app.MainKt"
        // Skiko loads its native renderer; declare it so newer JDKs do not warn.
        jvmArgs += "--enable-native-access=ALL-UNNAMED"
        // Linux: the window class must match the menu entry (LinuxPlatform.integrate). Each
        // installer is built on its own OS, so only the Linux one carries these; elsewhere the
        // --add-opens would warn that sun.awt.X11 does not exist.
        if (System.getProperty("os.name").startsWith("Linux")) {
            jvmArgs += "-Dsproutstudio.desktopId=$linuxDesktopId"
            jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED" // no public API sets WM_CLASS
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = appName
            packageVersion = appVersion
            description = "PlantUML IDE"
            vendor = "Hugo Alvarez Ajenjo"

            // java.desktop for AWT/Desktop (open-file events), java.xml for SVG,
            // java.logging + jdk.unsupported used by PlantUML / Skiko; java.management,
            // java.security.jgss and java.sql by JGit (found with jdeps).
            modules(
                "java.desktop", "java.xml", "java.logging", "java.naming", "jdk.unsupported",
                "java.management", "java.security.jgss", "java.sql",
            )

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

            windows {
                // Fixed for good: it is how Windows knows a new .msi upgrades the installed app.
                upgradeUuid = "6f1c2b0e-8d4a-4b7e-9a35-2c1e5f7d9b40"
                menuGroup = "SproutStudio"
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true // no administrator rights needed
                if (iconPng.asFile.exists()) iconFile.set(iconIco)
            }

            linux {
                // Debian package names are lowercase; the app and its menu entry keep "SproutStudio".
                packageName = linuxPackageName
                debMaintainer = "Hugo Alvarez Ajenjo <hugo.alvarezajenjo@gmail.com>"
                appCategory = "devel" // Debian section
                menuGroup = "Development" // freedesktop menu category
                shortcut = true
                // Linux takes the PNG as it is.
                if (iconPng.asFile.exists()) iconFile.set(iconPng)
            }
        }
    }
}
