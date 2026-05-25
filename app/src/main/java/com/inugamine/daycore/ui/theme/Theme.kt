package com.inugamine.daycore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DaycoreColorScheme = darkColorScheme(
    primary = DaycoreAccent,
    onPrimary = DaycoreTextPrimary,
    secondary = DaycoreAccentLight,
    background = DaycoreBackground,
    surface = DaycoreSurface,
    onBackground = DaycoreTextPrimary,
    onSurface = DaycoreTextPrimary,
    outline = DaycoreDivider
)

@Composable
fun DaycoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DaycoreColorScheme,
        content = content
    )
}
