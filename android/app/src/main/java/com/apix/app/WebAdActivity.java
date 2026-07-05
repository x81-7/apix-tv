package com.apix.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * WebView-based (CPM) ad.
 *
 * Design (per product spec):
 *   - The ad content is a custom HTML page (e.g. https://ads.domain.com) loaded
 *     inside a REAL WebView, so networks that block raw redirect URLs still fill.
 *   - There is NO user-facing "close" button. The HTML page owns the countdown
 *     and calls back into Android through the JS bridge {@code AndroidAd} to
 *     dismiss the ad once its timer ends.
 *   - A native fail-safe timer force-closes the ad at {@code skipAfter + BUFFER}
 *     seconds so a broken/empty ad page can never hang the app.
 *
 * JS bridge contract (call from the ad page):
 *   AndroidAd.getDuration()   -> int seconds the page should count down
 *   AndroidAd.onAdComplete()  -> dismiss the ad and continue into the app
 *   AndroidAd.onAdReady()     -> (optional) tells native the ad rendered
 *   AndroidAd.openVip()       -> open the VIP activation screen
 *
 * Inputs (Intent extras):
 *   - "url"        : ad page URL
 *   - "skipAfter"  : countdown seconds (default 5)
 *   - "sellerUrl"  : Telegram/contact URL for VIP activation
 */
public class WebAdActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_SKIP_AFTER = "skipAfter";
    public static final String EXTRA_SELLER_URL = "sellerUrl";

    // Extra safety window added on top of skipAfter for the native fail-safe.
    private static final int FAILSAFE_BUFFER_SEC = 5;

    private int durationSec;
    private String sellerUrl;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean finished = new AtomicBoolean(false);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String url = getIntent().getStringExtra(EXTRA_URL);
        durationSec = Math.max(1, getIntent().getIntExtra(EXTRA_SKIP_AFTER, 5));
        sellerUrl = getIntent().getStringExtra(EXTRA_SELLER_URL);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        // The page talks back to Android through this bridge object.
        web.addJavascriptInterface(new AdJsBridge(), "AndroidAd");
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (url != null && !url.isEmpty()) web.loadUrl(url);

        // The ONLY interactive control is the VIP activation CTA. No user skip.
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(24, 24, 24, 32);

        Button btnVip = new Button(this);
        btnVip.setText("تفعيل الاشتراك");
        btnVip.setAllCaps(false);
        btnVip.setTextColor(0xFFFFFFFF);
        btnVip.setBackgroundColor(0xFF2563EB);
        btnVip.setFocusable(true);
        btnVip.setFocusableInTouchMode(false);
        btnVip.setOnClickListener(v -> openVip());
        btnVip.setOnFocusChangeListener((v, has) -> v.setBackgroundColor(has ? 0xFF1D4ED8 : 0xFF2563EB));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bar.addView(btnVip, lp);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(bar, barLp);

        setContentView(root);
        btnVip.requestFocus();

        // Native fail-safe: if the page never calls onAdComplete() (empty/broken
        // ad or JS disabled) we still dismiss so the app never hangs.
        handler.postDelayed(this::finishAd,
                (durationSec + FAILSAFE_BUFFER_SEC) * 1000L);
    }

    private void openVip() {
        Intent i = new Intent(WebAdActivity.this, ActivationActivity.class);
        if (sellerUrl != null) i.putExtra(ActivationActivity.EXTRA_SELLER_URL, sellerUrl);
        startActivity(i);
    }

    /** Dismiss exactly once and hand control back to the ad gate. */
    private void finishAd() {
        if (!finished.compareAndSet(false, true)) return;
        try { AdManager.WebAdBridge.complete(); } catch (Throwable ignored) {}
        try { finish(); } catch (Throwable ignored) {}
    }

    /** Block the hardware/system back button while the ad is showing. */
    @Override
    public void onBackPressed() { /* no-op: user cannot dismiss the ad */ }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        // Safety: make sure the gate callback fires even if the OS killed us.
        if (finished.compareAndSet(false, true)) {
            try { AdManager.WebAdBridge.complete(); } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    /** JavaScript ↔ Android bridge exposed to the ad page as `AndroidAd`. */
    private final class AdJsBridge {
        @JavascriptInterface
        public int getDuration() { return durationSec; }

        @JavascriptInterface
        public void onAdReady() { /* hook for analytics if needed */ }

        @JavascriptInterface
        public void onAdComplete() { handler.post(WebAdActivity.this::finishAd); }

        @JavascriptInterface
        public void openVip() { handler.post(WebAdActivity.this::openVip); }
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
