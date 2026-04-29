package com.apix.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

public class PlayerEngine {

    private Context context;
    private StreamConfig config;
    private ExoPlayer player;

    public PlayerEngine(Context context, StreamConfig config) {
        this.context = context;
        this.config = config;
    }

    public ExoPlayer build(PlayerView view) {

        String url = cleanUrl(config.url);
        String format = detectFormat(url);

        // 1. هوية متصفح قوية لتجاوز الحظر
        DataSource.Factory factory = new DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setAllowCrossProtocolRedirects(true);

        DefaultMediaSourceFactory mediaFactory = new DefaultMediaSourceFactory(factory);

        // 2. تفعيل مفككات الفيديو الاحتياطية
        DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true);

        // Aggressive buffer sizes — helps weak connections by prefetching
        // a larger chunk ahead of playback (up to 60s) instead of the default
        // 15-30s. Trades a bit of memory for far fewer stalls.
        LoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        30_000,      // min buffer before playback resumes
                        120_000,     // max buffer (2 minutes ahead)
                        2_500,       // buffer needed before STARTING playback
                        5_000)       // buffer needed after a rebuffer
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();

        // بناء المشغل
        player = new ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .setTrackSelector(new DefaultTrackSelector(context))
                .setMediaSourceFactory(mediaFactory)
                .setLoadControl(loadControl)
                .build();

        // 3. جاسوس الأخطاء المرئي
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                String causeText = (error.getCause() != null) ? error.getCause().getMessage() : "بدون تفاصيل إضافية";
                // 🔥 تم الإصلاح هنا (إضافة get والأقواس)
                String msg = "نوع الخطأ:\n" + error.getErrorCodeName() + "\n\nالسبب الدقيق:\n" + causeText;
                
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (context instanceof Activity && !((Activity) context).isFinishing()) {
                        new AlertDialog.Builder(context)
                            .setTitle("⚠️ توقف المشغل!")
                            .setMessage(msg)
                            .setPositiveButton("حسناً", null)
                            .setCancelable(false)
                            .show();
                    }
                });
            }
        });

        // جلب الفيديو مشفراً
        MediaItem item = MediaSourceBuilder.build(config, format);

        if (item != null) {
            player.setMediaItem(item);
            player.prepare();
            player.play();
        }

        view.setPlayer(player);
        new RetryManager().attach(player, config);

        return player;
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    private String cleanUrl(String url) {
        if (url == null) return "";
        return url.contains("|") ? url.split("\\|")[0] : url;
    }

    private String detectFormat(String url) {
        if (url == null) return "other";
        if (url.contains(".mpd")) return "dash";
        if (url.contains(".m3u8")) return "hls";
        return "other";
    }
}
