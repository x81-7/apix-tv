package com.apix.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
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
import com.apix.app.data.PlayerHeaders
import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.MediumRed
import kotlinx.coroutines.delay

// خدعة الـ iOS لإجبار يوتيوب على تقديم HLS غير مشفر 
private const val MAGIC_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

private fun cleanVideoUrl(url: String): String {
    return url
        .replace(Regex("[?&]range=[^&]*"), "")
        .replace(Regex("[?&]rn=[^&]*"), "")
        .replace(Regex("[?&]rbuf=[^&]*"), "")
        .replace(Regex("[?&]ump=[^&]*"), "")
        .replace(Regex("[?&]sabr=[^&]*"), "")
        .replace(Regex("&&+"), "&")
        .replace(Regex("[?&]$"), "")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSnifferScreen(
    youtubeUrl: String,
    config: PlayerConfig,
    onBack: () -> Unit,
    onStreamReady: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableStateOf("sniffing") }
    var statusMsg by remember { mutableStateOf("يجري التحميل انتظر قليلا...") }
    var finalStreamUrl by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var sniffFound by remember { mutableStateOf(false) }

    LaunchedEffect(phase) {
        if (phase != "sniffing") return@LaunchedEffect
        delay(20_000)
        if (!sniffFound) {
            phase = "error"
            statusMsg = "تعذر سحب الفيديو — تأكد من الرابط أو اتصالك"
        }
    }

    when (phase) {
        "playing" -> {
            val playerConfig = config.copy(
                url = finalStreamUrl,
                headers = PlayerHeaders(
                    userAgent = MAGIC_USER_AGENT,
                    referer   = "https://m.youtube.com/"
                ),
                drm = null,
                drmLicenseHeaders = null,
                subtitleUrl = null 
            )
            PlayerScreen(config = playerConfig, onBack = onBack)
        }

        "sniffing" -> {
            Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
                val fullUrl = remember(youtubeUrl) {
                    when {
                        youtubeUrl.contains("youtu.be/") ->
                            "https://m.youtube.com/watch?v=${Uri.parse(youtubeUrl).lastPathSegment}&bpctr=9999999999" 
                        youtubeUrl.contains("youtube.com/shorts/") ->
                            youtubeUrl.replace("www.youtube.com", "m.youtube.com").replace("/shorts/", "/watch?v=")
                        else ->
                            youtubeUrl.replace("www.youtube.com", "m.youtube.com")
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(1, 1) 
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false 
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = MAGIC_USER_AGENT 
                                cacheMode = WebSettings.LOAD_NO_CACHE
                            }
                            
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null

                                    if (sniffFound) return null

                                    // --- السر هنا: نتجاهل الـ DASH تماماً لكي لا نحصل على فيديو مشفر يسبب خطأ DRM ---
                                    val isHls = reqUrl.contains(".m3u8") // تم مسح manifest.googlevideo.com
                                    val isVideoPlayback = reqUrl.contains("videoplayback")
                                    val isAudioOnly = reqUrl.contains("mime=audio") || reqUrl.contains("itag=140")

                                    if ((isHls || (isVideoPlayback && !isAudioOnly)) && !sniffFound) {
                                        sniffFound = true
                                        val clean = cleanVideoUrl(reqUrl)
                                        
                                        view?.post {
                                            finalStreamUrl = clean
                                            onStreamReady(clean)
                                            phase = "playing"
                                            view.stopLoading()
                                            view.destroy()
                                            webViewRef = null
                                        }
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    val js = """
                                        (function() {
                                            var attempts = 0;
                                            function tryPlay() {
                                                attempts++;
                                                var v = document.querySelector('video');
                                                if (v) {
                                                    v.muted = true; 
                                                    v.play().catch(function(){});
                                                }
                                                var playBtn = document.querySelector('.ytp-large-play-button');
                                                if (playBtn) playBtn.click();
                                                
                                                if (attempts < 8) setTimeout(tryPlay, 1000);
                                            }
                                            setTimeout(tryPlay, 500);
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(js, null)
                                }
                            }

                            webChromeClient = WebChromeClient()
                            webViewRef = this
                            loadUrl(fullUrl)
                        }
                    },
                    modifier = Modifier.size(1.dp)
                )

                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MediumRed, strokeWidth = 3.dp, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(statusMsg, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    webViewRef?.stopLoading()
                    webViewRef?.destroy()
                    webViewRef = null
                }
            }
        }

        "error" -> {
            Box(modifier = modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, null, tint = MediumRed, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(statusMsg, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = Gold)) {
                        Text("رجوع", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
