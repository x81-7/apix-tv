package com.apix.pc.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Mirrors `android/app/src/main/java/com/apix/app/ui/theme/Theme.kt`
 * so the Windows UI looks identical: gold accent on near-black surfaces.
 */
val Gold = Color(0xFFFFD700)
val GoldDark = Color(0xFFFFC107)
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF1A1A1A)
val DarkCard = Color(0xFF222222)
val CharcoalCard = Color(0xFF2A2A2A)
val White = Color.White
val LightGray = Color(0xFFAAAAAA)
val MediumRed = Color(0xFFCC3333)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color.Black,
    secondary = GoldDark,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = White,
    surface = DarkSurface,
    onSurface = White,
    surfaceVariant = DarkCard,
    onSurfaceVariant = LightGray,
    error = MediumRed,
)

@Composable
fun ApixTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = Typography(),
        content = content
    )
}