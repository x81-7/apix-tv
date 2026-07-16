package com.apix.app

import android.annotation.SuppressLint
import android.content.Context
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

data class OkVideoQuality(
    val name: String,    // "1080", "720", "480", "360", "240"
    val url: String      // رابط okcdn.ru المباشر
)

object OkVideoex {

    private const val T  = "OkVideoex"

    private val UA = "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                     "AppleWebKit/537.36 (KHTML, like Gecko) " +
                     "Chrome/124.0.0.0 Mobile Safari/537.36"

    fun isVideoUrl(url: String): Boolean {
        val u = url.lowercase().trim()
        return (u.contains("ok.ru/video/") ||
                u.contains("ok.ru/videoembed/") ||
                u.contains("odnoklassniki.ru/video")) &&
               u.endsWith(".mp4")
    }

    fun extractVideoId(url: String): String? {
        return try {
            val clean = url.replace("https://","").replace("http://","").removeSuffix(".mp4")
            val parts = clean.split("/")
            val idx = parts.indexOfFirst { it == "video" || it == "videoembed" }
            if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1].split("?").first().trim()
            else null
        } catch (_: Exception) { null }
    }

    fun buildEmbedUrl(videoId: String) =
        "https://ok.ru/videoembed/$videoId?nochat=1&autoplay=1"

    /**
     * يُعيد قائمة بجميع الدقات المتاحة
     * callback: List<OkVideoQuality> — مرتبة من الأعلى للأسفل
     */
    fun resolve(
        context: Context,
        rawUrl: String,
        callback: (List<OkVideoQuality>) -> Unit
    ) {
        val videoId = extractVideoId(rawUrl) ?: run { callback(emptyList()); return }
        Log.d(T, "Fetching all qualities: $videoId")
        fetchViaWebView(context, videoId, callback)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchViaWebView(
        context: Context,
        videoId: String,
        callback: (List<OkVideoQuality>) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var done = false
        var webView: WebView? = null

        val finish: (List<OkVideoQuality>) -> Unit = { qualities ->
            if (!done) {
                done = true
                handler.post {
                    try { webView?.stopLoading(); webView?.destroy() } catch (_:Exception) {}
                    webView = null
                    callback(qualities)
                }
            }
        }

        handler.postDelayed({ finish(emptyList()) }, 20_000L)

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

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (done) return
                        val cookies = CookieManager.getInstance()
                            .getCookie("https://ok.ru") ?: ""

                        view?.evaluateJavascript("""
                            (function(){
                                try{
                                    var v=document.querySelector('video');
                                    if(v){v.muted=true;v.play();}
                                    ['[data-action=play]','.vid-play-big','.vid-controls_play'].forEach(function(s){
                                        var b=document.querySelector(s);if(b)b.click();
                                    });
                                } catch(e){}
                            })();
                        """.trimIndent(), null)

                        if (cookies.isNotBlank()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val qualities = apiPostAllQualities(videoId, cookies)
                                if (qualities.isNotEmpty()) finish(qualities)
                            }
                        }
                    }
                }
                webChromeClient = WebChromeClient()
                loadUrl(buildEmbedUrl(videoId))
            }
        }
    }

    // يجلب جميع الدقات ويرتبها من الأعلى للأسفل
    private fun apiPostAllQualities(videoId: String, cookies: String): List<OkVideoQuality> {
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
            if (conn.responseCode != 200) return emptyList()

            val root   = JSONObject(conn.inputStream.bufferedReader().readText())
            val videos = root.optJSONArray("videos") ?: return emptyList()

            val map = mutableMapOf<String, String>()
            for (i in 0 until videos.length()) {
                val v = videos.getJSONObject(i)
                val u = v.optString("url","").replace("\\u0026","&")
                val r = v.optString("name","")
                if (u.isNotEmpty() && r.isNotEmpty()) map[r] = u
            }

            // ترتيب من الأعلى للأسفل
            val order = listOf("1080","720","480","360","240")
            val result = mutableListOf<OkVideoQuality>()
            order.forEach { q -> map[q]?.let { result.add(OkVideoQuality(q+"p", it)) } }
            // إضافة أي دقات أخرى غير مذكورة في القائمة
            map.entries
                .filter { e -> order.none { q -> e.key == q } }
                .forEach { e -> result.add(OkVideoQuality(e.key, e.value)) }
            result

        } catch (_:Exception){ emptyList() } finally { conn?.disconnect() }
    }
}