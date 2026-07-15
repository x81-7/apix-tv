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
 * OkRuExtractor — بث مباشر فقط (m3u8)
 * يُفعَّل عندما الرابط ok.ru بدون .mp4 في النهاية
 * الكاش: 15 دقيقة، يُعيد جلب جديد عند أي فشل
 */
object OkRuExtractor {

    private const val T     = "OkRuLive"
    private const val PREFS = "okru_live_cache"
    private const val TTL   = 15 * 60 * 1000L   // 15 دقيقة فقط

    private val UA = "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                     "AppleWebKit/537.36 (KHTML, like Gecko) " +
                     "Chrome/124.0.0.0 Mobile Safari/537.36"

    // ── كشف البث المباشر ───────────────────────────────────────────────
    fun isLiveUrl(url: String): Boolean {
        val u = url.lowercase().trim()
        // رابط ok.ru بدون .mp4 في النهاية = بث مباشر
        return (u.contains("ok.ru/video/") ||
                u.contains("ok.ru/videoembed/") ||
                u.contains("odnoklassniki.ru/video")) &&
               !u.endsWith(".mp4")
    }

    // للتوافق مع ComposeActivity
    fun isOkRuUrl(url: String) = isLiveUrl(url)

    fun extractVideoId(url: String): String? {
        return try {
            val clean = url.replace("https://","").replace("http://","")
                          .removeSuffix(".mp4")
            val parts = clean.split("/")
            val idx = parts.indexOfFirst { it == "video" || it == "videoembed" }
            if (idx >= 0 && idx + 1 < parts.size)
                parts[idx + 1].split("?").first().trim()
            else null
        } catch (_: Exception) { null }
    }

    fun buildEmbedUrl(videoId: String) =
        "https://ok.ru/videoembed/$videoId?nochat=1&autoplay=1"

    // ── الدخول الرئيسي ─────────────────────────────────────────────────
    fun resolve(
        context: Context,
        rawUrl: String,
        channelName: String = "",
        callback: (url: String?) -> Unit
    ) {
        val videoId = extractVideoId(rawUrl) ?: run { callback(null); return }
        val key = cacheKey(videoId, channelName)

        // الكاش أولاً
        val cached = getCache(context, key)
        if (cached != null) {
            Log.d(T, "Cache hit: $key")
            callback(cached)
            return
        }

        Log.d(T, "Cache miss → WebView: $key")
        fetchViaWebView(context, videoId, key, callback)
    }

    /** retry: يُحذف الكاش ويبدأ جلب جديد كلياً */
    fun retry(
        context: Context,
        videoId: String,
        channelName: String = "",
        callback: (url: String?) -> Unit
    ) {
        val key = cacheKey(videoId, channelName)
        clearCache(context, key)
        Log.d(T, "Retry → fresh fetch: $key")
        fetchViaWebView(context, videoId, key, callback)
    }

    // ── WebView يجلب الكوكيز ويصطاد m3u8 ──────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchViaWebView(
        context: Context,
        videoId: String,
        cacheKey: String,
        callback: (String?) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var done = false
        var webView: WebView? = null

        val finish: (String?) -> Unit = { url ->
            if (!done) {
                done = true
                if (url != null) saveCache(context, cacheKey, url)
                handler.post {
                    try { webView?.stopLoading(); webView?.destroy() } catch (_:Exception) {}
                    webView = null
                    callback(url)
                }
            }
        }

        handler.postDelayed({ finish(null) }, 20_000L)

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
                        view: WebView?, request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null
                        if (done) return null
                        // نصطاد m3u8 فقط
                        if (reqUrl.contains(".m3u8")) {
                            Log.d(T, "Intercepted HLS")
                            finish(reqUrl)
                        }
                        return null
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (done) return
                        val cookies = CookieManager.getInstance()
                            .getCookie("https://ok.ru") ?: ""

                        // تشغيل الفيديو لإجبار ok.ru على طلب البث
                        view?.evaluateJavascript("""
                            (function(){
                                try{
                                    var v=document.querySelector('video');
                                    if(v){v.muted=true;v.play();}
                                    ['[data-action=play]','.vid-play-big',
                                     '.vid-controls_play','.vp-play'].forEach(function(s){
                                        var b=document.querySelector(s);if(b)b.click();
                                    });
                                    var el=document.elementFromPoint(
                                        window.innerWidth/2,window.innerHeight/2);
                                    if(el)el.click();
                                }catch(e){}
                            })();
                        """.trimIndent(), null)

                        // API بالكوكيز كـ fallback
                        if (cookies.isNotBlank()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val result = apiPost(videoId, cookies)
                                if (result != null && result.contains(".m3u8")) {
                                    finish(result)
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

    // ── API POST ────────────────────────────────────────────────────────
    private fun apiPost(videoId: String, cookies: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://ok.ru/dk?cmd=videoPlayerMetadata&mid=$videoId")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "POST"; conn.doOutput = true
            conn.connectTimeout = 10000; conn.readTimeout = 12000
            conn.setRequestProperty("Content-Type","application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Cookie", cookies)
            conn.setRequestProperty("Referer","https://ok.ru/video/$videoId")
            conn.setRequestProperty("Origin","https://ok.ru")
            conn.setRequestProperty("Accept","application/json, */*")
            conn.setRequestProperty("X-Requested-With","XMLHttpRequest")
            OutputStreamWriter(conn.outputStream,"UTF-8").use{it.write("gwt.requested=1")}
            if (conn.responseCode != 200) return null
            val root = JSONObject(conn.inputStream.bufferedReader().readText())
            root.optString("hlsMasterPlaylistUrl","").replace("\\u0026","&").ifEmpty{null}
                ?: root.optString("hlsManifestUrl","").replace("\\u0026","&").ifEmpty{null}
        } catch (_:Exception){ null } finally { conn?.disconnect() }
    }

    // ── كاش ────────────────────────────────────────────────────────────
    private fun cacheKey(videoId: String, name: String): String {
        val s = name.trim().replace(" ","_")
            .replace(Regex("[^A-Za-z0-9_\\u0600-\\u06FF]"),"").take(40)
        return if (s.isNotEmpty()) "${videoId}_$s" else videoId
    }

    private fun getCache(ctx: Context, key: String): String? {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val u  = sp.getString("u_$key", null) ?: return null
        val ts = sp.getLong("t_$key", 0L)
        return if (System.currentTimeMillis() - ts < TTL) u else null
    }

    private fun saveCache(ctx: Context, key: String, url: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("u_$key", url).putLong("t_$key", System.currentTimeMillis())
            .apply()
    }

    private fun clearCache(ctx: Context, key: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove("u_$key").remove("t_$key").apply()
    }
}