package com.mrhabibi.usholli.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme

val Primary = Color(0xFF4F83CC)
val PrimaryDark = Color(0xFF01579B)
val Accent = Color(0xFF9FA8DA)
val OnBackground = Color(0xFFFFFFFF)
val Surface = Color(0xFF1E1E1E)
val SurfaceVariant = Color(0xFF2A2A2A)
val TextDim = Color(0xFFB0B0B0)

@Composable
fun UsholliTheme(content: @Composable () -> Unit) {
    // Wear MaterialTheme already ships a dark, OLED-friendly palette.
    MaterialTheme(content = content)
}
