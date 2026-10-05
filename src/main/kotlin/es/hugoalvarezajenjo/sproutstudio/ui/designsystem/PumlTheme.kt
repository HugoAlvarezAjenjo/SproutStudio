package es.hugoalvarezajenjo.sproutstudio.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalIde = staticCompositionLocalOf { DarkIde }

/** Shorthand for the current IDE colours. */
val ide: IdeColors
    @Composable @ReadOnlyComposable get() = LocalIde.current

val EditorFont = FontFamily.Monospace

val editorTextStyle = TextStyle(fontFamily = EditorFont, fontSize = 13.sp, lineHeight = 21.sp)

@Composable
fun PumlTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val c = if (dark) DarkIde else LightIde
    val scheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.selection,
        onPrimaryContainer = c.text,
        background = c.panel,
        onBackground = c.text,
        surface = c.panel,
        onSurface = c.text,
        surfaceVariant = c.hover,
        onSurfaceVariant = c.textMuted,
        surfaceContainer = c.popup,
        surfaceContainerHigh = c.popup,
        outline = c.popupBorder,
        outlineVariant = c.border,
        error = c.error,
        errorContainer = c.errorBg,
        onErrorContainer = c.text,
    )
    // JetBrains UI type: 13px body, tight weights. (Inter is what IntelliJ ships; we use the system sans.)
    val base = Typography()
    val ui = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    CompositionLocalProvider(LocalIde provides c) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(4.dp),
                small = RoundedCornerShape(6.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(10.dp),
                extraLarge = RoundedCornerShape(12.dp),
            ),
            typography = base.copy(
                headlineMedium = base.headlineMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                titleLarge = base.titleLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                titleMedium = base.titleMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                titleSmall = base.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                bodyLarge = ui.copy(fontSize = 14.sp, lineHeight = 20.sp),
                bodyMedium = ui,
                bodySmall = ui.copy(fontSize = 12.sp, lineHeight = 16.sp),
                labelLarge = ui.copy(fontWeight = FontWeight.Medium),
                labelMedium = ui.copy(fontSize = 12.sp),
                labelSmall = ui.copy(fontSize = 11.sp),
            ),
            content = content,
        )
    }
}
