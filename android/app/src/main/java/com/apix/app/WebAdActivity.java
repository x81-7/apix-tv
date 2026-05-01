package com.apix.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.apix.app.security.DeviceIntegrity;

/**
 * WebView-based ad with skip countdown + VIP CTA.
 *
 * Inputs (Intent extras):
 *   - "url"           : ad URL to load
 *   - "skipAfter"     : seconds before Skip becomes available (default 5)
 *   - "sellerUrl"     : Telegram URL for VIP activation
 *
 * NOTE: D-Pad focus is supported via standard Android focusable views (no gold
 * focus tint — we use a blue stroke for VIP, white stroke for Skip).
 */
public class WebAdActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_SKIP_AFTER = "skipAfter";
    public static final String EXTRA_SELLER_URL = "sellerUrl";

    private int remaining;
    private Button btnSkip;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getIntent().getStringExtra(EXTRA_URL);
        remaining = Math.max(1, getIntent().getIntExtra(EXTRA_SKIP_AFTER, 5));
        String sellerUrl = getIntent().getStringExtra(EXTRA_SELLER_URL);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // WebView ad
        WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (url != null && !url.isEmpty()) web.loadUrl(url);

        // Bottom button bar
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(24, 24, 24, 32);

        Button btnVip = new Button(this);
        btnVip.setText("تفعيل الاشتراك");
        btnVip.setAllCaps(false);
        btnVip.setTextColor(0xFFFFFFFF);
        btnVip.setBackgroundColor(0xFF2563EB); // blue (NOT gold)
        btnVip.setFocusable(true);
        btnVip.setFocusableInTouchMode(false);
        btnVip.setOnClickListener(v -> {
            Intent i = new Intent(WebAdActivity.this, ActivationActivity.class);
            if (sellerUrl != null) i.putExtra(ActivationActivity.EXTRA_SELLER_URL, sellerUrl);
            startActivity(i);
        });
        // Blue focus stroke handled by selector — minimal inline style:
        btnVip.setOnFocusChangeListener((v, has) -> v.setBackgroundColor(has ? 0xFF1D4ED8 : 0xFF2563EB));

        btnSkip = new Button(this);
        btnSkip.setAllCaps(false);
        btnSkip.setTextColor(0xFF000000);
        btnSkip.setBackgroundColor(0xFFE5E7EB); // neutral
        btnSkip.setEnabled(false);
        btnSkip.setFocusable(true);
        btnSkip.setText("تخطّي (" + remaining + ")");
        btnSkip.setOnClickListener(v -> finish());
        btnSkip.setOnFocusChangeListener((v, has) -> v.setBackgroundColor(has ? 0xFFCBD5E1 : 0xFFE5E7EB));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(12, 0, 12, 0);
        bar.addView(btnVip, lp);
        bar.addView(btnSkip, lp);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(bar, barLp);

        setContentView(root);
        btnVip.requestFocus();
        tickSkip();
    }

    private void tickSkip() {
        if (remaining <= 0) {
            btnSkip.setEnabled(true);
            btnSkip.setText("تخطّي");
            btnSkip.setBackgroundColor(0xFFFFFFFF);
            btnSkip.requestFocus();
            return;
        }
        btnSkip.setText("تخطّي (" + remaining + ")");
        remaining--;
        handler.postDelayed(this::tickSkip, 1000);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Helper: launch when needed (returns true when started). */
    public static boolean launchIfEnabled(Context ctx, String url, int skipAfter, String sellerUrl) {
        if (url == null || url.isEmpty()) return false;
        Intent i = new Intent(ctx, WebAdActivity.class);
        i.putExtra(EXTRA_URL, url);
        i.putExtra(EXTRA_SKIP_AFTER, skipAfter);
        if (sellerUrl != null) i.putExtra(EXTRA_SELLER_URL, sellerUrl);
        if (!(ctx instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
        return true;
    }
}
