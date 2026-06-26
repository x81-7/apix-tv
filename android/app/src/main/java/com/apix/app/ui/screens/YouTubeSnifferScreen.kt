package com.apix.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import android.view.View
import androidx.core.net.toUri

// خدعة iPhone/iOS لجلب مسارات HLS/Manifest أنظف من واجهة يوتيوب
private const val MAGIC_USER_AGENT =
    "Mozilla/5.0 (iPhone; CPU iPhone OS 16_6 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1"

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

private fun toMobileYouTubeUrl(input: String): String {
    val u = input.trim()
    return when {
        u.contains("youtu.be/") -> {
            val id = Uri.parse(u).lastPathSegment ?: ""
            "https://m.youtube.com/watch?v=$id&bpctr=9999999999"
        }

        u.contains("youtube.com/shorts/") -> {
            u.replace("www.youtube.com", "m.youtube.com")
                .replace("youtube.com", "m.youtube.com")
                .replace("/shorts/", "/watch?v=")
        }

        u.contains("youtube.com/live/") -> {
            u.replace("www.youtube.com", "m.youtube.com")
                .replace("youtube.com", "m.youtube.com")
        }

        else -> {
            u.replace("www.youtube.com", "m.youtube.com")
                .replace("youtube.com", "m.youtube.com")
        }
    }
}

private fun isYouTubeLiveRequest(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("source=yt_live_broadcast") ||
            lower.contains("live=1") ||
            lower.contains("live=dvr") ||
            lower.contains("yt_live_broadcast") ||
            lower.contains("watch?v=") && lower.contains("/live/")
}

private fun isPlayableSniffUrl(url: String): Boolean {
    val lower = url.lowercase()
    val path = lower.substringBefore("?")

    val isHls = lower.contains(".m3u8") || path.endsWith(".m3u8") || lower.contains("format=m3u8")
    val isDash = lower.contains(".mpd") || path.endsWith(".mpd") || lower.contains("format=mpd") || lower.contains("manifest.googlevideo.com")
    val isVideoPlayback = lower.contains("videoplayback")
    val isAudioOnly = lower.contains("mime=audio") || lower.contains("itag=140")

    return isHls || isDash || (isVideoPlayback && !isAudioOnly)
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
    var statusMsg by remember { mutableStateOf("يجري التحميل انتظر قليلاً...") }
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
                    referer = "https://m.youtube.com/"
                ),
                drm = null,
                drmLicenseHeaders = null,
                subtitleUrl = null
            )

            PlayerScreen(
                config = playerConfig,
                onBack = onBack
            )
        }

        "sniffing" -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                val fullUrl = remember(youtubeUrl) {
                    toMobileYouTubeUrl(youtubeUrl)
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
                                databaseEnabled = true
                                allowFileAccess = false
                                allowContentAccess = false
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

                                    val live = isYouTubeLiveRequest(reqUrl)
                                    val playable = isPlayableSniffUrl(reqUrl)

                                    if ((playable || live) && !sniffFound) {
                                        sniffFound = true

                                        // في البث المباشر لا ننظّف الرابط كثيرًا
                                        val clean = if (live) reqUrl else cleanVideoUrl(reqUrl)

                                        view?.post {
                                            finalStreamUrl = clean
                                            onStreamReady(clean)
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

                                                var liveBadge = document.querySelector('.ytp-live-badge');
                                                if (liveBadge) {
                                                    var vv = document.querySelector('video');
                                                    if (vv) {
                                                        vv.play().catch(function(){});
                                                    }
                                                }

                                                if (attempts < 10) setTimeout(tryPlay, 900);
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
                    CircularProgressIndicator(
                        color = MediumRed,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = statusMsg,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    try {
                        webViewRef?.stopLoading()
                        webViewRef?.loadUrl("about:blank")
                        webViewRef?.clearHistory()
                        webViewRef?.removeAllViews()
                        webViewRef?.destroy()
                    } catch (_: Throwable) {
                    }
                    webViewRef = null
                }
            }
        }

        "error" -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MediumRed,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = statusMsg,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
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