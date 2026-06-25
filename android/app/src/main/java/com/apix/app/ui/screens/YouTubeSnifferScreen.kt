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

// itags الخاصة بالصوت فقط — نتجاهلها
private val AUDIO_ONLY_ITAGS = setOf(
    "139", "140", "141", // AAC audio
    "171", "172",         // Vorbis audio
    "249", "250", "251"  // Opus audio (WebM)
)

// itags المدمجة (فيديو + صوت) — هذه الأفضل لـ ExoPlayer
private val MUXED_ITAGS = setOf("18", "22", "37", "59", "78")

private fun isMuxedStream(url: String): Boolean {
    val itag = Regex("[?&]itag=(\\d+)").find(url)?.groupValues?.getOrNull(1)
    return itag != null && itag in MUXED_ITAGS
}

private fun isAudioOnlyStream(url: String): Boolean {
    val itag = Regex("[?&]itag=(\\d+)").find(url)?.groupValues?.getOrNull(1)
    if (itag != null && itag in AUDIO_ONLY_ITAGS) return true
    if (url.contains("mime=audio")) return true
    return false
}

private fun isSabrStream(url: String): Boolean =
    url.contains("sabr=1") || url.contains("rqh=1")

private fun cleanVideoUrl(url: String): String {
    // نحذف المعاملات الإشكالية التي تمنع ExoPlayer من التشغيل
    return url
        .replace(Regex("[?&]range=[^&]*"), "")
        .replace(Regex("[?&]rn=[^&]*"), "")
        .replace(Regex("[?&]rbuf=[^&]*"), "")
        .replace(Regex("[?&]ump=[^&]*"), "")
        .replace(Regex("[?&]sabr=[^&]*"), "")
        .replace(Regex("[?&]rqh=[^&]*"), "")
        .replace(Regex("[?&]alr=[^&]*"), "")
        .replace(Regex("[?&]cpn=[^&]*"), "")
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

    // مؤقت أمان 30 ثانية
    LaunchedEffect(phase) {
        if (phase != "sniffing") return@LaunchedEffect
        delay(30_000)
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
                    userAgent = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36",
                    referer   = "https://m.youtube.com/"
                )
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
                            youtubeUrl.replace("www.youtube.com", "m.youtube.com")
                                .replace("/shorts/", "/watch?v=")
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
                                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36"
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null

                                    // إذا وجدنا رابطاً بالفعل لا نحتاج المزيد
                                    if (sniffFound) return null

                                    if (reqUrl.contains("videoplayback")) {

                                        // 1. الأولوية القصوى: روابط مدمجة (فيديو+صوت)
                                        if (isMuxedStream(reqUrl) && !isAudioOnlyStream(reqUrl)) {
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
                                            return null
                                        }

                                        // 2. رابط عادي بدون SABR وبدون صوت فقط
                                        if (!isSabrStream(reqUrl) && !isAudioOnlyStream(reqUrl)) {
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
                                            return null
                                        }

                                        // 3. SABR مقبول كآخر ملجأ إذا لم نجد شيئاً آخر
                                        // (نحفظه لاحتمال الاستخدام إذا لم يظهر أي رابط آخر)
                                        if (!isAudioOnlyStream(reqUrl) && finalStreamUrl.isEmpty()) {
                                            val clean = cleanVideoUrl(reqUrl)
                                            view?.post { finalStreamUrl = clean }
                                        }
                                    }

                                    // فحص HLS manifest (أفضل للبث المباشر)
                                    if ((reqUrl.contains("manifest.googlevideo.com") ||
                                         reqUrl.contains(".m3u8")) && !sniffFound) {
                                        sniffFound = true
                                        view?.post {
                                            finalStreamUrl = reqUrl
                                            onStreamReady(reqUrl)
                                            phase = "playing"
                                            view.stopLoading()
                                            view.destroy()
                                            webViewRef = null
                                        }
                                    }

                                    return null
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)

                                    // JS متقدم يجبر يوتيوب على استخدام البث القديم
                                    val js = """
                                        (function() {
                                            try {
                                                // محاولة إجبار YouTube على استخدام itag=22 (720p مدمج)
                                                if (window.yt && window.yt.config_) {
                                                    var cfg = window.yt.config_;
                                                    if (cfg.EXPERIMENT_FLAGS) {
                                                        cfg.EXPERIMENT_FLAGS.html5_sabr_ump = false;
                                                        cfg.EXPERIMENT_FLAGS.html5_enable_sabr_ump = false;
                                                        cfg.EXPERIMENT_FLAGS.html5_disable_sabr = true;
                                                    }
                                                }
                                            } catch(e) {}
                                            
                                            var attempts = 0;
                                            function tryPlay() {
                                                attempts++;
                                                var v = document.querySelector('video');
                                                if (v) {
                                                    v.muted = true;
                                                    v.volume = 0;
                                                    v.play().catch(function(){});
                                                }
                                                // محاولة النقر على أزرار التشغيل المختلفة
                                                var btns = [
                                                    document.querySelector('.ytp-large-play-button'),
                                                    document.querySelector('button[aria-label="Play"]'),
                                                    document.querySelector('.ytp-play-button'),
                                                    document.querySelector('[data-title-no-tooltip="Play"]')
                                                ];
                                                btns.forEach(function(b) { if(b) b.click(); });
                                                
                                                // محاولة الوصول للبيانات عبر يوتيوب API
                                                if (!window._apix_sniff_tried) {
                                                    window._apix_sniff_tried = true;
                                                    try {
                                                        var p = document.getElementById('movie_player');
                                                        if (p && p.getVideoData) {
                                                            var d = p.getVideoData();
                                                            if (d && d.video_id) {
                                                                p.playVideo();
                                                            }
                                                        }
                                                    } catch(e) {}
                                                }
                                                
                                                if (attempts < 10) setTimeout(tryPlay, 1500);
                                            }
                                            setTimeout(tryPlay, 800);
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(js, null)
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    // إذا كان لدينا رابط محفوظ نستخدمه حتى مع وجود خطأ
                                    if (!sniffFound && finalStreamUrl.isNotEmpty()) {
                                        sniffFound = true
                                        val saved = finalStreamUrl
                                        view?.post {
                                            onStreamReady(saved)
                                            phase = "playing"
                                            view.stopLoading()
                                            view.destroy()
                                            webViewRef = null
                                        }
                                    }
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
                    CircularProgressIndicator(
                        color = MediumRed,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "جاري تحميل الفيديو...",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "يوتيوب",
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp
                    )
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
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Error, null, tint = MediumRed, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(statusMsg, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
    }
}