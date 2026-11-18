package com.arziki.fileshare

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B7A5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE2),
    onPrimaryContainer = Color(0xFF00201A)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1A5),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF005140),
    onPrimaryContainer = Color(0xFFCDEFE2)
)

@Composable
fun ArzikiTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
