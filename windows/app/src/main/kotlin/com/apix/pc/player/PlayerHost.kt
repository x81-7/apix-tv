package com.apix.pc.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.apix.pc.data.StreamConfig

/**
 * Picks between JCEF Shaka (DRM-capable) and VLCJ (plain HLS/MP4) and overlays
 * the shared Compose controls (back / play-pause / seek / resize / …) — same
 * visual language as the Android ExoPlayer player.
 */
@Composable
fun PlayerHost(stream: StreamConfig, onClose: () -> Unit) {
    val useJcef = remember(stream) {
        stream.playerType.equals("webview", true) ||
        stream.playerType.equals("shaka_web", true) ||
        stream.playerType.equals("shaka", true) ||
        stream.playerType.equals("jw_web", true) ||
        !stream.drmKey.isNullOrBlank() ||
        !stream.drmLicenseUrl.isNullOrBlank() ||
        stream.url.endsWith(".mpd", true)
    }
    var resizeMode by remember { mutableStateOf(0) } // 0 contain, 1 fill, 2 cover
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var selectedQ by remember { mutableStateOf(0) }
    var selectedA by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (useJcef) {
            val bridge = remember { ShakaBridge() }
            JcefPlayer(stream, bridge)
            PlayerOverlayControls(
                title = stream.title, state = bridge.state,
                onBack = onClose,
                onPlayPause = { bridge.playPause() },
                onSeekBy = { bridge.seekBy(it) },
                onSeekTo = { bridge.seekTo(it) },
                onResize = {
                    resizeMode = (resizeMode + 1) % 3
                    bridge.setFit(resizeMode)
                },
                onSubtitle = { showAudioDialog = true },
                onSettings = { showQualityDialog = true },
            )
            if (showQualityDialog) {
                TrackSelectionDialog(
                    title = "الجودة",
                    items = listOf("تلقائي") + bridge.qualities.map { "${it}p" },
                    selectedIndex = selectedQ,
                    onSelect = { idx ->
                        selectedQ = idx
                        if (idx == 0) bridge.setQuality(-1) else bridge.setQuality(bridge.qualities[idx - 1])
                    },
                    onDismiss = { showQualityDialog = false }
                )
            }
            if (showAudioDialog) {
                TrackSelectionDialog(
                    title = "الصوت / الترجمة",
                    items = bridge.audioLanguages,
                    selectedIndex = selectedA,
                    onSelect = { idx ->
                        selectedA = idx
                        bridge.setAudio(bridge.audioLanguages[idx])
                    },
                    onDismiss = { showAudioDialog = false }
                )
            }
        } else {
            val bridge = remember { VlcjBridge() }
            VlcjPlayer(stream, bridge)
            PlayerOverlayControls(
                title = stream.title, state = bridge.state,
                onBack = onClose,
                onPlayPause = { bridge.playPause() },
                onSeekBy = { bridge.seekBy(it) },
                onSeekTo = { bridge.seekTo(it) },
                onResize = { /* VLCJ aspect-ratio toggle TBD */ },
            )
        }
    }
}