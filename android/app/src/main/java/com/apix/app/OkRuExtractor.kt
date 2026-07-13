package com.apix.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object OkRuExtractor {

    private const val T = "OkRuExtractor"
    private const val PREFS = "okru_cache"
    private const val URL_TTL_MS = 3 * 60 * 60 * 1000L

    // ── كشف رابط OK.ru ─────────────────────────────────────────────────
    fun isOkRuUrl(url: String): Boolean {
        val u = url.lowercase().trim()
        return u.contains("ok.ru/video/") ||
               u.contains("ok.ru/videoembed/") ||
               u.contains("odnoklassniki.ru/video")
    }

    // ── استخراج رقم الفيديو ─────────────────────────────────────────────
    fun extractVideoId(url: String): String? {
        return try {
            val clean = url.replace("https://", "").replace("http://", "")
            val parts = clean.split("/")
            val idx = parts.indexOfFirst { it == "video" || it == "videoembed" }
            if (idx >= 0 && idx + 1 < parts.size)
                parts[idx + 1].split("?").first().trim()
            else null
        } catch (e: Exception) { null }
    }

    // ── بناء رابط التضمين ───────────────────────────────────────────────
    fun buildEmbedUrl(videoId: String) =
        "http://ok.ru/videoembed/$videoId?nochat=1&autoplay=1"

    // ── الدالة الرئيسية ─────────────────────────────────────────────────
    // المفتاح هنا: cacheKey يجمع videoId + channelName لفصل كل قناة
    fun resolve(
        context: Context,
        rawUrl: String,
        channelName: String = "",
        callback: (String?) -> Unit
    ) {
        val videoId = extractVideoId(rawUrl)
        if (videoId == null) { callback(null); return }

        // مفتاح الكاش = videoId + اسم القناة (لفصل كل قناة بمفردها)
        val cacheKey = buildCacheKey(videoId, channelName)

        val cached = getCachedStream(context, cacheKey)
        if (cached != null) {
            Log.d(T, "Cache hit: $cacheKey")
            callback(cached)
            return
        }

        Log.d(T, "Cache miss — starting WebView: $cacheKey")
        extractViaWebView(context, videoId, cacheKey) { streamUrl ->
            if (streamUrl != null) saveToCache(context, cacheKey, streamUrl)
            callback(streamUrl)
        }
    }

    // ── إعادة المحاولة عند فشل المشغل ──────────────────────────────────
    fun retry(
        context: Context,
        videoId: String,
        channelName: String = "",
        callback: (String?) -> Unit
    ) {
        val cacheKey = buildCacheKey(videoId, channelName)
        clearCache(context, cacheKey)
        extractViaWebView(context, videoId, cacheKey) { streamUrl ->
            if (streamUrl != null) saveToCache(context, cacheKey, streamUrl)
            callback(streamUrl)
        }
    }

    // ── بناء مفتاح الكاش (videoId + اسم القناة) ─────────────────────────
    private fun buildCacheKey(videoId: String, channelName: String): String {
        val safeName = channelName.trim()
            .replace(" ", "_")
            .replace(Regex("[^A-Za-z0-9_\\u0600-\\u06FF]"), "")
            .take(40)
        return if (safeName.isNotEmpty()) "${videoId}_${safeName}" else videoId
    }

    // ── WebView مخفي ────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun extractViaWebView(
        context: Context,
        videoId: String,
        cacheKey: String,
        callback: (String?) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var done = false
        var webView: WebView? = null

        val finish: (String?) -> Unit = { result ->
            if (!done) {
                done = true
                handler.post {
                    try { webView?.stopLoading(); webView?.destroy() } catch (_: Exception) {}
                    webView = null
                    callback(result)
                }
            }
        }

        handler.postDelayed({ finish(null) }, 25_000L)

        val embedUrl = buildEmbedUrl(videoId)
        val ua = "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                 "AppleWebKit/537.36 (KHTML, like Gecko) " +
                 "Chrome/124.0.0.0 Mobile Safari/537.36"

        handler.post {
            webView = WebView(context).apply {
                layoutParams = FrameLayout.LayoutParams(1, 1)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                CookieManager.getInstance().setAcceptCookie(true)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = ua
                    cacheMode = WebSettings.LOAD_DEFAULT
                }

                webViewClient = object : WebViewClient() {

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null
                        if (done) return null

                        // البث المباشر: m3u8
                        if (reqUrl.contains(".m3u8") ||
                            reqUrl.contains("manifest.m3u8")) {
                            Log.d(T, "Intercepted HLS m3u8")
                            finish(reqUrl)
                            return null
                        }

                        // الفيديو المسجل: okcdn.ru مع sig=
                        if (reqUrl.contains("okcdn.ru") &&
                            reqUrl.contains("sig=")) {
                            Log.d(T, "Intercepted CDN video")
                            finish(reqUrl)
                            return null
                        }

                        return null
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (done) return

                        val cookies = CookieManager.getInstance()
                            .getCookie("https://ok.ru") ?: ""

                        val js = """
                            (function() {
                                try {
                                    var v = document.querySelector('video');
                                    if (v) { v.muted = true; v.play(); }
                                    var sels = [
                                        '.vid-play-big', '[data-action="play"]',
                                        '.video-layer_play-btn', '.vid-controls_play'
                                    ];
                                    sels.forEach(function(sel) {
                                        var b = document.querySelector(sel);
                                        if (b) b.click();
                                    });
                                    var cx = window.innerWidth/2, cy = window.innerHeight/2;
                                    var el = document.elementFromPoint(cx, cy);
                                    if (el) el.click();
                                } catch(e) {}
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)

                        if (cookies.isNotBlank()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val result = fetchWithCookies(videoId, cookies, ua)
                                if (result != null) finish(result)
                            }
                        }
                    }
                }

                webChromeClient = WebChromeClient()
                loadUrl(embedUrl)
            }
        }
    }

    // ── استدعاء API OK.ru بالكوكيز ──────────────────────────────────────
    private fun fetchWithCookies(
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
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write("gwt.requested=1") }

            if (conn.responseCode != 200) return null
            parseStreamUrl(conn.inputStream.bufferedReader().readText())
        } catch (e: Exception) {
            Log.w(T, "fetchWithCookies failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    // ── تحليل الرد ──────────────────────────────────────────────────────
    private fun parseStreamUrl(body: String): String? {
        return try {
            val root = JSONObject(body)

            // أولوية: HLS
            val hls = root.optString("hlsMasterPlaylistUrl", "")
                .replace("\\u0026", "&").ifEmpty { null }
                ?: root.optString("hlsManifestUrl", "")
                    .replace("\\u0026", "&").ifEmpty { null }
            if (hls != null) return hls

            // ثانياً: أعلى جودة فيديو مسجل
            val videos = root.optJSONArray("videos") ?: return null
            val map = mutableMapOf<String, String>()
            for (i in 0 until videos.length()) {
                val v = videos.getJSONObject(i)
                val u = v.optString("url", "").replace("\\u0026", "&")
                val r = v.optString("name", "")
                if (u.isNotEmpty() && r.isNotEmpty()) map[r] = u
            }
            listOf("1080", "720", "480", "360", "240").forEach { p ->
                map[p]?.let { return it }
            }
            map.values.firstOrNull()
        } catch (e: Exception) {
            Log.w(T, "parseStreamUrl failed")
            null
        }
    }

    // ── كاش محلي ────────────────────────────────────────────────────────
    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getCachedStream(ctx: Context, key: String): String? {
        val sp = prefs(ctx)
        val url = sp.getString("url_$key", null) ?: return null
        val ts = sp.getLong("ts_$key", 0L)
        return if (System.currentTimeMillis() - ts < URL_TTL_MS) url else null
    }

    private fun saveToCache(ctx: Context, key: String, url: String) {
        prefs(ctx).edit()
            .putString("url_$key", url)
            .putLong("ts_$key", System.currentTimeMillis())
            .apply()
    }

    private fun clearCache(ctx: Context, key: String) {
        prefs(ctx).edit()
            .remove("url_$key")
            .remove("ts_$key")
            .apply()
    }
}