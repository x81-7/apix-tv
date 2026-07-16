package com.apix.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.*
import org.json.JSONArray

/**
 * OkVideoex — استخراج الجودات لملفات الفيديو المسجلة
 */
object OkVideoex {

    private const val T  = "OkVideoex"

    private val UA = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

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

    fun resolve(
        context: Context,
        rawUrl: String,
        callback: (url: String?) -> Unit
    ) {
        val videoId = extractVideoId(rawUrl) ?: run { callback(null); return }
        Log.d(T, "Fetching video: $videoId")
        fetchViaWebView(context, videoId, callback)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
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

        // تقليل مهلة الانتظار إلى 15 ثانية بدل 20 (لأننا نستخدم JS الآن وهو أسرع بكثير)
        handler.postDelayed({ finish(null) }, 15_000L)

        handler.post {
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    userAgentString = UA
                }
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                // واجهة برمجية لجلب الدقات من داخل HTML الصفحة في أجزاء من الثانية (بدون انتظار API)
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onVideosExtracted(jsonArrayStr: String) {
                        if (done) return
                        try {
                            val videos = JSONArray(jsonArrayStr)
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
                                val encoded = Base64.encodeToString(m3u8String.toByteArray(), Base64.NO_WRAP)
                                finish("data:application/x-mpegURL;format=m3u8;base64,$encoded")
                            }
                        } catch (e: Exception) {
                            Log.e(T, "Failed to parse JS videos", e)
                        }
                    }
                }, "AndroidApix")

                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null
                        if (done) return null

                        // الخطة البديلة: إذا وجدنا رابط HLS جاهز يمر في الشبكة نلتقطه فوراً!
                        if ((reqUrl.contains("okcdn.ru") || reqUrl.contains("vkuser.net")) &&
                            reqUrl.contains("sig=") && reqUrl.contains(".m3u8")) {
                            Log.d(T, "Intercepted CDN M3U8 directly")
                            finish(reqUrl)
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (done) return

                        // حقن سكريبت لسحب جميع الجودات المخفية في HTML بسرعة فائقة
                        view?.evaluateJavascript("""
                            (function(){
                                try {
                                    var el = document.querySelector('[data-options]');
                                    if (el) {
                                        var opts = JSON.parse(el.getAttribute('data-options'));
                                        var meta = JSON.parse(opts.flashvars.metadata);
                                        if (meta && meta.videos) {
                                            AndroidApix.onVideosExtracted(JSON.stringify(meta.videos));
                                            return;
                                        }
                                    }
                                } catch(e) {}
                                
                                // إذا فشل الاستخراج من HTML، نجبر المشغل على العمل ليظهر الرابط في الشبكة
                                try {
                                    var v = document.querySelector('video');
                                    if(v){ v.muted=true; v.play(); }
                                    ['[data-action=play]', '.vid-play-big', '.vid-controls_play'].forEach(function(s){
                                        var b = document.querySelector(s); if(b) b.click();
                                    });
                                } catch(e) {}
                            })();
                        """.trimIndent(), null)
                    }
                }
                webChromeClient = WebChromeClient()
                loadUrl(buildEmbedUrl(videoId))
            }
        }
    }
}
