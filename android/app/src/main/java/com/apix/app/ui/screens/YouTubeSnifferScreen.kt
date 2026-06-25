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

// خدعة الـ iOS لإجبار يوتيوب على تقديم روابط HLS نظيفة ومدمجة
private const val MAGIC_USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1.2 Safari/605.1.15"

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

    // مؤقت أمان 20 ثانية (أكثر من كافية)
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
                // إجبار المشغل على تجاهل أي حماية DRM قادمة من القنوات السابقة
                drm = null,
                drmLicenseHeaders = null,
                // إجبار المشغل على إخفاء أي ترجمة تخص القناة السابقة
                subtitleUrl = null 
            )
            PlayerScreen(config = playerConfig, onBack = onBack)
        }

        "sniffing" -> {
            Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
                val fullUrl = remember(youtubeUrl) {
                    when {
                        youtubeUrl.contains("youtu.be/") ->
                            "https://m.youtube.com/watch?v=${Uri.parse(youtubeUrl).lastPathSegment}&bpctr=9999999999" // تخطي قيود العمر
                        youtubeUrl.contains("youtube.com/shorts/") ->
                            youtubeUrl.replace("www.youtube.com", "m.youtube.com").replace("/shorts/", "/watch?v=")
                        else ->
                            youtubeUrl.replace("www.youtube.com", "m.youtube.com")
                    }
                }

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(1, 1) // متصفح الظل (مخفي)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false // السماح بالتشغيل التلقائي
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                userAgentString = MAGIC_USER_AGENT // تطبيق خدعة الآيفون هنا!
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

                                    // 1. اصطياد روابط HLS (الكنز الذي سيعطينا إياه يوتيوب بفضل خدعة الآيفون)
                                    val isHls = reqUrl.contains("manifest.googlevideo.com") || reqUrl.contains(".m3u8")
                                    
                                    // 2. اصطياد الروابط العادية المدمجة (مع تجاهل روابط الصوت فقط)
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

                                    // حقن JS بسيط وفعال لتشغيل الفيديو فوراً وبدون تعقيد كلاود
                                    val js = """
                                        (function() {
                                            var attempts = 0;
                                            function tryPlay() {
                                                attempts++;
                                                var v = document.querySelector('video');
                                                if (v) {
                                                    v.muted = true; // يجب كتم الصوت ليسمح المتصفح بالتشغيل التلقائي
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

                // واجهة التحميل
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
