package com.folzi.astrachat.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.folzi.astrachat.data.AppSettings

@Composable
fun AstraTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = settings.theme in setOf("dark", "amoled") || (settings.theme == "system" && isSystemInDarkTheme())
    val colors = when {
        settings.theme == "amoled" -> darkColorScheme(primary = Color(0xFF8ABEFF), background = Color.Black, surface = Color.Black, surfaceContainer = Color(0xFF141719))
        settings.dynamicColors && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> darkColorScheme(primary = Color(0xFF8ABEFF), background = Color(0xFF121619), surface = Color(0xFF121619), surfaceContainer = Color(0xFF1C2329))
        else -> lightColorScheme(primary = Color(0xFF1760A6), background = Color(0xFFFCFCFD), surface = Color(0xFFFCFCFD), surfaceContainer = Color(0xFFF0F3F7))
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
