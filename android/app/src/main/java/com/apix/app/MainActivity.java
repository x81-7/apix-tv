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
import com.apix.app.security.KeysVault;
import com.apix.app.db.SecureStorageManager;

/**
 * Main Activity secured with NDK Vault and Encrypted Database integration.
 */
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private WebView webView;
    private Gson gson = new Gson();
    
    private static final String WEB_APP_URL = "https://tv-plus.lovable.app";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Ensure Security Vault is ready before UI
        try {
            KeysVault.INSTANCE.getEncryptionSecretKey();
        } catch (Exception e) {
            Log.e("Security", "Vault initialization failed");
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        webView = binding.webView;
        setupWebView();
        
        webView.loadUrl(WEB_APP_URL);
        
        // Start integrity monitor
        webView.postDelayed(() -> {
            AppVerifier.getInstance(MainActivity.this).startMonitor();
        }, 3000);
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
        settings.setUserAgentString(userAgent + " APiXAndroid/Security_V2");
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
            
            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
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
                    // Check app integrity before playing
                    String integrityError = AppVerifier.getInstance(MainActivity.this).runCheck();
                    if (integrityError != null) {
                        showToast(integrityError);
                        return;
                    }

                    StreamConfig config = gson.fromJson(jsonConfig, StreamConfig.class);
                    if (config == null) {
                        showToast("Invalid stream configuration");
                        return;
                    }
                    
                    // Secure Launch
                    launchPlayer(config, jsonConfig);
                } catch (Exception e) {
                    showToast("Playback Error: " + e.getMessage());
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
            return "2.0.0_Secured";
        }
    }
    
    private void launchPlayer(StreamConfig config, String jsonConfig) {
        if (config.isIntentAction() && config.intentUri != null) {
            launchIntent(config.intentUri);
        } else if (config.isWebViewAction()) {
            // Forwarding to secured WebView Player
            openWebView(config.url, config.title, config.webViewOrientation);
        } else {
            // Default to the new Compose Player natively (Removed old PlayerActivity)
            openComposePlayer(jsonConfig);
        }
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
        AppVerifier.getInstance(this).runCheckAsync((passed, reason) -> {
            if (!passed) runOnUiThread(() -> showToast(reason));
        });
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
