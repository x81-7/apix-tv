package com.apix.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import android.widget.FrameLayout
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

// ═══════════════════════════════════════════════════════════════════════════
// OkRuExtractor — مستقل تماماً عن كود apix.png ولا يمسّه أبداً
// ═══════════════════════════════════════════════════════════════════════════

object OkRuExtractor {

    private const val T = "OkRuExtractor"
    private const val PREFS = "okru_cache"
    private const val URL_TTL_MS = 3 * 60 * 60 * 1000L  // 3 ساعات
    private const val COOKIE_TTL_MS = 6 * 60 * 60 * 1000L // 6 ساعات

    // ── معرّف الرابط ────────────────────────────────────────────────────
    fun isOkRuUrl(url: String): Boolean {
        val u = url.lowercase().trim()
        return u.contains("ok.ru/video/") ||
               u.contains("ok.ru/videoembed/") ||
               u.contains("odnoklassniki.ru/video")
    }

    fun extractVideoId(url: String): String? {
        return try {
            val clean = url.replace("https://", "").replace("http://", "")
            val parts = clean.split("/")
            val idx = parts.indexOfFirst { it == "video" || it == "videoembed" }
            if (idx >= 0 && idx + 1 < parts.size) {
                parts[idx + 1].split("?").first().trim()
            } else null
        } catch (e: Exception) { null }
    }

    fun buildEmbedUrl(videoId: String) =
        "http://ok.ru/videoembed/$videoId?nochat=1&autoplay=1"

    // ── الدالة الرئيسية ────────────────────────────────────────────────
    suspend fun resolve(
        context: Context,
        rawUrl: String,
        onResult: (String?) -> Unit
    ) = withContext(Dispatchers.Main) {
        val videoId = extractVideoId(rawUrl)
        if (videoId == null) { onResult(null); return@withContext }

        // 1. تحقق من الكاش أولاً
        val cached = getCachedStream(context, videoId)
        if (cached != null) {
            Log.d(T, "Cache hit for $videoId")
            onResult(cached)
            return@withContext
        }

        // 2. جلب جديد عبر WebView
        Log.d(T, "Cache miss for $videoId — starting WebView extraction")
        extractViaWebView(context, videoId) { streamUrl ->
            if (streamUrl != null) {
                saveToCache(context, videoId, streamUrl)
                Log.d(T, "Extracted and cached: $videoId")
            }
            onResult(streamUrl)
        }
    }

    // ── إعادة المحاولة عند فشل المشغل ─────────────────────────────────
    suspend fun retry(
        context: Context,
        videoId: String,
        onResult: (String?) -> Unit
    ) = withContext(Dispatchers.Main) {
        Log.d(T, "Retrying extraction for $videoId (clearing stale cache)")
        clearCache(context, videoId)
        extractViaWebView(context, videoId) { streamUrl ->
            if (streamUrl != null) saveToCache(context, videoId, streamUrl)
            onResult(streamUrl)
        }
    }

    // ── استخراج عبر WebView مخفي ───────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun extractViaWebView(
        context: Context,
        videoId: String,
        timeout: Long = 25_000L,
        callback: (String?) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var done = false
        var webView: WebView? = null

        val finish: (String?) -> Unit = { result ->
            if (!done) {
                done = true
                handler.post {
                    try { webView?.destroy() } catch (_: Exception) {}
                    callback(result)
                }
            }
        }

        // مهلة 25 ثانية
        handler.postDelayed({ finish(null) }, timeout)

        val embedUrl = buildEmbedUrl(videoId)
        val ua = "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                 "AppleWebKit/537.36 (KHTML, like Gecko) " +
                 "Chrome/124.0.0.0 Mobile Safari/537.36"

        webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(1, 1)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = ua
                cacheMode = WebSettings.LOAD_DEFAULT
                allowFileAccess = false
                allowContentAccess = false
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(this@apply, true)
            }

            // اعتراض طلبات الشبكة — البحث عن m3u8
            webViewClient = object : WebViewClient() {

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val reqUrl = request?.url?.toString() ?: return null

                    // اصطياد m3u8 مباشرة
                    if (reqUrl.contains(".m3u8") || reqUrl.contains("manifest.m3u8")) {
                        Log.d(T, "Intercepted m3u8: $reqUrl")
                        finish(reqUrl)
                        return null
                    }

                    // اصطياد بيانات الـ metadata API
                    if (reqUrl.contains("videoPlayerMetadata") || reqUrl.contains("videoPlayer")) {
                        Log.d(T, "Intercepted player API call")
                        // نجمع الكوكيز ونستدعي الـ API مباشرة
                        val cookies = CookieManager.getInstance().getCookie("https://ok.ru") ?: ""
                        if (cookies.isNotBlank()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val result = fetchStreamWithCookies(videoId, cookies, ua)
                                if (result != null) finish(result)
                            }
                        }
                    }
                    return null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (done) return

                    // جمع الكوكيز بعد تحميل الصفحة
                    val cookies = CookieManager.getInstance().getCookie("https://ok.ru") ?: ""

                    // ضغطة في منتصف الشاشة لتشغيل الفيديو
                    val js = """
                        (function() {
                            try {
                                // محاولة تشغيل الفيديو مباشرة
                                var video = document.querySelector('video');
                                if (video) {
                                    video.muted = true;
                                    video.play();
                                }
                                // ضغط على زر التشغيل
                                var playBtn = document.querySelector('.vid-play-big') ||
                                              document.querySelector('[data-action="play"]') ||
                                              document.querySelector('.video-layer_play-btn');
                                if (playBtn) playBtn.click();
                                
                                // محاكاة ضغطة في منتصف الشاشة
                                var cx = window.innerWidth / 2;
                                var cy = window.innerHeight / 2;
                                var el = document.elementFromPoint(cx, cy);
                                if (el) {
                                    el.dispatchEvent(new MouseEvent('click', {
                                        bubbles: true, clientX: cx, clientY: cy
                                    }));
                                }
                            } catch(e) {}
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(js, null)

                    // إذا كان عندنا كوكيز نستخدم الـ API مباشرة
                    if (cookies.isNotBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val result = fetchStreamWithCookies(videoId, cookies, ua)
                            if (result != null) finish(result)
                        }
                    }
                }
            }

            loadUrl(embedUrl)
        }
    }

    // ── استدعاء API OK.ru مع الكوكيز ──────────────────────────────────
    private fun fetchStreamWithCookies(
        videoId: String,
        cookies: String,
        userAgent: String
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://ok.ru/dk?cmd=videoPlayerMetadata&mid=$videoId")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 12000
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Cookie", cookies)
            conn.setRequestProperty("Referer", "https://ok.ru/video/$videoId")
            conn.setRequestProperty("Origin", "https://ok.ru")
            conn.setRequestProperty("Accept", "application/json, */*")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write("gwt.requested=1")
            }

            if (conn.responseCode != 200) return null
            val raw = conn.inputStream.bufferedReader().readText()
            parseStreamUrl(raw)
        } catch (e: Exception) {
            Log.w(T, "API call failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    // ── تحليل الرابط من الرد ───────────────────────────────────────────
    private fun parseStreamUrl(responseBody: String): String? {
        return try {
            val root = JSONObject(responseBody)

            // أولوية: HLS manifest
            val hls = root.optString("hlsMasterPlaylistUrl").ifEmpty { null }
                ?: root.optString("hlsManifestUrl").ifEmpty { null }
            if (hls != null) return hls

            // ثانياً: أعلى جودة فيديو
            val videos = root.optJSONArray("videos") ?: return null
            val priority = listOf("1080", "720", "480", "360", "240")
            val map = mutableMapOf<String, String>()
            for (i in 0 until videos.length()) {
                val v = videos.getJSONObject(i)
                val u = v.optString("url", "").replace("\\u0026", "&")
                val r = v.optString("name", "")
                if (u.isNotEmpty() && r.isNotEmpty()) map[r] = u
            }
            for (p in priority) { map[p]?.let { return it } }
            map.values.firstOrNull()
        } catch (e: Exception) {
            Log.w(T, "Parse failed: ${e.message}")
            null
        }
    }

    // ── كاش محلي ───────────────────────────────────────────────────────
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getCachedStream(ctx: Context, videoId: String): String? {
        val sp   = prefs(ctx)
        val url  = sp.getString("url_$videoId", null) ?: return null
        val time = sp.getLong("ts_$videoId", 0L)
        return if (System.currentTimeMillis() - time < URL_TTL_MS) url else null
    }

    private fun saveToCache(ctx: Context, videoId: String, streamUrl: String) {
        prefs(ctx).edit()
            .putString("url_$videoId", streamUrl)
            .putLong("ts_$videoId", System.currentTimeMillis())
            .apply()
    }

    private fun clearCache(ctx: Context, videoId: String) {
        prefs(ctx).edit()
            .remove("url_$videoId")
            .remove("ts_$videoId")
            .apply()
    }
}