package com.kaze.code.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object KC {
    val bg = Color(0xFF0D1117)
    val surface = Color(0xFF151B23)
    val surface2 = Color(0xFF1C2430)
    val line = Color(0xFF2A3342)
    val text = Color(0xFFE6EDF3)
    val dim = Color(0xFF8B98A9)
    val violet = Color(0xFF7C5CFF)
    val teal = Color(0xFF2DD4BF)
    val red = Color(0xFFFF6B7A)
    val green = Color(0xFF3FB950)
}

@Composable
fun KazeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = KC.violet, onPrimary = Color.White,
            secondary = KC.teal, onSecondary = Color.Black,
            background = KC.bg, onBackground = KC.text,
            surface = KC.surface, onSurface = KC.text,
            surfaceVariant = KC.surface2, onSurfaceVariant = KC.dim,
            outline = KC.line, error = KC.red
        ),
        content = content
    )
}
