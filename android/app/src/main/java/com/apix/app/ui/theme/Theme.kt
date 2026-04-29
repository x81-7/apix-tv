package com.apix.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Gold = Color(0xFFFFD700)
val GoldDark = Color(0xFFFFC107)
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF1A1A1A)
val DarkCard = Color(0xFF222222)
val CharcoalCard = Color(0xFF2A2A2A)
val White = Color.White
val LightGray = Color(0xFFAAAAAA)
val MediumRed = Color(0xFFCC3333)

private val DarkColorScheme = darkColorScheme(
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

private val LightColorScheme = lightColorScheme(
    primary = Gold,
    onPrimary = Color.Black,
    secondary = GoldDark,
    background = Color(0xFFF5F5F5),
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE0E0E0),
)

@Composable
fun APiXTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
