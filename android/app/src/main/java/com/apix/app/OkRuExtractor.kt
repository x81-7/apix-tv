package com.apix.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.*
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

    // بث مباشر: 15 دقيقة فقط
    private const val LIVE_TTL_MS  = 15 * 60 * 1000L
    // فيديو مسجل: 3 ساعات
    private const val VIDEO_TTL_MS = 3 * 60 * 60 * 1000L

    // ── كشف نوع المحتوى ────────────────────────────────────────────────
    private fun isLiveStream(url: String) =
        url.contains(".m3u8") || url.contains("manifest") || url.contains("hls")

    private fun isCdnVideo(url: String) =
        (url.contains("okcdn.ru") || url.contains("vkuser.net")) && url.contains("sig=")

    // ── API العام ───────────────────────────────────────────────────────
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
            if (idx >= 0 && idx + 1 < parts.size)
                parts[idx + 1].split("?").first().trim()
            else null
        } catch (_: Exception) { null }
    }

    fun buildEmbedUrl(videoId: String) =
        "https://ok.ru/videoembed/$videoId?nochat=1&autoplay=1"

    // ── الدالة الرئيسية ─────────────────────────────────────────────────
    fun resolve(
        context: Context,
        rawUrl: String,
        channelName: String = "",
        callback: (String?) -> Unit
    ) {
        val videoId = extractVideoId(rawUrl)
        if (videoId == null) { callback(null); return }

        val cacheKey = buildCacheKey(videoId, channelName)
        val cached   = getCachedStream(context, cacheKey)
        if (cached != null) {
            Log.d(T, "Cache hit: $cacheKey")
            callback(cached)
            return
        }

        Log.d(T, "Cache miss → WebView: $cacheKey")
        startExtraction(context, videoId, cacheKey, callback)
    }

    // ── إعادة المحاولة — يحذف الكاش ويبدأ من جديد ──────────────────────
    fun retry(
        context: Context,
        videoId: String,
        channelName: String = "",
        callback: (String?) -> Unit
    ) {
        val cacheKey = buildCacheKey(videoId, channelName)
        clearCache(context, cacheKey)
        Log.d(T, "Retry — cleared cache: $cacheKey")
        startExtraction(context, videoId, cacheKey, callback)
    }

    // ── محرك الاستخراج الرئيسي ─────────────────────────────────────────
    private fun startExtraction(
        context: Context,
        videoId: String,
        cacheKey: String,
        callback: (String?) -> Unit
    ) {
        // أولاً: جرّب API مباشرة (أسرع وأموثق)
        CoroutineScope(Dispatchers.IO).launch {
            val ua = "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                     "AppleWebKit/537.36 (KHTML, like Gecko) " +
                     "Chrome/124.0.0.0 Mobile Safari/537.36"

            // نحتاج كوكيز أولاً — نجلبها بطلب GET بسيط
            val cookies = fetchCookiesViaGet(videoId, ua)

            if (cookies.isNotBlank()) {
                val streamUrl = fetchWithCookies(videoId, cookies, ua)
                if (streamUrl != null) {
                    Log.d(T, "API success (direct): $cacheKey")
                    saveToCacheWithTtl(context, cacheKey, streamUrl)
                    Handler(Looper.getMainLooper()).post { callback(streamUrl) }
                    return@launch
                }
            }

            // ثانياً: WebView كـ fallback
            Log.d(T, "API failed → fallback to WebView: $cacheKey")
            Handler(Looper.getMainLooper()).post {
                extractViaWebView(context, videoId, ua) { streamUrl ->
                    if (streamUrl != null) saveToCacheWithTtl(context, cacheKey, streamUrl)
                    callback(streamUrl)
                }
            }
        }
    }

    // ── جلب الكوكيز عبر GET بسيط ───────────────────────────────────────
    private fun fetchCookiesViaGet(videoId: String, ua: String): String {
        return try {
            val conn = URL("https://ok.ru/video/$videoId")
                .openConnection() as HttpURLConnection
            conn.requestMethod  = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout    = 8000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", ua)
            conn.setRequestProperty("Accept", "text/html,*/*")
            conn.responseCode // trigger connection
            val raw = conn.headerFields["Set-Cookie"]
                ?.joinToString("; ") { it.split(";").first() } ?: ""
            conn.disconnect()
            raw
        } catch (_: Exception) { "" }
    }

    // ── استدعاء API OK.ru مع الكوكيز ───────────────────────────────────
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
            conn.doOutput      = true
            conn.connectTimeout = 10000
            conn.readTimeout    = 12000
            conn.setRequestProperty("Content-Type",   "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent",     userAgent)
            conn.setRequestProperty("Cookie",         cookies)
            conn.setRequestProperty("Referer",        "https://ok.ru/video/$videoId")
            conn.setRequestProperty("Origin",         "https://ok.ru")
            conn.setRequestProperty("Accept",         "application/json, */*")
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

    // ── WebView كـ fallback ──────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun extractViaWebView(
        context: Context,
        videoId: String,
        ua: String,
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

        handler.postDelayed({ finish(null) }, 20_000L)

        webView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = ua
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
                    if (done) return null

                    // بث مباشر
                    if (reqUrl.contains(".m3u8")) {
                        Log.d(T, "WebView intercepted HLS")
                        finish(reqUrl)
                        return null
                    }

                    // فيديو CDN مسجل
                    if (isCdnVideo(reqUrl)) {
                        Log.d(T, "WebView intercepted CDN video")
                        finish(reqUrl)
                        return null
                    }

                    return null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (done) return

                    val cookies = CookieManager.getInstance().getCookie("https://ok.ru") ?: ""

                    // محاولة تشغيل الفيديو
                    view?.evaluateJavascript("""
                        (function(){
                            try {
                                var v = document.querySelector('video');
                                if(v){ v.muted=true; v.play(); }
                                ['#openvv-vplayer .vp-play','[data-action=play]',
                                 '.vid-play-big','.vid-controls_play'].forEach(function(s){
                                    var b=document.querySelector(s); if(b) b.click();
                                });
                            } catch(e){}
                        })();
                    """.trimIndent(), null)

                    // جرب API مباشرة بالكوكيز المستخرجة
                    if (cookies.isNotBlank()) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val result = fetchWithCookies(videoId, cookies, ua)
                            if (result != null) finish(result)
                        }
                    }
                }
            }

            webChromeClient = WebChromeClient()
            loadUrl(buildEmbedUrl(videoId))
        }
    }

    // ── تحليل الرد من API ───────────────────────────────────────────────
    private fun parseStreamUrl(body: String): String? {
        return try {
            val root = JSONObject(body)

            // أولوية: HLS مباشر
            val hls = root.optString("hlsMasterPlaylistUrl", "")
                .replace("\\u0026", "&").ifEmpty { null }
                ?: root.optString("hlsManifestUrl", "")
                    .replace("\\u0026", "&").ifEmpty { null }
            if (hls != null) return hls

            // أعلى جودة من الروابط المباشرة
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
        } catch (_: Exception) { null }
    }

    // ── كاش ذكي حسب نوع المحتوى ────────────────────────────────────────
    private fun saveToCacheWithTtl(ctx: Context, key: String, url: String) {
        val ttl = if (isLiveStream(url)) LIVE_TTL_MS else VIDEO_TTL_MS
        prefs(ctx).edit()
            .putString("url_$key", url)
            .putLong("ts_$key", System.currentTimeMillis())
            .putLong("ttl_$key", ttl)
            .apply()
        Log.d(T, "Cached (${ if (isLiveStream(url)) "LIVE 15min" else "VIDEO 3h" }): $key")
    }

    private fun getCachedStream(ctx: Context, key: String): String? {
        val sp  = prefs(ctx)
        val url = sp.getString("url_$key", null) ?: return null
        val ts  = sp.getLong("ts_$key", 0L)
        val ttl = sp.getLong("ttl_$key", VIDEO_TTL_MS)
        return if (System.currentTimeMillis() - ts < ttl) url else null
    }

    private fun clearCache(ctx: Context, key: String) {
        prefs(ctx).edit()
            .remove("url_$key")
            .remove("ts_$key")
            .remove("ttl_$key")
            .apply()
    }

    private fun buildCacheKey(videoId: String, channelName: String): String {
        val safe = channelName.trim()
            .replace(" ", "_")
            .replace(Regex("[^A-Za-z0-9_\\u0600-\\u06FF]"), "")
            .take(40)
        return if (safe.isNotEmpty()) "${videoId}_$safe" else videoId
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}