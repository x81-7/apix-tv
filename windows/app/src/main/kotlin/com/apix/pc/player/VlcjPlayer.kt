package com.apix.pc.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.apix.pc.data.StreamConfig
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.MediaPlayer

/**
 * VLCJ-backed fallback for plain HLS / MP4 / direct streams that do NOT need
 * EME/DRM. VLC supports custom HTTP headers natively (`:http-user-agent`,
 * `:http-referrer`) so this is the right pick for sources where Shaka would
 * fail because of CORS or missing MSE support.
 */

/** Shared bridge — the Compose overlay reads state and posts commands. */
class VlcjBridge {
    var state by mutableStateOf(PlaybackState(isLive = true))
    var execute: (String) -> Unit = {}
    fun playPause() { execute("toggle") }
    fun seekBy(ms: Long) { execute("seekBy:$ms") }
    fun seekTo(ms: Long) { execute("seekTo:$ms") }
}

@Composable
fun VlcjPlayer(stream: StreamConfig, bridge: VlcjBridge, modifier: Modifier = Modifier) {
    val discovered = remember { NativeDiscovery().discover() }
    if (!discovered) {
        // VLC not installed on the host — surface a friendly placeholder.
        return
    }

    val component = remember { EmbeddedMediaPlayerComponent() }
    val player: EmbeddedMediaPlayer = remember { component.mediaPlayer() }

    DisposableEffect(stream) {
        val opts = mutableListOf<String>()
        stream.userAgent?.takeIf { it.isNotBlank() }?.let { opts += ":http-user-agent=$it" }
        stream.referer?.takeIf { it.isNotBlank() }?.let { opts += ":http-referrer=$it" }

        val listener = object : MediaPlayerEventAdapter() {
            override fun playing(mp: MediaPlayer?) { bridge.state = bridge.state.copy(isPlaying = true, isBuffering = false) }
            override fun paused(mp: MediaPlayer?) { bridge.state = bridge.state.copy(isPlaying = false) }
            override fun buffering(mp: MediaPlayer?, newCache: Float) {
                bridge.state = bridge.state.copy(isBuffering = newCache < 100f)
            }
            override fun timeChanged(mp: MediaPlayer?, newTime: Long) {
                val dur = mp?.status()?.length() ?: 0L
                bridge.state = bridge.state.copy(
                    position = newTime, duration = dur,
                    isLive = dur <= 0L
                )
            }
        }
        player.events().addMediaPlayerEventListener(listener)

        bridge.execute = { cmd ->
            when {
                cmd == "toggle" -> if (player.status().isPlaying) player.controls().pause() else player.controls().play()
                cmd.startsWith("seekBy:") -> player.controls().skipTime(cmd.removePrefix("seekBy:").toLong())
                cmd.startsWith("seekTo:") -> player.controls().setTime(cmd.removePrefix("seekTo:").toLong())
            }
        }

        player.media().play(stream.url, *opts.toTypedArray())
        onDispose {
            player.events().removeMediaPlayerEventListener(listener)
            player.controls().stop()
            component.release()
        }
    }

    SwingPanel(
        modifier = modifier.fillMaxSize(),
        factory = { component }
    )
}