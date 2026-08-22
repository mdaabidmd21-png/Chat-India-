package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GptPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = GptPrimaryContainer,
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    background = GptDarkBackground,
    onBackground = GptTextPrimary,
    surface = GptDarkSurface,
    onSurface = GptTextPrimary,
    surfaceVariant = GptDarkSurfaceVariant,
    onSurfaceVariant = GptTextSecondary,
    surfaceContainer = GptDarkSurfaceContainer,
    outline = GptBorder
)

private val LightColorScheme = lightColorScheme(
    primary = GptPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1FAE5),
    onPrimaryContainer = Color(0xFF064E3B),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    background = GptLightBackground,
    onBackground = GptLightTextPrimary,
    surface = GptLightSurface,
    onSurface = GptLightTextPrimary,
    surfaceVariant = GptLightSurfaceVariant,
    onSurfaceVariant = GptLightTextSecondary,
    outline = GptLightBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // default to sleek ChatGPT dark theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
