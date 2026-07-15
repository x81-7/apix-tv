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

/**
 * OkRuExtractor
 *
 * يُميّز بين نوعين من المحتوى:
 *
 * ── النوع 1: بث مباشر (Live Stream) ─────────────────────────────────────
 *   الرابط: ok.ru/videoembed/ID أو ok.ru/video/ID (قنوات مباشرة)
 *   الناتج: رابط .m3u8
 *   الطريقة: WebView يفتح صفحة embed ← يجلب كوكيز حقيقية
 *            ← API POST يعيد m3u8 ← يُمرر للمشغل مباشرة
 *   الكاش: 15 دقيقة (ينتهي سريعاً لطبيعة البث المباشر)
 *
 * ── النوع 2: فيديو مسجل (Recorded Video) ────────────────────────────────
 *   الرابط: ok.ru/video/ID (أرقام ID أطول عادةً)
 *   الناتج: رابط okcdn.ru أو vkuser.net (MP4 بدون امتداد)
 *   الطريقة: WebView ← يصطاد رابط CDN مباشرة من shouldInterceptRequest
 *            أو API POST يعيد قائمة videos[] ← نختار أعلى جودة
 *   الكاش: 3 ساعات
 *   الملاحظة: المشغل يتلقاه مع MimeType=VIDEO_MP4 الصريح
 */
object OkRuExtractor {

    private const val T = "OkRuExtractor"
    private const val PREFS = "okru_cache_v2"
    private const val LIVE_TTL_MS  = 15 * 60 * 1000L
    private const val VIDEO_TTL_MS = 3 * 60 * 60 * 1000L

    private val UA = "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                     "AppleWebKit/537.36 (KHTML, like Gecko) " +
                     "Chrome/124.0.0.0 Mobile Safari/537.36"

    // ═══════════════════════════════════════════════════════════════════
    // ── دوال عامة ───────────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

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

    /** هل الرابط الناتج من CDN (فيديو مسجل)؟ */
    fun isCdnVideoUrl(url: String) =
        (url.contains("okcdn.ru") || url.contains("vkuser.net")) &&
        url.contains("sig=")

    /** هل الرابط ناتج m3u8 (بث مباشر)؟ */
    private fun isHlsUrl(url: String) = url.contains(".m3u8")

    // ═══════════════════════════════════════════════════════════════════
    // ── الدخول الرئيسي ──────────────────────────────────────────────────
    // ═══════════════════════════════════════════════════════════════════

    /**
     * resolve() — يقرر تلقائياً النوع ويستخرج الرابط
     * callback يُعيد: Pair<String, String?>
     *   first  = URL الرابط المستخرج
     *   second = "hls" | "mp4" | null (لمعرفة كيف يمرره للمشغل)
     */
    fun resolve(
        context: Context,
        rawUrl: String,
        channelName: String = "",
        callback: (url: String?, type: String?) -> Unit
    ) {
        val videoId = extractVideoId(rawUrl)
        if (videoId == null) { callback(null, null); return }

        val cacheKey = buildCacheKey(videoId, channelName)

        // تحقق من الكاش
        val cached = getCachedStream(context, cacheKey)
        if (cached != null) {
            val type = if (isCdnVideoUrl(cached)) "mp4" else "hls"
            Log.d(T, "Cache hit [$type]: $cacheKey")
            callback(cached, type)
            return
        }

        Log.d(T, "Cache miss — starting WebView extraction: $cacheKey")

        // WebView هو المصدر الأساسي (يعمل دائماً مع ok.ru)
        extractViaWebView(context, videoId, cacheKey) { url, type ->
            callback(url, type)
        }
    }

    /** retry() — يحذف الكاش ويبدأ من جديد */
    fun retry(
        context: Context,
        videoId: String,
        channelName: String = "",
        callback: (url: String?, type: String?) -> Unit
    ) {
        val cacheKey = buildCacheKey(videoId, channelName)
        clearCache(context, cacheKey)
        Log.d(T, "Retry — cache cleared: $cacheKey")
        extractViaWebView(context, videoId, cacheKey, callback)
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── جزء 1: البث المباشر عبر WebView + API ────────────────────────
    // المبدأ: نفس طريقة apix.png لكن WebView يجلب الكوكيز بنفسه
    // ═══════════════════════════════════════════════════════════════════

    @SuppressLint("SetJavaScriptEnabled")
    private fun extractViaWebView(
        context: Context,
        videoId: String,
        cacheKey: String,
        callback: (url: String?, type: String?) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var done = false
        var webView: WebView? = null

        val finish: (String?, String?) -> Unit = { url, type ->
            if (!done) {
                done = true
                if (url != null) saveToCache(context, cacheKey, url)
                handler.post {
                    try { webView?.stopLoading(); webView?.destroy() }
                    catch (_: Exception) {}
                    webView = null
                    callback(url, type)
                }
            }
        }

        // مهلة 20 ثانية
        handler.postDelayed({ finish(null, null) }, 20_000L)

        handler.post {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled                = true
                    domStorageEnabled                = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString  = UA
                    cacheMode        = WebSettings.LOAD_DEFAULT
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

                        // ── جزء 1: اصطياد m3u8 (بث مباشر) ───────────
                        if (isHlsUrl(reqUrl)) {
                            Log.d(T, "Intercepted HLS m3u8 → LIVE")
                            finish(reqUrl, "hls")
                            return null
                        }

                        // ── جزء 2: اصطياد CDN (فيديو مسجل) ──────────
                        if (isCdnVideoUrl(reqUrl)) {
                            Log.d(T, "Intercepted CDN video → MP4")
                            finish(reqUrl, "mp4")
                            return null
                        }

                        return null
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (done) return

                        // جلب الكوكيز من WebView (الطريقة الموثوقة مع ok.ru)
                        val cookies = CookieManager.getInstance()
                            .getCookie("https://ok.ru") ?: ""

                        // ضغطة تشغيل لإجبار ok.ru على طلب البث
                        view?.evaluateJavascript("""
                            (function(){
                                try {
                                    var v = document.querySelector('video');
                                    if(v){ v.muted=true; v.play(); }
                                    ['[data-action=play]','.vid-play-big',
                                     '.vid-controls_play','#openvv-vplayer .vp-play',
                                     '.vp-play'].forEach(function(s){
                                        var b=document.querySelector(s);
                                        if(b) b.click();
                                    });
                                    // ضغطة مركز الشاشة
                                    var el=document.elementFromPoint(
                                        window.innerWidth/2, window.innerHeight/2);
                                    if(el) el.click();
                                } catch(e){}
                            })();
                        """.trimIndent(), null)

                        // استدعاء API بالكوكيز المجلوبة من WebView
                        // (مفيد عندما لا يُصطاد رابط من shouldInterceptRequest)
                        if (cookies.isNotBlank()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val result = fetchViaApi(videoId, cookies)
                                if (result != null) {
                                    val type = if (isHlsUrl(result)) "hls" else "mp4"
                                    finish(result, type)
                                }
                            }
                        }
                    }
                }

                webChromeClient = WebChromeClient()
                loadUrl(buildEmbedUrl(videoId))
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ── جزء 2: API POST لجلب رابط البث (مشترك بين النوعين) ──────────
    // يُستدعى بعد جلب الكوكيز من WebView
    // ═══════════════════════════════════════════════════════════════════

    private fun fetchViaApi(videoId: String, cookies: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://ok.ru/dk?cmd=videoPlayerMetadata&mid=$videoId")
                .openConnection() as HttpURLConnection
            conn.requestMethod  = "POST"
            conn.doOutput       = true
            conn.connectTimeout = 10000
            conn.readTimeout    = 12000
            conn.setRequestProperty("Content-Type",     "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent",       UA)
            conn.setRequestProperty("Cookie",           cookies)
            conn.setRequestProperty("Referer",          "https://ok.ru/video/$videoId")
            conn.setRequestProperty("Origin",           "https://ok.ru")
            conn.setRequestProperty("Accept",           "application/json, */*")
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write("gwt.requested=1")
            }
            if (conn.responseCode != 200) return null
            parseApiResponse(conn.inputStream.bufferedReader().readText())
        } catch (e: Exception) {
            Log.w(T, "API failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseApiResponse(body: String): String? {
        return try {
            val root = JSONObject(body)

            // جزء 1: HLS (بث مباشر)
            val hls = root.optString("hlsMasterPlaylistUrl", "")
                .replace("\\u0026", "&").ifEmpty { null }
                ?: root.optString("hlsManifestUrl", "")
                    .replace("\\u0026", "&").ifEmpty { null }
            if (hls != null) return hls

            // جزء 2: أعلى جودة فيديو مسجل
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

    // ═══════════════════════════════════════════════════════════════════
    // ── كاش ذكي (15 دقيقة للبث، 3 ساعات للفيديو) ─────────────────────
    // ═══════════════════════════════════════════════════════════════════

    private fun saveToCache(ctx: Context, key: String, url: String) {
        val ttl = if (isHlsUrl(url)) LIVE_TTL_MS else VIDEO_TTL_MS
        prefs(ctx).edit()
            .putString("u_$key", url)
            .putLong("t_$key", System.currentTimeMillis())
            .putLong("l_$key", ttl)
            .apply()
    }

    private fun getCachedStream(ctx: Context, key: String): String? {
        val sp  = prefs(ctx)
        val url = sp.getString("u_$key", null) ?: return null
        val ts  = sp.getLong("t_$key", 0L)
        val ttl = sp.getLong("l_$key", VIDEO_TTL_MS)
        return if (System.currentTimeMillis() - ts < ttl) url else null
    }

    private fun clearCache(ctx: Context, key: String) {
        prefs(ctx).edit()
            .remove("u_$key").remove("t_$key").remove("l_$key")
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