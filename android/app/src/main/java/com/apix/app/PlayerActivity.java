package com.apix.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import com.google.gson.Gson;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private PlayerEngine engine;
    private ExternalAudioMixer audioMixer;
    private ImageView logoOverlay;
    private FrameLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        root = findViewById(R.id.player_root);
        playerView = findViewById(R.id.playerView);
        logoOverlay = findViewById(R.id.logo_overlay);

        String json = getIntent().getStringExtra("streamConfig");

        if (json == null) {
            Toast.makeText(this, "No config", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        StreamConfig config = new Gson().fromJson(json, StreamConfig.class);

        StreamAnalyzer.analyze(config);

        // Panel-driven aspect ratio enforcement (4 modes: fit, fill, zoom, original)
        applyAspectRatio(config);

        engine = new PlayerEngine(this, config);
        engine.build(playerView);

        // Hide the resize button entirely if the panel locked aspect ratio.
        applyResizeButtonVisibility(config);

        // External audio source: keep main video playing MUTED while a headless
        // ExoPlayer plays the external audio source (m3u8/mp3/etc.) in sync.
        if (config.hasAudioSources()) {
            String externalUrl = config.audioSources.get(0).url;
            if (externalUrl != null && !externalUrl.isEmpty() && engine.getPlayer() != null) {
                audioMixer = new ExternalAudioMixer(this);
                audioMixer.attach(engine.getPlayer(), externalUrl);
            }
        }

        // Smart Logo Overlay (panel-supplied URL + percent coords)
        if (config.logoOverlay != null && config.logoOverlay.url != null
                && !config.logoOverlay.url.isEmpty()) {
            renderLogoOverlay(config.logoOverlay);
        }
    }

    private void applyAspectRatio(StreamConfig config) {
        if (config == null) return;
        int mode = resolveResizeMode(config.forcedAspectRatio);
        if (mode >= 0) {
            playerView.setResizeMode(mode);
        }
    }

    private void applyResizeButtonVisibility(StreamConfig config) {
        if (config != null && config.lockAspectRatio) {
            View resizeBtn = playerView.findViewById(R.id.exo_resize);
            if (resizeBtn != null) resizeBtn.setVisibility(View.GONE);
        }
    }

    private int resolveResizeMode(String forced) {
        if (forced == null) return -1;
        switch (forced.toLowerCase()) {
            case "original":
            case "fit": return AspectRatioFrameLayout.RESIZE_MODE_FIT;
            case "stretch":
            case "fill": return AspectRatioFrameLayout.RESIZE_MODE_FILL;
            case "zoom":
            case "crop": return AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
            case "16:9": return AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH;
            case "4:3": return AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT;
            case "fixed_width": return AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH;
            case "fixed_height": return AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT;
            default: return -1;
        }
    }

    /** Loads logo and positions it on top of PlayerView using percent x/y/width. */
    private void renderLogoOverlay(StreamConfig.LogoOverlay l) {
        new Thread(() -> {
            try {
                URL u = new URL(l.url);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                InputStream is = conn.getInputStream();
                final Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                if (bmp == null) return;
                runOnUiThread(() -> {
                    logoOverlay.setImageBitmap(bmp);
                    logoOverlay.setAlpha(l.opacity > 0 ? l.opacity : 1.0f);

                    // Convert percent → px after layout pass.
                    root.post(() -> {
                        int rw = root.getWidth(), rh = root.getHeight();
                        // x/y/width are 0..100 percent. height auto from aspect ratio.
                        int wPct = l.width > 0 ? l.width : 12;     // default 12% wide
                        int xPct = clampPct(l.offsetX);
                        int yPct = clampPct(l.offsetY);

                        int wPx = (rw * wPct) / 100;
                        int hPx = (int) (wPx * ((float) bmp.getHeight() / Math.max(1, bmp.getWidth())));

                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(wPx, hPx);
                        lp.leftMargin = (rw * xPct) / 100;
                        lp.topMargin = (rh * yPct) / 100;
                        // Honor `position` keyword if set (top-left/top-right/...)
                        lp.gravity = resolveGravity(l.position);
                        if (lp.gravity != android.view.Gravity.NO_GRAVITY) {
                            // For named positions, ignore offsets and let gravity do the work.
                            lp.leftMargin = (rw * xPct) / 100;
                            lp.topMargin = (rh * yPct) / 100;
                        }
                        logoOverlay.setLayoutParams(lp);
                        logoOverlay.setVisibility(View.VISIBLE);
                    });
                });
            } catch (Exception ignored) {}
        }).start();
    }

    private static int clampPct(int v) { return Math.max(0, Math.min(100, v)); }

    private static int resolveGravity(String pos) {
        if (pos == null) return android.view.Gravity.NO_GRAVITY;
        switch (pos.toLowerCase()) {
            case "top-left":     return android.view.Gravity.TOP | android.view.Gravity.START;
            case "top-right":    return android.view.Gravity.TOP | android.view.Gravity.END;
            case "bottom-left":  return android.view.Gravity.BOTTOM | android.view.Gravity.START;
            case "bottom-right": return android.view.Gravity.BOTTOM | android.view.Gravity.END;
            default: return android.view.Gravity.NO_GRAVITY;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioMixer != null) audioMixer.release();
        if (engine != null && engine.getPlayer() != null) {
            engine.getPlayer().release();
        }
    }
}
