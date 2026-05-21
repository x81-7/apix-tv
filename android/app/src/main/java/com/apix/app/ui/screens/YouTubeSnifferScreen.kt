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
import com.apix.app.data.PlayerDrm
import com.apix.app.data.PlayerHeaders
import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.MediumRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val YT_TAG = "YouTubePlayer"

// ── استخراج Video ID من أي رابط يوتيوب ──────────────────────────
private fun extractVideoId(url: String): String? {
    return try {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: ""
        when {
            host.contains("youtu.be") ->
                uri.lastPathSegment
            host.contains("youtube.com") -> {
                when {
                    uri.pathSegments.contains("shorts") ->
                        uri.pathSegments.getOrNull(uri.pathSegments.indexOf("shorts") + 1)
                    uri.pathSegments.contains("embed") ->
                        uri.pathSegments.getOrNull(uri.pathSegments.indexOf("embed") + 1)
                    else ->
                        uri.getQueryParameter("v")
                }
            }
            else -> null
        }?.replace(Regex("[^a-zA-Z0-9_-]"), "")?.take(11)
    } catch (e: Exception) {
        Log.w(YT_TAG, "extractVideoId failed", e)
        null
    }
}

// ── استدعاء InnerTube API ──────────────────────────────────────────
private fun tryInnerTubeApi(videoId: String): String? {
    // قائمة Clients للتجربة بالترتيب
    val clients = listOf(
        mapOf(
            "clientName" to "ANDROID",
            "clientVersion" to "19.09.37",
            "androidSdkVersion" to 30,
            "hl" to "en", "gl" to "US"
        ),
        mapOf(
            "clientName" to "IOS",
            "clientVersion" to "19.09.3",
            "deviceMake" to "Apple",
            "deviceModel" to "iPhone16,2",
            "hl" to "en", "gl" to "US"
        ),
        mapOf(
            "clientName" to "WEB",
            "clientVersion" to "2.20260424.01.00",
            "hl" to "en", "gl" to "US"
        )
    )

    for (client in clients) {
        try {
            val clientJson = JSONObject(client)
            val requestBody = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", clientJson)
                })
                put("videoId", videoId)
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }

            val conn = URL("https://www.youtube.com/youtubei/v1/player").openConnection()
                    as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12)")
            conn.setRequestProperty("Origin", "https://www.youtube.com")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(requestBody.toString())
            writer.flush()
            writer.close()

            if (conn.responseCode != 200) continue

            val resp = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(resp)
            val streaming = json.optJSONObject("streamingData") ?: continue

            // أولاً: HLS Manifest (للبث المباشر)
            val hls = streaming.optString("hlsManifestUrl", "")
            if (hls.isNotEmpty() && hls.startsWith("http")) {
                Log.d(YT_TAG, "Got HLS from ${client["clientName"]}")
                return hls
            }

            // ثانياً: Adaptive Formats (للمقاطع)
            val adaptive = streaming.optJSONArray("adaptiveFormats")
            if (adaptive != null) {
                // اختر أعلى جودة فيديو
                var bestUrl = ""
                var bestHeight = 0
                for (i in 0 until adaptive.length()) {
                    val fmt = adaptive.getJSONObject(i)
                    val mimeType = fmt.optString("mimeType", "")
                    if (!mimeType.contains("video/")) continue
                    val height = fmt.optInt("height", 0)
                    val url = fmt.optString("url", "")
                    if (height > bestHeight && url.isNotEmpty() && url.startsWith("http")) {
                        bestHeight = height
                        bestUrl = url
                    }
                }
                if (bestUrl.isNotEmpty()) {
                    Log.d(YT_TAG, "Got adaptive ${bestHeight}p from ${client["clientName"]}")
                    return bestUrl
                }
            }

            // ثالثاً: Formats العادية
            val formats = streaming.optJSONArray("formats")
            if (formats != null && formats.length() > 0) {
                val url = formats.getJSONObject(0).optString("url", "")
                if (url.isNotEmpty() && url.startsWith("http")) {
                    Log.d(YT_TAG, "Got format from ${client["clientName"]}")
                    return url
                }
            }

        } catch (e: Exception) {
            Log.w(YT_TAG, "Client ${client["clientName"]} failed: ${e.message}")
        }
    }
    return null
}

// ── الشاشة الرئيسية ───────────────────────────────────────────────
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSnifferScreen(
    youtubeUrl: String,
    config: PlayerConfig,
    onBack: () -> Unit,
    onStreamReady: (String) -> Unit = {}, // 👈 هذا هو السطر الذي أضفناه ليحل المشكلة
    modifier: Modifier = Modifier
) {
    // مراحل: api_loading → api_success → sniffing → playing → error
    var phase by remember { mutableStateOf("api_loading") }
    var statusMsg by remember { mutableStateOf("جاري تحميل الفيديو...") }
    var finalStreamUrl by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var sniffFound by remember { mutableStateOf(false) }

    // المرحلة 1: محاولة InnerTube API في الخلفية
    LaunchedEffect(youtubeUrl) {
        val videoId = extractVideoId(youtubeUrl)
        if (videoId.isNullOrEmpty()) {
            statusMsg = "رابط يوتيوب غير صحيح"
            phase = "error"
            return@LaunchedEffect
        }

        statusMsg = "جاري الاتصال بيوتيوب..."
        val url = withContext(Dispatchers.IO) { tryInnerTubeApi(videoId) }
        if (!url.isNullOrEmpty()) {
            finalStreamUrl = url
            onStreamReady(url) // 👈 إبلاغ الأكتيفتي برابط البث
            phase = "playing"
        } else {
            // الانتقال للمرحلة 2: WebView Sniffing
            statusMsg = "جاري تحضير البث..."
            phase = "sniffing"
        }
    }

    // Timeout للـ Sniffing
    LaunchedEffect(phase) {
        if (phase != "sniffing") return@LaunchedEffect
        delay(30000)
        if (!sniffFound) {
            phase = "error"
            statusMsg = "تعذر تحميل الفيديو — تحقق من الرابط"
        }
    }

    when (phase) {
        "playing" -> {
            // تشغيل في ExoPlayer
            val playerConfig = config.copy(
                url = finalStreamUrl,
                headers = PlayerHeaders(
                    userAgent = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                )
            )
            PlayerScreen(config = playerConfig, onBack = onBack)
        }

        "sniffing" -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black)
            ) {
                // WebView مخفي لاصطياد رابط videoplayback
                val fullUrl = remember(youtubeUrl) {
                    when {
                        youtubeUrl.contains("youtu.be/") -> {
                            val id = Uri.parse(youtubeUrl).lastPathSegment
                            "https://m.youtube.com/watch?v=$id"
                        }
                        youtubeUrl.contains("youtube.com") ->
                            youtubeUrl.replace("www.youtube.com", "m.youtube.com")
                        else -> youtubeUrl
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
                                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val reqUrl = request?.url?.toString() ?: return null
                                    if (reqUrl.contains("videoplayback") && !sniffFound) {
                                        sniffFound = true
                                        view?.post {
                                            finalStreamUrl = reqUrl
                                            onStreamReady(reqUrl) // 👈 إبلاغ الأكتيفتي برابط البث
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
                                    // محاولات متعددة لتشغيل الفيديو
                                    val js = """
                                        (function() {
                                            var attempts = 0;
                                            function tryPlay() {
                                                attempts++;
                                                var v = document.querySelector('video');
                                                if (v) {
                                                    v.muted = false;
                                                    v.volume = 1.0;
                                                    v.play().catch(function(){});
                                                }
                                                var btn = document.querySelector('.ytp-large-play-button, .ytp-play-button');
                                                if (btn) btn.click();
                                                if (attempts < 5) setTimeout(tryPlay, 1000);
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
                    CircularProgressIndicator(
                        color = MediumRed,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(statusMsg, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("يوتيوب", color = Color.Gray, fontSize = 12.sp)
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

        "api_loading" -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MediumRed,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(statusMsg, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        "error" -> {
            Box(
                modifier = modifier.fillMaxSize().background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️", fontSize = 48.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(statusMsg, color = MediumRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
