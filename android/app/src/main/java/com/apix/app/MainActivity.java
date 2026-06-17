package com.apix.app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import com.apix.app.databinding.ActivityMainBinding;

/**
 * Main Activity hosting the WebView that loads the APiX web app
 * Handles the split Web/Android architecture with different action types
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private WebView webView;
    private Gson gson = new Gson();
    
    // Your web app URL
    private static final String WEB_APP_URL = "https://tv-plus.lovable.app";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        webView = binding.webView;
        setupWebView();
        
        // Load the web app FIRST, then start security after a delay
        webView.loadUrl(WEB_APP_URL);
        
        // Delay security monitor to let WebView initialize properly
        webView.postDelayed(() -> {
            AppVerifier.getInstance(MainActivity.this).startMonitor();
        }, 3000);
                com.apix.app.security.GuardRunner.startGlobalMonitor(MainActivity.this);

    }


    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        String userAgent = settings.getUserAgentString();
        settings.setUserAgentString(userAgent + " APiXAndroid/1.0");
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.e("MainActivity", "WebView error: " + description);
                view.postDelayed(() -> view.loadUrl(WEB_APP_URL), 2000);
            }
        });
        
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
    }

    public class AndroidBridge {
        
        @JavascriptInterface
        public void playVideo(String jsonConfig) {
            runOnUiThread(() -> {
                try {
                    StreamConfig config = gson.fromJson(jsonConfig, StreamConfig.class);
                    if (config == null) {
                        showToast("Invalid stream configuration");
                        return;
                    }
                    launchPlayer(config, jsonConfig);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
        
        @JavascriptInterface
        public void checkAdGate(String categoryId) {
            runOnUiThread(() -> {
                webView.evaluateJavascript("window.__adGateResult && window.__adGateResult(true)", null);
            });
        }
        
        @JavascriptInterface
        public void showToast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
        
        @JavascriptInterface
        public boolean isAndroidApp() {
            return true;
        }
        
        @JavascriptInterface
        public String getAppVersion() {
            return "1.0.0";
        }
    }
    
    private void launchPlayer(StreamConfig config, String jsonConfig) {
        if (config.isIntentAction() && config.intentUri != null) {
            launchIntent(config.intentUri);
        } else if (config.isHybridAction()) {
            openComposePlayer(jsonConfig);
        } else if (config.isWebViewAction()) {
            openWebView(config.url, config.title, config.webViewOrientation);
        } else {
            openNativePlayer(jsonConfig);
        }
    }
    
    private void openNativePlayer(String jsonConfig) {
        Intent intent = new Intent(MainActivity.this, PlayerActivity.class);
        intent.putExtra("streamConfig", jsonConfig);
        startActivity(intent);
    }
    
    private void openWebView(String url, String title, String orientation) {
        Intent intent = new Intent(MainActivity.this, WebViewActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("title", title != null ? title : "");
        intent.putExtra("orientation", orientation);
        startActivity(intent);
    }

    private void openComposePlayer(String jsonConfig) {
        Intent intent = new Intent(MainActivity.this, ComposeActivity.class);
        intent.putExtra("streamConfig", jsonConfig);
        startActivity(intent);
    }
    
    private void launchIntent(String intentUri) {
        try {
            Intent intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                String packageName = intent.getPackage();
                if (packageName != null) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
                } else {
                    showToast("Application not found");
                }
            }
        } catch (Exception e) {
            showToast("Failed to launch: " + e.getMessage());
        }
    }
    
    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        webView.destroy();
        super.onDestroy();
    }
}
