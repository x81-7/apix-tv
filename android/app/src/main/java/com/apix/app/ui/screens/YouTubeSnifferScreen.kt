package com.apix.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.apix.app.data.PlayerConfig
import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.MediumRed

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSnifferScreen(
    youtubeUrl: String,
    config: PlayerConfig,
    onStreamReady: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var statusText by remember { mutableStateOf("جاري استخراج رابط البث...") }
    var hasFound by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showError by remember { mutableStateOf(false) }

    // Timeout — if no videoplayback URL found in 25 seconds, show error
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(25000)
        if (!hasFound) {
            showError = true
            statusText = "فشل استخراج رابط البث من يوتيوب"
            webViewRef?.post {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
            }
            webViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Hidden WebView (1x1 pixel, off-screen)
        if (!hasFound && !showError) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(1, 1)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            allowContentAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            cacheMode = WebSettings.LOAD_DEFAULT
                            loadWithOverviewMode = true
                            useWideViewPort = true
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val requestUrl = request?.url?.toString() ?: return null

                                // Sniff for videoplayback URL
                                if (requestUrl.contains("videoplayback") && !hasFound) {
                                    hasFound = true
                                    Log.d("YouTubeSniffer", "Found videoplayback URL: ${requestUrl.take(100)}...")

                                    // Post to main thread
                                    view?.post {
                                        statusText = "تم استخراج الرابط بنجاح!"
                                        // Destroy webview to free resources
                                        view.stopLoading()
                                        view.destroy()
                                        webViewRef = null
                                        // Pass the raw URL to ExoPlayer
                                        onStreamReady(requestUrl)
                                    }
                                }
                                return null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                // Inject JS to click play button if needed
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        // Try to auto-play
                                        var player = document.querySelector('video');
                                        if (player) { player.play(); }
                                        // Try clicking play button
                                        var playBtn = document.querySelector('.ytp-play-button');
                                        if (playBtn) { playBtn.click(); }
                                        var largePlayBtn = document.querySelector('.ytp-large-play-button');
                                        if (largePlayBtn) { largePlayBtn.click(); }
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }
                        }

                        webChromeClient = WebChromeClient()
                        webViewRef = this

                        // Convert youtu.be short links to full URLs
                        val fullUrl = if (youtubeUrl.contains("youtu.be/")) {
                            val videoId = Uri.parse(youtubeUrl).lastPathSegment
                            "https://m.youtube.com/watch?v=$videoId"
                        } else if (youtubeUrl.contains("youtube.com/watch")) {
                            youtubeUrl.replace("www.youtube.com", "m.youtube.com")
                        } else {
                            youtubeUrl
                        }

                        loadUrl(fullUrl)
                    }
                },
                modifier = Modifier.size(1.dp) // Invisible
            )
        }

        // Loading UI
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!showError) {
                CircularProgressIndicator(
                    color = MediumRed,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(20.dp))
            }

            Text(
                text = statusText,
                color = if (showError) MediumRed else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            if (showError) {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold)
                ) {
                    Text("رجوع", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }
}
