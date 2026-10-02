package es.hugoalvarezajenjo.sproutstudio.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import es.hugoalvarezajenjo.sproutstudio.lang.SyntaxColors
import java.util.prefs.Preferences

/**
 * IDE colour tokens, modelled on the JetBrains "New UI" (Int UI) palettes.
 * Every surface in the app reads from here, never from literal colours.
 */
@Immutable
data class IdeColors(
    val isDark: Boolean,
    /** Window chrome: tool windows, tab strip, status bar. */
    val panel: Color,
    /** Editor background. */
    val editor: Color,
    /** 1px separators between areas. */
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val textDisabled: Color,
    val hover: Color,
    /** Selected row in a list/tree that has focus. */
    val selection: Color,
    val accent: Color,
    val onAccent: Color,
    val currentLine: Color,
    val lineNumber: Color,
    val lineNumberActive: Color,
    val error: Color,
    val errorBg: Color,
    val warning: Color,
    val success: Color,
    val popup: Color,
    val popupBorder: Color,
    val tooltip: Color,
    val onTooltip: Color,
    /** Find: every match / the selected one (IntelliJ search-result colours). */
    val findMatch: Color,
    val findCurrent: Color,
    val syntax: SyntaxColors,
)

val DarkIde = IdeColors(
    isDark = true,
    panel = Color(0xFF2B2D30),
    editor = Color(0xFF1E1F22),
    border = Color(0xFF1E1F22),
    text = Color(0xFFDFE1E5),
    textMuted = Color(0xFF868A91),
    textDisabled = Color(0xFF5A5D63),
    hover = Color(0xFF393B40),
    selection = Color(0xFF2E436E),
    accent = Color(0xFF3574F0),
    onAccent = Color.White,
    currentLine = Color(0xFF26282E),
    lineNumber = Color(0xFF4B5059),
    lineNumberActive = Color(0xFFA1A3AB),
    error = Color(0xFFDB5C5C),
    errorBg = Color(0xFF402929),
    warning = Color(0xFFF2C55C),
    success = Color(0xFF5FB865),
    popup = Color(0xFF2B2D30),
    popupBorder = Color(0xFF43454A),
    tooltip = Color(0xFF393B40),
    onTooltip = Color(0xFFDFE1E5),
    findMatch = Color(0xFF2E4A36),
    findCurrent = Color(0xFF3F6E4A),
    syntax = SyntaxColors(
        keyword = Color(0xFFCF8E6D),
        directive = Color(0xFFC77DBB),
        preprocessor = Color(0xFFB3AE60),
        string = Color(0xFF6AAB73),
        comment = Color(0xFF7A7E85),
        arrow = Color(0xFF56A8F5),
        color = Color(0xFF2AACB8),
        stereotype = Color(0xFFB3AE60),
        symbol = Color(0xFFBCBEC4),
        error = Color(0xFFF75464),
        bracketMatch = Color(0x4043454A),
    ),
)

val LightIde = IdeColors(
    isDark = false,
    panel = Color(0xFFF7F8FA),
    editor = Color(0xFFFFFFFF),
    border = Color(0xFFEBECF0),
    text = Color(0xFF000000),
    textMuted = Color(0xFF6C707E),
    textDisabled = Color(0xFFA8ADBD),
    hover = Color(0xFFDFE1E5),
    selection = Color(0xFFD5E1FF),
    accent = Color(0xFF3574F0),
    onAccent = Color.White,
    currentLine = Color(0xFFF5F8FE),
    lineNumber = Color(0xFFAEB3C2),
    lineNumberActive = Color(0xFF767A8A),
    error = Color(0xFFE55765),
    errorBg = Color(0xFFFFF2F2),
    warning = Color(0xFFC27D04),
    success = Color(0xFF208A3C),
    popup = Color(0xFFFFFFFF),
    popupBorder = Color(0xFFDFE1E5),
    tooltip = Color(0xFF27282E),
    onTooltip = Color(0xFFDFE1E5),
    findMatch = Color(0xFFFCEFB4),
    findCurrent = Color(0xFFF5D76E),
    syntax = SyntaxColors(
        keyword = Color(0xFF0033B3),
        directive = Color(0xFF871094),
        preprocessor = Color(0xFF9E880D),
        string = Color(0xFF067D17),
        comment = Color(0xFF8C8C8C),
        arrow = Color(0xFF00627A),
        color = Color(0xFF1750EB),
        stereotype = Color(0xFF9E880D),
        symbol = Color(0xFF080808),
        error = Color(0xFFF50000),
        bracketMatch = Color(0x4093D9D9),
    ),
)

/** The user's theme choice, persisted. Dark by default, like the JetBrains IDEs. */
object ThemePrefs {
    private val prefs = Preferences.userRoot().node("es/hugoalvarezajenjo/sproutstudio")
    var dark by mutableStateOf(prefs.getBoolean("darkTheme", true))
        private set

    fun toggle() {
        dark = !dark
        prefs.putBoolean("darkTheme", dark)
    }
}

/**
 * Whether the PREVIEW recolours diagrams dark. Independent of [ThemePrefs]: a dark editor with
 * a light diagram is the default. Exports and copied images always keep the original colours.
 */
object DiagramPrefs {
    private val prefs = Preferences.userRoot().node("es/hugoalvarezajenjo/sproutstudio")
    var dark by mutableStateOf(prefs.getBoolean("darkDiagram", false))
        private set

    fun toggle() {
        dark = !dark
        prefs.putBoolean("darkDiagram", dark)
    }
}

val LocalIde = staticCompositionLocalOf { DarkIde }

/** Shorthand for the current IDE colours. */
val ide: IdeColors
    @Composable @ReadOnlyComposable get() = LocalIde.current

val EditorFont = FontFamily.Monospace

val editorTextStyle = TextStyle(fontFamily = EditorFont, fontSize = 13.sp, lineHeight = 21.sp)

@Composable
fun PumlTheme(dark: Boolean = ThemePrefs.dark, content: @Composable () -> Unit) {
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
