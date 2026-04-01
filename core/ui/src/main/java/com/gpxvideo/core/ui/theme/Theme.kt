package com.gpxvideo.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Primary accent — a sporty blue that pops on dark surfaces
val AccentBlue = Color(0xFF448AFF)
val AccentBlueDark = Color(0xFF1565C0)

// Secondary — warm highlight for GPX data visuals
val AccentTeal = Color(0xFF26A69A)
val AccentTealDark = Color(0xFF00796B)

// Tertiary — warm accent for warnings and emphasis
val AccentAmber = Color(0xFFFFAB40)

// Sport-activity highlight colors (kept for route/profile visuals)
val SportGreen = Color(0xFF66BB6A)
val SportRed = Color(0xFFEF5350)

// Dark palette — deep blue-black editor surfaces
val EditorBackground = Color(0xFF0D1117)
val EditorSurface = Color(0xFF161B22)
val EditorSurfaceVariant = Color(0xFF21262D)
val EditorSurfaceContainer = Color(0xFF1A1F27)
val EditorSurfaceContainerHigh = Color(0xFF262C36)
val EditorSurfaceDim = Color(0xFF0A0E13)

private val DarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlueDark,
    onPrimaryContainer = Color.White,
    secondary = AccentTeal,
    onSecondary = Color.White,
    secondaryContainer = AccentTealDark,
    onSecondaryContainer = Color.White,
    tertiary = AccentAmber,
    background = EditorBackground,
    onBackground = Color(0xFFE6EDF3),
    surface = EditorSurface,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = EditorSurfaceVariant,
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceContainerLowest = EditorSurfaceDim,
    surfaceContainerLow = EditorBackground,
    surfaceContainer = EditorSurfaceContainer,
    surfaceContainerHigh = EditorSurfaceContainerHigh,
    surfaceDim = EditorSurfaceDim,
    outline = Color(0xFF30363D),
    outlineVariant = Color(0xFF21262D),
    error = Color(0xFFF85149),
    onError = Color.White,
    inverseSurface = Color(0xFFE6EDF3),
    inverseOnSurface = EditorBackground
)

private val LightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A40),
    secondary = AccentTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00201E),
    tertiary = AccentAmber
)

val GpxVideoTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp
    )
)

val GpxVideoShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun GpxVideoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GpxVideoTypography,
        shapes = GpxVideoShapes,
        content = content
    )
}
