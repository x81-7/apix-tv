package com.lagradost.cloudstream3.apix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ApixGold = Color(0xFFFFD24A)
val ApixGoldSoft = Color(0xFFFFC107)
val ApixBackground = Color(0xFF060606)
val ApixSurface = Color(0xFF131313)
val ApixSurface2 = Color(0xFF1C1C1C)
val ApixGray = Color(0xFFB9B9B9)
val ApixRed = Color(0xFFCC3B3B)

private val Dark = darkColorScheme(
    primary = ApixGold,
    secondary = ApixGoldSoft,
    background = ApixBackground,
    onBackground = Color.White,
    surface = ApixSurface,
    onSurface = Color.White,
    surfaceVariant = ApixSurface2,
    onSurfaceVariant = ApixGray,
    error = ApixRed,
)

@Composable
fun APiXTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, typography = Typography(), content = content)
}
