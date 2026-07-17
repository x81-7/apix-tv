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

// iOS UA → يجبر YouTube على إعطاء HLS بدون DRM
private const val IOS_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) " +
        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"

// itags مدمجة (فيديو+صوت) بدون DRM - آمنة للتشغيل المباشر
private val SAFE_MUXED_ITAGS = setOf("18", "22", "37", "59", "78")
// itags صوت فقط - نتجاهلها
private val AUDIO_ONLY_ITAGS = setOf("139", "140", "141", "171", "172", "249", "250", "251")

// هل هذا رابط بث مباشر (يوضع .live في نهايته)
fun isYouTubeLiveUrl(url: String) = url.lowercase().endsWith(".live")

private fun toWatchUrl(youtubeUrl: String): String {
    val clean = youtubeUrl.removeSuffix(".live").removeSuffix(".mp4")
    return when {
        clean.contains("youtu.be/") ->
            "https://m.youtube.com/watch?v=${Uri.parse(clean).lastPathSegment}"
        clean.contains("youtube.com/shorts/") ->
            clean.replace("www.youtube.com", "m.youtube.com").replace("/shorts/", "/watch?v=")
        else ->
            clean.replace("www.youtube.com", "m.youtube.com")
    }
}

private fun extractItag(url: String): String? =
    Regex("[?&]itag=(\\d+)").find(url)?.groupValues?.getOrNull(1)

private fun isSafeVideoUrl(url: String): Boolean {
    val itag = extractItag(url) ?: return false
    if (itag in AUDIO_ONLY_ITAGS) return false
    if (itag in SAFE_MUXED_ITAGS) return true
    // تجنب DASH/SABR
    if (url.contains("sabr=1") || url.contains("rqh=1")) return false
    if (url.contains("mime=audio")) return false
    return false // نقبل فقط SAFE_MUXED_ITAGS
}

private fun cleanUrl(url: String): String =
    url.replace(Regex("[?&]range=[^&]*"), "")
       .replace(Regex("[?&]rn=[^&]*"), "")
       .replace(Regex("[?&]rbuf=[^&]*"), "")
       .replace(Regex("[?&]sabr=[^&]*"), "")
       .replace(Regex("[?&]rqh=[^&]*"), "")
       .replace(Regex("[?&]alr=[^&]*"), "")
       .replace(Regex("[?&]cpn=[^&]*"), "")
       .replace(Regex("&&+"), "&")
       .replace(Regex("[?&]$"), "")

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSnifferScreen(
    youtubeUrl: String,
    config: PlayerConfig,
    onBack: () -> Unit,
    onStreamReady: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isLive = remember(youtubeUrl) { isYouTubeLiveUrl(youtubeUrl) }
    var phase       by remember { mutableStateOf("sniffing") }
    var finalUrl    by remember { mutableStateOf("") }
    var webViewRef  by remember { mutableStateOf<WebView?>(null) }
    var found       by remember { mutableStateOf(false) }

    // timeout
    LaunchedEffect(phase) {
        if (phase != "sniffing") return@LaunchedEffect
        delay(25_000)
        if (!found) phase = "error"
    }

    when (phase) {
        "playing" -> {
            PlayerScreen(
                config = config.copy(
                    url     = finalUrl,
                    drm     = null,
                    drmLicenseHeaders = null,
                    useLocalProxy = false,
                    headers = PlayerHeaders(
                        userAgent = IOS_UA,
                        referer   = "https://m.youtube.com/"
                    )
                ),
                onBack = onBack
            )
        }

        "sniffing" -> {
            val watchUrl = remember(youtubeUrl) { toWatchUrl(youtubeUrl) }

            Box(modifier = modifier.fillMaxSize().background(Color.Black)) {

                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(1, 1)
                            settings.apply {
                                javaScriptEnabled                = true
                                domStorageEnabled                = true
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                // iOS UA هنا هو المفتاح الحقيقي لمنع DRM
                                userAgentString  = IOS_UA
                                cacheMode        = WebSettings.LOAD_NO_CACHE
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                            webViewClient = object : WebViewClient() {

                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null
                                    if (found) return null

                                    if (isLive) {
                                        // ── بث مباشر: نبحث عن m3u8 ─────────────────
                                        if (reqUrl.contains(".m3u8")) {
                                            found = true
                                            val clean = cleanUrl(reqUrl)
                                            view?.post {
                                                finalUrl = clean
                                                onStreamReady(clean)
                                                phase = "playing"
                                                view.stopLoading(); view.destroy()
                                                webViewRef = null
                                            }
                                        }
                                    } else {
                                        // ── فيديو: نبحث عن itag آمن مدمج ─────────────
                                        if (reqUrl.contains("videoplayback") && isSafeVideoUrl(reqUrl)) {
                                            found = true
                                            val clean = cleanUrl(reqUrl)
                                            view?.post {
                                                finalUrl = clean
                                                onStreamReady(clean)
                                                phase = "playing"
                                                view.stopLoading(); view.destroy()
                                                webViewRef = null
                                            }
                                        }
                                        // HLS من iOS fallback
                                        if (!found && reqUrl.contains(".m3u8") &&
                                            reqUrl.contains("googlevideo.com")) {
                                            found = true
                                            view?.post {
                                                finalUrl = reqUrl
                                                onStreamReady(reqUrl)
                                                phase = "playing"
                                                view.stopLoading(); view.destroy()
                                                webViewRef = null
                                            }
                                        }
                                    }
                                    return null
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    view?.evaluateJavascript("""
                                        (function(){
                                            var attempts=0;
                                            function tryPlay(){
                                                attempts++;
                                                var v=document.querySelector('video');
                                                if(v){v.muted=true;v.play().catch(function(){});}
                                                var b=document.querySelector('.ytp-large-play-button')||
                                                       document.querySelector('button[aria-label="Play"]');
                                                if(b)b.click();
                                                if(attempts<10)setTimeout(tryPlay,1000);
                                            }
                                            setTimeout(tryPlay,500);
                                        })();
                                    """.trimIndent(), null)
                                }
                            }

                            webChromeClient = WebChromeClient()
                            webViewRef = this
                            loadUrl(watchUrl)
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
                        color = MediumRed, strokeWidth = 3.dp,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        if (isLive) "جاري تحميل البث المباشر..." else "جاري تحميل الفيديو...",
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isLive) "YouTube Live" else "YouTube",
                        color = Color(0xFFAAAAAA), fontSize = 13.sp
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
                    Text(
                        "تعذر تحميل المحتوى",
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
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