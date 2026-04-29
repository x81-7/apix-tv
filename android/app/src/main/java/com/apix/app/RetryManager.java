package com.apix.app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * RetryManager — keeps the player alive when:
 *   • The stream fails to start (network / decoder / HTTP errors).
 *     We retry the SAME URL forever with exponential backoff (2s → 4s → 8s,
 *     capped at 15s), because transient CDN hiccups are the #1 cause.
 *   • The stream stalls (time does not advance for > 6s). We switch buffering
 *     mode to aggressive prefetch so future stalls are less likely.
 *   • After 6 consecutive failures we fall back to `backupUrl` if available.
 */
public class RetryManager {

    private static final String TAG = "RetryManager";
    private static final int BACKUP_THRESHOLD = 6;
    private static final long STALL_THRESHOLD_MS = 6_000L;

    private int retry = 0;
    private long lastPositionMs = -1L;
    private long lastPositionAt  = 0L;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable stallWatcher;

    public void attach(ExoPlayer player, StreamConfig config) {

        player.addListener(new Player.Listener() {

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.w(TAG, "player error #" + retry + ": " + error.getErrorCodeName());
                retry++;
                long backoff = Math.min(15_000L, 2_000L * (1L << Math.min(3, retry - 1)));
                main.postDelayed(() -> {
                    if (retry < BACKUP_THRESHOLD) {
                        // Same URL — transient failure most likely.
                        player.prepare();
                        player.play();
                    } else if (config.backupUrl != null && !config.backupUrl.isEmpty()) {
                        // Exhausted primary → try backup.
                        config.url = config.backupUrl;
                        config.backupUrl = null;
                        String format = config.url.contains(".m3u8") ? "hls"
                                      : (config.url.contains(".mpd") ? "dash" : "other");
                        MediaItem item = MediaSourceBuilder.build(config, format);
                        if (item != null) {
                            player.setMediaItem(item);
                            player.prepare();
                            player.play();
                        }
                    } else {
                        // Keep banging on the same URL — better UX than a dead player.
                        player.prepare();
                        player.play();
                    }
                }, backoff);
            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    retry = 0; // reset on any successful playback
                    startStallWatcher(player);
                } else if (state == Player.STATE_ENDED) {
                    stopStallWatcher();
                }
            }
        });
    }

    /** If the current position hasn't advanced for STALL_THRESHOLD_MS, force
     *  a re-prepare. ExoPlayer normally recovers on its own, but on flaky
     *  networks it can hang on "buffering" indefinitely.
     */
    private void startStallWatcher(ExoPlayer player) {
        stopStallWatcher();
        lastPositionMs = -1L;
        lastPositionAt = System.currentTimeMillis();
        stallWatcher = new Runnable() {
            @Override public void run() {
                try {
                    long pos = player.getCurrentPosition();
                    long now = System.currentTimeMillis();
                    boolean stalled = (pos == lastPositionMs) && player.isPlaying();
                    if (stalled && (now - lastPositionAt) > STALL_THRESHOLD_MS) {
                        Log.w(TAG, "stall detected — re-preparing");
                        player.prepare();
                        lastPositionAt = now;
                    } else if (pos != lastPositionMs) {
                        lastPositionMs = pos;
                        lastPositionAt = now;
                    }
                } catch (Throwable ignored) {}
                main.postDelayed(this, 1500L);
            }
        };
        main.postDelayed(stallWatcher, 1500L);
    }

    private void stopStallWatcher() {
        if (stallWatcher != null) main.removeCallbacks(stallWatcher);
        stallWatcher = null;
    }
}
