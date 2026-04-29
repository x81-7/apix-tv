package com.apix.pc.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.pc.ui.theme.Gold
import kotlinx.coroutines.delay

/**
 * Pixel-faithful port of `custom_player_controls.xml` — top back button,
 * centered Rew / Play-Pause / Fwd, bottom seek bar + time + icons row.
 * Auto-hides after 3s of inactivity.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val isLive: Boolean = true,
)

@Composable
fun PlayerOverlayControls(
    title: String,
    state: PlaybackState,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onResize: () -> Unit,
    onSubtitle: () -> Unit = {},
    onSettings: () -> Unit = {},
    onPip: () -> Unit = {},
) {
    var visible by remember { mutableStateOf(true) }
    var lastTouch by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lastTouch) {
        delay(3500)
        if (System.currentTimeMillis() - lastTouch >= 3000) visible = false
    }

    val touchSrc = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = touchSrc, indication = null) {
                visible = !visible
                if (visible) lastTouch = System.currentTimeMillis()
            }
    ) {
        // Buffering spinner
        if (state.isBuffering) {
            CircularProgressIndicator(
                color = Gold, strokeWidth = 4.dp,
                modifier = Modifier.align(Alignment.Center).size(56.dp)
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.55f),
                           Color.Transparent,
                           Color.Black.copy(alpha = 0.65f))
                ))
            ) {
                // Top bar — back button + title
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircleIcon(Icons.Default.ArrowBack, "رجوع") {
                        onBack(); lastTouch = System.currentTimeMillis()
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(title, color = Color.White, fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold)
                }

                // Center — Rew / Play / Fwd
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(40.dp)
                ) {
                    BigCircleIcon(Icons.Default.Replay10, "تأخير 10 ث") {
                        onSeekBy(-10_000); lastTouch = System.currentTimeMillis()
                    }
                    BigCircleIcon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "تشغيل/إيقاف", size = 80.dp
                    ) { onPlayPause(); lastTouch = System.currentTimeMillis() }
                    BigCircleIcon(Icons.Default.Forward10, "تقديم 10 ث") {
                        onSeekBy(10_000); lastTouch = System.currentTimeMillis()
                    }
                }

                // Bottom — seek bar + time + icons
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    if (!state.isLive && state.duration > 0L) {
                        Slider(
                            value = state.position.coerceAtLeast(0L).toFloat(),
                            valueRange = 0f..state.duration.toFloat(),
                            onValueChange = {
                                onSeekTo(it.toLong()); lastTouch = System.currentTimeMillis()
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = Gold,
                                activeTrackColor = Gold,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (state.isLive) "● مباشر" else
                                "${formatTime(state.position)}  ·  ${formatTime(state.duration)}",
                            color = if (state.isLive) Color(0xFFFF5252) else Color.White.copy(alpha = 0.75f),
                            fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.weight(1f))
                        SmallIcon(Icons.Default.ClosedCaption, "ترجمة") {
                            onSubtitle(); lastTouch = System.currentTimeMillis()
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallIcon(Icons.Default.Settings, "إعدادات") {
                            onSettings(); lastTouch = System.currentTimeMillis()
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallIcon(Icons.Default.AspectRatio, "حجم") {
                            onResize(); lastTouch = System.currentTimeMillis()
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallIcon(Icons.Default.PictureInPicture, "صورة في صورة") {
                            onPip(); lastTouch = System.currentTimeMillis()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp)
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
private fun BigCircleIcon(icon: ImageVector, desc: String, size: androidx.compose.ui.unit.Dp = 64.dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(size)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(size * 0.55f)) }
}

@Composable
private fun SmallIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(38.dp)
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(20.dp)) }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}