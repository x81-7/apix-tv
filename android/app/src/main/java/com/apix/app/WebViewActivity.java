package com.apix.app;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Full-screen WebView Activity for android_action_type: webview
 */
public class WebViewActivity extends AppCompatActivity {

    private WebView webView;
    private FrameLayout topBar;
    private TextView titleText;
    private ImageButton backButton;
    private String url;
    private String title;
    private String orientationMode;
    private String sequentialAdUrls;
    private boolean sequentialAdsMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableFullscreen();
        setContentView(R.layout.activity_webview);

        url = getIntent().getStringExtra("url");
        title = getIntent().getStringExtra("title");
        orientationMode = getIntent().getStringExtra("orientation");
        sequentialAdUrls = getIntent().getStringExtra("sequential_ad_urls");
        sequentialAdsMode = sequentialAdUrls != null && !sequentialAdUrls.isEmpty();

        if (!sequentialAdsMode && (url == null || url.isEmpty())) {
            finish();
            return;
        }

        setupViews();
        setupWebView();
        if (!sequentialAdsMode) {
            webView.loadUrl(url);
        }
    }

    private void enableFullscreen() {
        if ("landscape".equals(orientationMode)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else if ("portrait".equals(orientationMode)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.hide(WindowInsetsCompat.Type.systemBars());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    private void setupViews() {
        webView = findViewById(R.id.webView);
        topBar = findViewById(R.id.top_bar);
        titleText = findViewById(R.id.title_text);
        backButton = findViewById(R.id.back_button);

        if (title != null && !title.isEmpty()) titleText.setText(title);
        backButton.setOnClickListener(v -> finish());
        if (!sequentialAdsMode) {
            topBar.postDelayed(() -> topBar.setVisibility(View.GONE), 3000);
        }
        webView.setOnClickListener(v -> {
            if (sequentialAdsMode) return;
            if (topBar.getVisibility() == View.VISIBLE) {
                topBar.setVisibility(View.GONE);
            } else {
                topBar.setVisibility(View.VISIBLE);
                topBar.postDelayed(() -> topBar.setVisibility(View.GONE), 3000);
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        String userAgent = settings.getUserAgentString();
        settings.setUserAgentString(userAgent + " APiXAndroid/1.0");
        webView.addJavascriptInterface(new SequentialAdsBridge(), "AdBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        if (sequentialAdsMode) {
            backButton.setVisibility(View.GONE);
            topBar.setVisibility(View.GONE);
            loadSequentialAdsPage();
        }
    }

    private void loadSequentialAdsPage() {
        try {
            JSONArray urls = new JSONArray(sequentialAdUrls);
            StringBuilder jsArray = new StringBuilder("[");
            for (int i = 0; i < urls.length(); i++) {
                if (i > 0) jsArray.append(',');
                jsArray.append(JSONObject.quote(urls.optString(i)));
            }
            jsArray.append(']');

            String html = "<!doctype html><html lang='ar'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<style>body{margin:0;background:#000;color:#fff;font-family:sans-serif;display:flex;align-items:center;justify-content:center;height:100vh}#wrap{width:100%;height:100%;position:relative}video{width:100%;height:100%;object-fit:contain;background:#000}#label{position:absolute;top:20px;left:20px;background:rgba(0,0,0,.65);padding:10px 14px;border-radius:10px;font-size:13px}</style></head><body><div id='wrap'><div id='label'>إعلانات</div><video id='v' autoplay playsinline controlslist='nodownload noplaybackrate'></video></div><script>const ads="
                    + jsArray
                    + ";let idx=0;const v=document.getElementById('v');function done(){if(window.AdBridge){window.AdBridge.complete();}}function load(){v.src=ads[idx];v.play().catch(()=>{setTimeout(next,1500);});}function next(){idx++;if(idx<ads.length){load();}else{done();}}v.addEventListener('ended',next);v.addEventListener('error',next);load();</script></body></html>";
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        } catch (Exception e) {
            finish();
        }
    }

    private final class SequentialAdsBridge {
        @JavascriptInterface
        public void complete() {
            runOnUiThread(() -> {
                AdManager.SequentialAdBridge.complete();
                finish();
            });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        if (sequentialAdsMode) return true;
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() { super.onResume(); webView.onResume(); }

    @Override
    protected void onPause() { webView.onPause(); super.onPause(); }

    @Override
    protected void onDestroy() { webView.destroy(); super.onDestroy(); }
}
