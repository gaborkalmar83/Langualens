package com.lingualens.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF3F7CFF)
private val AccentLight = Color(0xFF7FB2FF)

private val DarkColors = darkColorScheme(
    primary = AccentLight,
    onPrimary = Color(0xFF06183A),
    secondary = Color(0xFF9FC0FF),
    background = Color(0xFF101319),
    surface = Color(0xFF161A23),
    onBackground = Color(0xFFE7EDF8),
    onSurface = Color(0xFFE7EDF8)
)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = Color(0xFF2F6FE4),
    background = Color(0xFFFBFAF7),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF141821),
    onSurface = Color(0xFF141821)
)

@Composable
fun LinguaLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
