package com.apix.app

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * VideoExtractor — a hidden 1×1 WebView "scraper".
 *
 * It loads an embed/web page that plays a stream, watches every network request
 * via [WebViewClient.shouldInterceptRequest], and captures the FIRST real media
 * URL (.m3u8 / .mp4 / .mkv) it sees together with the original request headers
 * (Referer / User-Agent / Cookie / Origin). Subtitle tracks (.vtt / .srt) are
 * captured opportunistically. As soon as a media URL is found the WebView is
 * destroyed and the result is delivered on the main thread — ready to hand to
 * ExoPlayer.
 *
 * Usage:
 * ```
 * VideoExtractor(context).extract(
 *     pageUrl = "https://host/embed/123",
 *     referer = "https://host/",
 *     userAgent = null,
 *     onResult = { result -> /* play result.url with result.headers */ },
 *     onError = { /* fallback */ }
 * )
 * ```
 */
class VideoExtractor(private val context: Context) {

    data class Result(
        val url: String,
        val headers: Map<String, String>,
        val subtitleUrl: String? = null
    )

    companion object {
        private const val TAG = "VideoExtractor"
        private const val DEFAULT_TIMEOUT_MS = 25_000L
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val MEDIA_REGEX = Regex("\\.(m3u8|mp4|mkv|mpd)(\\?|$)", RegexOption.IGNORE_CASE)
        private val SUBTITLE_REGEX = Regex("\\.(vtt|srt)(\\?|$)", RegexOption.IGNORE_CASE)
    }

    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var delivered = false
    private var capturedSubtitle: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun extract(
        pageUrl: String,
        referer: String? = null,
        userAgent: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onResult: (Result) -> Unit,
        onError: () -> Unit
    ) {
        main.post {
            try {
                val ua = userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_UA

                val timeoutRunnable = Runnable {
                    if (!delivered) {
                        delivered = true
                        Log.w(TAG, "extraction timed out for $pageUrl")
                        destroy()
                        onError()
                    }
                }
                main.postDelayed(timeoutRunnable, timeoutMs)

                webView = WebView(context).apply {
                    // 1×1 px — effectively invisible but still "rendered".
                    layoutParams = ViewGroup.LayoutParams(1, 1)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = ua
                        cacheMode = WebSettings.LOAD_NO_CACHE
                        loadsImagesAutomatically = false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null

                            // Opportunistically remember a subtitle track.
                            if (capturedSubtitle == null && SUBTITLE_REGEX.containsMatchIn(reqUrl)) {
                                capturedSubtitle = reqUrl
                            }

                            if (!delivered && MEDIA_REGEX.containsMatchIn(reqUrl)) {
                                delivered = true
                                val headers = buildHeaders(request, ua, referer)
                                main.removeCallbacks(timeoutRunnable)
                                main.post {
                                    val sub = capturedSubtitle
                                    destroy()
                                    onResult(Result(url = reqUrl, headers = headers, subtitleUrl = sub))
                                }
                            }
                            return null
                        }
                    }

                    // Headers for the top-level navigation (helps gated embeds).
                    val navHeaders = HashMap<String, String>()
                    referer?.takeIf { it.isNotBlank() }?.let { navHeaders["Referer"] = it }
                    loadUrl(pageUrl, navHeaders)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "extract failed", e)
                if (!delivered) { delivered = true; destroy(); onError() }
            }
        }
    }

    private fun buildHeaders(
        request: WebResourceRequest,
        ua: String,
        referer: String?
    ): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        // Start with whatever the WebView attached to the media request.
        try {
            request.requestHeaders?.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) out[k] = v
            }
        } catch (_: Throwable) {}
        // Guarantee the essentials.
        out.putIfAbsent("User-Agent", ua)
        referer?.takeIf { it.isNotBlank() }?.let { out.putIfAbsent("Referer", it) }
        return out
    }

    private fun destroy() {
        webView?.let { wv ->
            try { wv.stopLoading() } catch (_: Throwable) {}
            try { wv.destroy() } catch (_: Throwable) {}
        }
        webView = null
    }
}
