package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.Taskbar
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * The app logo, read from `src/main/resources/icon.png` (bundled in the jar).
 *
 * The packaged .app gets its Dock/Finder icon from the generated .icns (see build.gradle.kts);
 * this covers everything else: `./gradlew run`, where the Dock would otherwise show the
 * Java/Kotlin icon, and the window icon on Windows/Linux. Missing file -> platform default.
 */
object AppIcon {
    private val image: BufferedImage? by lazy {
        AppIcon::class.java.getResourceAsStream("/icon.png")?.use { runCatching { ImageIO.read(it) }.getOrNull() }
    }

    /** Window icon (title bar / taskbar on Windows and Linux); null keeps the default. */
    val painter: Painter? by lazy { image?.let { BitmapPainter(it.toComposeImageBitmap()) } }

    /** Replace the Dock / taskbar icon of the running process. Call once at startup. */
    fun installInDock() {
        val img = image ?: return
        runCatching {
            if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                Taskbar.getTaskbar().iconImage = img
            }
        }
    }
}
