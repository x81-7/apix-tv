package com.apix.pc.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.apix.pc.ui.components.ApixLogo
import com.apix.pc.ui.theme.Gold
import kotlinx.coroutines.delay

/** Boot splash — APiX wordmark with a gold spinner. Mirrors Android SplashActivity. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    LaunchedEffect(Unit) {
        delay(1400)
        onDone()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ApixLogo(fontSize = 64, modifier = Modifier.scale(scale))
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(
                color = Gold,
                trackColor = Color(0xFF333333),
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}