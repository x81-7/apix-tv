package com.apix.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.dash.DashChunkSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resilient ExoPlayer wrapper.
 *
 *  - Resolves dynamic JSON wrappers off the main thread.
 *  - Merges userAgent / Referer / Cookie / Origin / customHeaders into the
 *    HTTP DataSource and keeps them across redirects.
 *  - Uses an aggressive retry policy (5 silent retries) before surfacing errors.
 *  - Forces APPLICATION_M3U8 MimeType when URL is HLS-disguised.
 */
public class PlayerEngine {

    private static final String DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile";

    private final Context context;
    private final StreamConfig config;
    private ExoPlayer player;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public PlayerEngine(Context context, StreamConfig config) {
        this.context = context;
        this.config = config;
    }

    public ExoPlayer build(PlayerView view) {
        // 1. Aggressive but safe load policy: 5 silent retries before surfacing.
        DefaultLoadErrorHandlingPolicy errorPolicy = new DefaultLoadErrorHandlingPolicy(5);

        // 2. Larger buffers help on flaky mobile connections.
        LoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 120_000, 2_500, 5_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true);

        // Build with a placeholder DataSource factory; actual factory is rebuilt
        // once the dynamic resolver finishes. We rebuild the MediaSource then.
        DataSource.Factory boot = baseDataSourceFactory(buildHeaderMap(config, null), null);
        DefaultMediaSourceFactory mediaFactory = new DefaultMediaSourceFactory(boot)
                .setLoadErrorHandlingPolicy(errorPolicy);

        player = new ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(new DefaultTrackSelector(context))
                .setMediaSourceFactory(mediaFactory)
                .setLoadControl(loadControl)
                .build();

        attachErrorListener();
        view.setPlayer(player);
        new RetryManager().attach(player, config);

        // 3. Resolve URL off-thread, then load.
        final String rawUrl = config != null ? config.url : null;
        io.execute(() -> {
            DynamicStreamResolver.Resolved r = DynamicStreamResolver.resolve(rawUrl);
            // Merge resolver-provided headers / UA / Referer with config headers.
            Map<String, String> merged = buildHeaderMap(config, r);

            // Rebuild factory with merged headers (replaces boot).
            DataSource.Factory finalFactory = baseDataSourceFactory(merged,
                    pickUserAgent(config, r));
            DefaultMediaSourceFactory rebuilt = new DefaultMediaSourceFactory(finalFactory)
                    .setLoadErrorHandlingPolicy(errorPolicy);

            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    if (player == null) return;
                    player.setMediaSource(rebuilt.createMediaSource(
                            MediaSourceBuilder.build(config, r.url, r.forceHls)));
                    player.prepare();
                    player.play();
                } catch (Exception e) {
                    showError("init failed", e.getMessage());
                }
            });
        });

        return player;
    }

    public ExoPlayer getPlayer() { return player; }

    // ---- helpers ----------------------------------------------------------

    private DataSource.Factory baseDataSourceFactory(Map<String, String> headers, String ua) {
        DefaultHttpDataSource.Factory f = new DefaultHttpDataSource.Factory()
                .setUserAgent(ua != null ? ua : DEFAULT_UA)
                .setAllowCrossProtocolRedirects(true)
                .setKeepPostFor302Redirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000);
        if (headers != null && !headers.isEmpty()) {
            f.setDefaultRequestProperties(headers);
        }
        return f;
    }

    private static String pickUserAgent(StreamConfig c, DynamicStreamResolver.Resolved r) {
        if (r != null && r.userAgent != null && !r.userAgent.isEmpty()) return r.userAgent;
        if (c != null && c.headers != null && c.headers.userAgent != null
                && !c.headers.userAgent.isEmpty()) return c.headers.userAgent;
        return null;
    }

    private static Map<String, String> buildHeaderMap(StreamConfig c, DynamicStreamResolver.Resolved r) {
        Map<String, String> m = new HashMap<>();
        if (c != null) {
            if (c.headers != null) {
                if (notEmpty(c.headers.referer))  m.put("Referer", c.headers.referer);
                if (notEmpty(c.headers.cookie))   m.put("Cookie",  c.headers.cookie);
                if (notEmpty(c.headers.origin))   m.put("Origin",  c.headers.origin);
            }
            if (c.customHeaders != null) {
                for (Map.Entry<String, String> e : c.customHeaders.entrySet()) {
                    if (notEmpty(e.getKey()) && e.getValue() != null) m.put(e.getKey(), e.getValue());
                }
            }
            // Legacy "url|key:val|key2:val2" tail.
            if (c.url != null && c.url.contains("|")) {
                String[] parts = c.url.split("\\|");
                for (int i = 1; i < parts.length; i++) {
                    DynamicStreamResolver.parseEncodedHeaders(parts[i], m);
                }
            }
        }
        if (r != null) {
            if (notEmpty(r.referer)) m.put("Referer", r.referer);
            if (r.headers != null) m.putAll(r.headers);
        }
        return m;
    }

    private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }

    private void attachErrorListener() {
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                String cause = (error.getCause() != null) ? error.getCause().getMessage() : "—";
                showError(error.getErrorCodeName(), cause);
            }
        });
    }

    private void showError(String code, String detail) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (context instanceof Activity && !((Activity) context).isFinishing()) {
                new AlertDialog.Builder(context)
                        .setTitle("⚠️ توقف المشغل")
                        .setMessage("نوع الخطأ:\n" + code + "\n\nالتفاصيل:\n" + detail)
                        .setPositiveButton("حسناً", null)
                        .setCancelable(false)
                        .show();
            }
        });
    }
}
