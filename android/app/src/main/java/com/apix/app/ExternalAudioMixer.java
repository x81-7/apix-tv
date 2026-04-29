package com.apix.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * Plays an external audio source (often an m3u8 video stream whose audio we
 * want to overlay on the main broadcast) in a HEADLESS ExoPlayer (no UI),
 * while muting the main player's audio.
 *
 * The main video keeps playing visually so the user gets picture from the main
 * broadcast and audio from the external source — exactly what panel sets up
 * for dual-language / commentary streams.
 *
 * Sync strategy: the audio stream starts together with main; we rely on each
 * player's internal clock. Most live sports/commentary streams are broadcast
 * with similar buffering profiles so drift stays under ~1s. If the main player
 * is paused / re-buffered we mirror the action on the audio player.
 */
public class ExternalAudioMixer {

    private final Context context;
    private ExoPlayer audioPlayer;
    private Player.Listener mainListener;
    private ExoPlayer mainPlayer;
    private final Handler ui = new Handler(Looper.getMainLooper());

    public ExternalAudioMixer(Context context) {
        this.context = context;
    }

    public void attach(ExoPlayer main, String externalAudioUrl) {
        this.mainPlayer = main;
        if (main == null || externalAudioUrl == null || externalAudioUrl.isEmpty()) return;

        // 1) MUTE the main video's own audio track entirely.
        try { main.setVolume(0f); } catch (Throwable ignored) {}

        // 2) Build a headless audio-only ExoPlayer.
        audioPlayer = new ExoPlayer.Builder(context).build();
        audioPlayer.setMediaItem(MediaItem.fromUri(externalAudioUrl));
        audioPlayer.setVolume(1.0f);
        audioPlayer.prepare();
        audioPlayer.play();

        // 3) Mirror play/pause/seek between the two players so they stay aligned.
        mainListener = new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                ui.post(() -> {
                    if (audioPlayer == null) return;
                    if (isPlaying) audioPlayer.play(); else audioPlayer.pause();
                });
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPos,
                                                Player.PositionInfo newPos, int reason) {
                ui.post(() -> {
                    if (audioPlayer == null || mainPlayer == null) return;
                    long diffMs = Math.abs(audioPlayer.getCurrentPosition() - mainPlayer.getCurrentPosition());
                    if (diffMs > 1500) {
                        audioPlayer.seekTo(mainPlayer.getCurrentPosition());
                    }
                });
            }
        };
        main.addListener(mainListener);
    }

    public void release() {
        if (mainPlayer != null && mainListener != null) {
            try { mainPlayer.removeListener(mainListener); } catch (Throwable ignored) {}
        }
        if (audioPlayer != null) {
            try { audioPlayer.release(); } catch (Throwable ignored) {}
            audioPlayer = null;
        }
    }
}
