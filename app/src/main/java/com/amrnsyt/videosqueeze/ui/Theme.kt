package com.amrnsyt.videosqueeze.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val stroke = 2.dp
    val thumb = 56.dp
    val panelMax = 340.dp
}

private val AppColors = darkColorScheme(
    primary = Color(0xFFFFD43B),
    onPrimary = Color(0xFF111111),
    secondary = Color(0xFFFFD43B),
    tertiary = Color(0xFF3ECF8E),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF0B0B0C),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF9A9AA0),
    outline = Color(0xFF38383A),
    error = Color(0xFFFF6B6B)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun VideoSqueezeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, shapes = AppShapes, content = content)
}
