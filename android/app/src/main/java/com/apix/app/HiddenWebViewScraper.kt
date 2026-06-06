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
 * HiddenWebViewScraper — the integrated movie/series stream extractor.
 *
 * Movie/series METADATA comes from TMDB (via the worker). The actual PLAY links
 * are scraped here from the embed page returned by the panel link template
 * (e.g. https://vidsrc.../embed/movie?tmdb=550). Flow:
 *
 *   1. Load the embed URL in a hidden 1×1 WebView with JavaScript enabled.
 *   2. Many embed servers only generate the real stream AFTER a user gesture,
 *      so after the page finishes we inject JS that performs a "simulated click"
 *      in the centre of the page (and clicks common play-button selectors).
 *   3. Override shouldInterceptRequest and catch the FIRST .m3u8 (preferred) /
 *      .mp4 / .mkv request, plus any subtitle (.vtt / .srt).
 *   4. Capture the request Headers (Referer / User-Agent / Cookie / Origin),
 *      destroy the WebView immediately, and hand the result to ExoPlayer.
 *
 * Never throws on the caller thread; failures go through [onError].
 */
class HiddenWebViewScraper(private val context: Context) {

    data class Result(
        val url: String,
        val headers: Map<String, String>,
        val subtitleUrl: String? = null
    )

    companion object {
        private const val TAG = "HiddenScraper"
        private const val DEFAULT_TIMEOUT_MS = 25_000L
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        // Prefer HLS/DASH manifests; progressive files are also accepted.
        private val MEDIA_REGEX = Regex("\\.(m3u8|mpd|mp4|mkv)(\\?|$)", RegexOption.IGNORE_CASE)
        private val SUBTITLE_REGEX = Regex("\\.(vtt|srt)(\\?|$)", RegexOption.IGNORE_CASE)

        // JS that simulates a tap in the middle of the page + clicks likely play
        // buttons. Run a few times to defeat lazy/iframed players.
        private const val AUTO_CLICK_JS = """
            (function(){
              try {
                function fire(el){ if(!el) return;
                  ['mousedown','mouseup','click'].forEach(function(t){
                    try{ el.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window})); }catch(e){}
                  });
                  try{ el.click(); }catch(e){}
                }
                var cx = Math.floor(window.innerWidth/2), cy = Math.floor(window.innerHeight/2);
                fire(document.elementFromPoint(cx, cy));
                var sels = ['video','.play','.play-button','.vjs-big-play-button','#player','.jw-icon-display','button[aria-label*=play i]','.btn-play'];
                sels.forEach(function(s){ document.querySelectorAll(s).forEach(fire); });
                document.querySelectorAll('video').forEach(function(v){ try{ v.muted=false; v.play(); }catch(e){} });
              } catch(e){}
            })();
        """
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
                        Log.w(TAG, "scrape timed out for $pageUrl")
                        destroy()
                        onError()
                    }
                }
                main.postDelayed(timeoutRunnable, timeoutMs)

                webView = WebView(context).apply {
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

                        override fun onPageFinished(view: WebView?, url: String?) {
                            // Simulated click(s) to trigger lazy stream generation.
                            repeat(3) { attempt ->
                                main.postDelayed({
                                    if (!delivered) {
                                        try { view?.evaluateJavascript(AUTO_CLICK_JS, null) } catch (_: Throwable) {}
                                    }
                                }, 800L + attempt * 1200L)
                            }
                        }
                    }

                    val navHeaders = HashMap<String, String>()
                    referer?.takeIf { it.isNotBlank() }?.let { navHeaders["Referer"] = it }
                    loadUrl(pageUrl, navHeaders)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "scrape failed", e)
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
        try {
            request.requestHeaders?.forEach { (k, v) ->
                if (k.isNotBlank() && v.isNotBlank()) out[k] = v
            }
        } catch (_: Throwable) {}
        out.putIfAbsent("User-Agent", ua)
        referer?.takeIf { it.isNotBlank() }?.let { out.putIfAbsent("Referer", it) }
        return out
    }

    private fun destroy() {
        webView?.let { wv ->
            try { wv.stopLoading() } catch (_: Throwable) {}
            try { (wv.parent as? ViewGroup)?.removeView(wv) } catch (_: Throwable) {}
            try { wv.destroy() } catch (_: Throwable) {}
        }
        webView = null
    }
}
