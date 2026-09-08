package com.folzi.astrachat.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.folzi.astrachat.data.AppSettings

/**
 * Neutral graphite palette in the spirit of a code-review web UI: low-chroma surfaces,
 * hairline outlines, generous spacing and a single restrained blue accent.
 * Colour roles are declared explicitly so no screen depends on Material defaults.
 */
private val graphiteDark = darkColorScheme(
    primary = Color(0xFF7FA8DC),
    onPrimary = Color(0xFF0E1116),
    primaryContainer = Color(0xFF1D2937),
    onPrimaryContainer = Color(0xFFD8E4F4),
    secondary = Color(0xFF9AA3AD),
    onSecondary = Color(0xFF12151A),
    secondaryContainer = Color(0xFF22262B),
    onSecondaryContainer = Color(0xFFE2E5E9),
    tertiary = Color(0xFF9FB0A6),
    onTertiary = Color(0xFF111614),
    background = Color(0xFF141518),
    onBackground = Color(0xFFE8E9EA),
    surface = Color(0xFF141518),
    onSurface = Color(0xFFE8E9EA),
    surfaceVariant = Color(0xFF1C1E22),
    onSurfaceVariant = Color(0xFFA2A6AC),
    surfaceContainer = Color(0xFF1A1C1F),
    surfaceContainerLow = Color(0xFF17181B),
    surfaceContainerHigh = Color(0xFF202226),
    surfaceContainerHighest = Color(0xFF26282D),
    outline = Color(0xFF34373D),
    outlineVariant = Color(0xFF26282D),
    error = Color(0xFFE5837B),
    onError = Color(0xFF1B0F0E),
    errorContainer = Color(0xFF3A211F),
    onErrorContainer = Color(0xFFF7DCD9),
    scrim = Color(0xB3000000),
)

private val graphiteBlack = graphiteDark.copy(
    background = Color.Black,
    onBackground = Color(0xFFE8E9EA),
    surface = Color.Black,
    onSurface = Color(0xFFE8E9EA),
    surfaceVariant = Color(0xFF101114),
    surfaceContainer = Color(0xFF101114),
    surfaceContainerLow = Color(0xFF0A0B0D),
    surfaceContainerHigh = Color(0xFF16181B),
    surfaceContainerHighest = Color(0xFF1D1F23),
    outline = Color(0xFF2C2F34),
    outlineVariant = Color(0xFF1E2024),
)

private val graphiteLight = lightColorScheme(
    primary = Color(0xFF3B6EA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4EDF8),
    onPrimaryContainer = Color(0xFF1B3350),
    secondary = Color(0xFF5F6368),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECEEF1),
    onSecondaryContainer = Color(0xFF1F2328),
    tertiary = Color(0xFF5C7265),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFBFA),
    onBackground = Color(0xFF1A1B1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1B1E),
    surfaceVariant = Color(0xFFF2F3F4),
    onSurfaceVariant = Color(0xFF5F6368),
    surfaceContainer = Color(0xFFF5F5F4),
    surfaceContainerLow = Color(0xFFF8F8F7),
    surfaceContainerHigh = Color(0xFFEFEFEE),
    surfaceContainerHighest = Color(0xFFE9E9E8),
    outline = Color(0xFFD6D8DB),
    outlineVariant = Color(0xFFE6E7E9),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFBE9E7),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0x66000000),
)

/** Tight, quiet type scale: semibold headings, neutral body, letterspaced small labels. */
private val codexTypography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.02).em),
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.015).em),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.015).em),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = 0.em),
    titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.em),
    bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 25.sp, letterSpacing = 0.em),
    bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.em),
    bodySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.01.em),
    labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.02.em),
    labelMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.06.em),
    labelSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.08.em),
)

/** IDE-like radii: nothing is a pill, panels read as sheets with a hairline edge. */
private val codexShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

@Composable
fun AstraTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = settings.theme in setOf("dark", "amoled") || (settings.theme == "system" && isSystemInDarkTheme())
    val colors = when {
        settings.theme == "amoled" -> graphiteBlack
        settings.dynamicColors && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> graphiteDark
        else -> graphiteLight
    }
    MaterialTheme(colorScheme = colors, typography = codexTypography, shapes = codexShapes, content = content)
}
