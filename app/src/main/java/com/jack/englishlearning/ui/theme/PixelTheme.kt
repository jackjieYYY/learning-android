package com.jack.englishlearning.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.englishlearning.R

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色"), SEPIA("米色护眼")
}

/** Persists the user's theme choice; the default follows the system light/dark setting. */
class ThemeSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)
    var mode by mutableStateOf(
        prefs.getString(KEY, null)?.let { saved -> ThemeMode.entries.firstOrNull { it.name == saved } } ?: ThemeMode.SYSTEM
    )
        private set

    fun update(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
        this.mode = mode
    }

    private companion object { const val KEY = "theme_mode" }
}

/** Claude-inspired colours shared by every theme. */
object Brand {
    val Clay = Color(0xFFD97757)
    val ClayLight = Color(0xFFE99A7E)
    val ClayShade = Color(0xFFA9553A)
    val Blue = Color(0xFF6A9BCC)
    val Green = Color(0xFF788C5D)
    val Gray = Color(0xFFB0AEA5)
    val Cream = Color(0xFFFAF9F5)
    val Ink = Color(0xFF141413)
    val Error = Color(0xFFB5462F)
}

@Immutable
data class PixelPalette(
    val background: Color,
    val panel: Color,
    val panelLight: Color,
    val panelShade: Color,
    val outline: Color,
    val text: Color,
    val textMuted: Color,
    val divider: Color,
    val isDark: Boolean,
    val accent: Color = Brand.Clay,
    val accentLight: Color = Brand.ClayLight,
    val accentShade: Color = Brand.ClayShade,
    val onAccent: Color = Brand.Cream,
    val blue: Color = Brand.Blue,
    val green: Color = Brand.Green,
    val error: Color = Brand.Error,
)

val LightPalette = PixelPalette(
    background = Brand.Cream, panel = Color(0xFFE8E6DC), panelLight = Color(0xFFF8F7F2),
    panelShade = Color(0xFFB0AEA5), outline = Brand.Ink, text = Brand.Ink,
    textMuted = Color(0xFF6B6A64), divider = Color(0xFFD5D2C6), isDark = false,
)

val DarkPalette = PixelPalette(
    background = Brand.Ink, panel = Color(0xFF262624), panelLight = Color(0xFF3A3936),
    panelShade = Color(0xFF0E0E0D), outline = Color(0xFF000000), text = Brand.Cream,
    textMuted = Brand.Gray, divider = Color(0xFF3A3936), isDark = true,
)

val SepiaPalette = PixelPalette(
    background = Color(0xFFF1E7D0), panel = Color(0xFFE6D8B8), panelLight = Color(0xFFF8F0DC),
    panelShade = Color(0xFFBFA77C), outline = Color(0xFF3B2F20), text = Color(0xFF3B2F20),
    textMuted = Color(0xFF7A6748), divider = Color(0xFFD8C7A1), isDark = false,
)

@OptIn(ExperimentalTextApi::class)
object PixelFonts {
    /** Fusion Pixel 12px (OFL): titles, buttons, labels and numbers in both Chinese and English. */
    val Pixel = FontFamily(Font(R.font.fusion_pixel))
    /** Lora (OFL): English reading text. */
    val SerifEnglish = FontFamily(
        Font(R.font.lora, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.lora, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    /** Noto Serif SC / 思源宋体 (OFL): Chinese reading text and mixed explanations. */
    val SerifChinese = FontFamily(Font(R.font.noto_serif_sc))
}

/** One "art pixel" of the blocky UI. */
val ArtPixel: Dp = 2.dp

val LocalPixelPalette = staticCompositionLocalOf { LightPalette }

object PixelTheme {
    val palette: PixelPalette @Composable get() = LocalPixelPalette.current
}

fun paletteFor(mode: ThemeMode, systemDark: Boolean): PixelPalette = when (mode) {
    ThemeMode.SYSTEM -> if (systemDark) DarkPalette else LightPalette
    ThemeMode.LIGHT -> LightPalette
    ThemeMode.DARK -> DarkPalette
    ThemeMode.SEPIA -> SepiaPalette
}

private fun pixel(size: Int, line: Int) =
    TextStyle(fontFamily = PixelFonts.Pixel, fontSize = size.sp, lineHeight = line.sp)

private fun serif(size: Int, line: Int) =
    TextStyle(fontFamily = PixelFonts.SerifChinese, fontSize = size.sp, lineHeight = line.sp)

private val PixelTypography = Typography(
    headlineSmall = pixel(24, 32),
    titleLarge = pixel(22, 30),
    titleMedium = pixel(18, 26),
    titleSmall = pixel(16, 22),
    labelLarge = pixel(16, 22),
    labelMedium = pixel(14, 20),
    labelSmall = pixel(12, 18),
    bodyLarge = serif(17, 28),
    bodyMedium = serif(15, 24),
    bodySmall = serif(13, 20),
)

/**
 * Reading text. English is the study material (Lora 18/29); Chinese translation and explanations use
 * MaterialTheme.typography.bodyLarge (思源宋体 17/28). Keep reading sizes to these two styles.
 */
val ReadingEnglish = TextStyle(fontFamily = PixelFonts.SerifEnglish, fontSize = 18.sp, lineHeight = 29.sp)

@Composable
fun PixelAppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val palette = paletteFor(mode, isSystemInDarkTheme())
    val colors = if (palette.isDark) darkColorScheme(
        primary = palette.accent, onPrimary = palette.onAccent,
        background = palette.background, onBackground = palette.text,
        surface = palette.background, onSurface = palette.text,
        surfaceVariant = palette.panel, onSurfaceVariant = palette.textMuted,
        surfaceContainerLow = palette.background, surfaceContainer = palette.panel,
        outline = palette.outline, outlineVariant = palette.divider, error = palette.error,
    ) else lightColorScheme(
        primary = palette.accent, onPrimary = palette.onAccent,
        background = palette.background, onBackground = palette.text,
        surface = palette.background, onSurface = palette.text,
        surfaceVariant = palette.panel, onSurfaceVariant = palette.textMuted,
        surfaceContainerLow = palette.background, surfaceContainer = palette.panel,
        outline = palette.outline, outlineVariant = palette.divider, error = palette.error,
    )
    CompositionLocalProvider(LocalPixelPalette provides palette) {
        MaterialTheme(colorScheme = colors, typography = PixelTypography, content = content)
    }
}
