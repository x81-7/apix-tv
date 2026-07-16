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

/**
 * OkVideoex — فيديو مسجل فقط (MP4/CDN)
 * يُفعَّل عندما الرابط ok.ru ينتهي بـ .mp4
 * لا يُخزَّن الرابط (يُجلَب من جديد في كل مرة)
 * الناتج: رابط okcdn.ru/vkuser.net يُمرَّر للمشغل كـ MP4 صريح
 */
object OkVideoex {

    private const val T  = "OkVideoex"

    private val UA = "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                     "AppleWebKit/537.36 (KHTML, like Gecko) " +
                     "Chrome/124.0.0.0 Mobile Safari/537.36"

    // ── كشف رابط الفيديو ───────────────────────────────────────────────
    fun isVideoUrl(url: String): Boolean {
        val u = url.lowercase().trim()
        return (u.contains("ok.ru/video/") ||
                u.contains("ok.ru/videoembed/") ||
                u.contains("odnoklassniki.ru/video")) &&
               u.endsWith(".mp4")
    }

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

    // ── الدخول الرئيسي (بدون كاش) ──────────────────────────────────────
    fun resolve(
        context: Context,
        rawUrl: String,
        callback: (url: String?) -> Unit
    ) {
        val videoId = extractVideoId(rawUrl) ?: run { callback(null); return }
        Log.d(T, "Fetching video (no cache): $videoId")
        fetchViaWebView(context, videoId, callback)
    }

    // ── WebView يصطاد رابط CDN ──────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchViaWebView(
        context: Context,
        videoId: String,
        callback: (String?) -> Unit
    ) {
        val handler = Handler(Looper.getMainLooper())
        var done = false
        var webView: WebView? = null

        val finish: (String?) -> Unit = { url ->
            if (!done) {
                done = true
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
                     
                        return super.shouldInterceptRequest(view, request)
                    }

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
                                    ['[data-action=play]','.vid-play-big',
                                     '.vid-controls_play'].forEach(function(s){
                                        var b=document.querySelector(s);if(b)b.click();
                                    });
                                    var el=document.elementFromPoint(
                                        window.innerWidth/2,window.innerHeight/2);
                                    if(el)el.click();
                                }catch(e){}
                            })();
                        """.trimIndent(), null)

                        if (cookies.isNotBlank()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val result = apiPost(videoId, cookies)
                                if (result != null) finish(result)
                            }
                        }
                    }
                }
                webChromeClient = WebChromeClient()
                loadUrl(buildEmbedUrl(videoId))
            }
        }
    }

    // ── API لجمع كل الجودات وتوليد M3U8 ───────────────────────────────────────
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
            val videos = root.optJSONArray("videos") ?: return null
            
            val sb = java.lang.StringBuilder()
            sb.append("#EXTM3U\n")
            var hasValidTracks = false

            for (i in 0 until videos.length()) {
                val v = videos.getJSONObject(i)
                val url = v.optString("url", "").replace("\\u0026", "&")
                val name = v.optString("name", "").lowercase()

                if (url.isNotEmpty() && name.isNotEmpty()) {
                    hasValidTracks = true
                    val height = when (name) {
                        "mobile" -> 144
                        "lowest" -> 240
                        "sd" -> 480
                        "hd" -> 720
                        "full" -> 1080
                        "quad" -> 1440
                        "ultra" -> 2160
                        else -> name.filter { it.isDigit() }.toIntOrNull() ?: 360
                    }
                    val bandwidth = height * 1000 * 2
                    val width = (height * 16) / 9
                    
                    sb.append("#EXT-X-STREAM-INF:BANDWIDTH=$bandwidth,RESOLUTION=${width}x$height,NAME=\"$name\"\n")
                    sb.append(url).append("\n")
                }
            }

            if (hasValidTracks) {
                val m3u8String = sb.toString()
                val encoded = android.util.Base64.encodeToString(m3u8String.toByteArray(), android.util.Base64.NO_WRAP)
                "data:application/x-mpegURL;format=m3u8;base64,$encoded"
            } else {
                null
            }
        } catch (_:Exception){ null } finally { conn?.disconnect() }
    }
}