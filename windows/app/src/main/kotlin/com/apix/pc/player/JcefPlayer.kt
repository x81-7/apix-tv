package com.apix.pc.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.apix.pc.data.StreamConfig
import me.friwi.jcefmaven.CefAppBuilder
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.net.URLEncoder
import javax.swing.JPanel

/**
 * JCEF-backed player. Loads the SAME `shaka_player.html` shipped with the
 * Android app (copied into Windows resources at build time) capable of:
 * - DASH (.mpd) with ClearKey / Widevine
 * - HLS (.m3u8) via Shaka HLS support
 * - Custom request headers (User-Agent, Referer, X-Token, …)
 *
 * The HTML exposes a `window.AndroidHybrid` JS object which we wire to a CEF
 * message router so the same overlay-controls API used on Android works here.
 */
object JcefRuntime {
    @Volatile private var cefApp: CefApp? = null

    fun ensure(): CefApp = cefApp ?: synchronized(this) {
        cefApp ?: CefAppBuilder().apply {
            cefSettings.windowless_rendering_enabled = false
            cefSettings.cache_path = System.getenv("APPDATA")?.let { "$it/APiXTV/jcef-cache" }
            // Add Widevine CDM auto-install args
            addJcefArgs("--enable-features=WidevineL1", "--autoplay-policy=no-user-gesture-required")
        }.build().also { cefApp = it }
    }
}

/** Shared bus the Compose overlay reads from / writes commands to. */
class ShakaBridge {
    var state by mutableStateOf(PlaybackState(isLive = true))
    var qualities by mutableStateOf<List<Int>>(emptyList())
    var audioLanguages by mutableStateOf<List<String>>(emptyList())
    var lastError by mutableStateOf<String?>(null)

    /** Filled in by JcefPlayer once a browser is attached. */
    var execute: (String) -> Unit = {}

    fun playPause() { execute(if (state.isPlaying) "window.pauseVid()" else "window.playVid()") }
    fun seekBy(ms: Long) { execute("window.seekVid(${ms / 1000})") }
    fun seekTo(ms: Long) { execute("window.seekTo(${ms / 1000})") }
    fun setQuality(h: Int) { execute("window.setQ($h)") }
    fun setAudio(lang: String) { execute("window.setA('${lang.replace("'", "")}')") }
    fun setFit(mode: Int) { execute("window.setFit($mode)") }
}

@Composable
fun JcefPlayer(stream: StreamConfig, bridge: ShakaBridge, modifier: Modifier = Modifier) {
    val cefApp = remember { JcefRuntime.ensure() }
    val client: CefClient = remember { cefApp.createClient() }

    // Resolve the bundled HTML (Android-equivalent shaka_player.html) and pass
    // url + clearkey via query params, exactly like the Android WebView does.
    val pageUrl = remember(stream) {
        val html = Thread.currentThread().contextClassLoader.getResource("web/shaka_player.html")
            ?: error("shaka_player.html missing from resources")
        val ck = if (!stream.drmKeyId.isNullOrBlank() && !stream.drmKey.isNullOrBlank())
            "${stream.drmKeyId}:${stream.drmKey}" else ""
        val q = "?url=${URLEncoder.encode(stream.url, "UTF-8")}" +
                if (ck.isNotEmpty()) "&ck=${URLEncoder.encode(ck, "UTF-8")}" else ""
        html.toString() + q
    }
    val browser: CefBrowser = remember(pageUrl) { client.createBrowser(pageUrl, false, false) }

    // JS bridge: window.AndroidHybrid.{updateState,updateTracks,error}
    DisposableEffect(client) {
        val routerCfg = CefMessageRouter.CefMessageRouterConfig("AndroidHybridQuery", "AndroidHybridCancel")
        val router = CefMessageRouter.create(routerCfg)
        val handler = object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser?, frame: org.cef.browser.CefFrame?, queryId: Long,
                request: String?, persistent: Boolean, callback: CefQueryCallback?
            ): Boolean {
                request ?: return false
                runCatching {
                    val o = org.json.JSONObject(request)
                    when (o.optString("type")) {
                        "state" -> {
                            val pos = (o.optDouble("position", 0.0) * 1000).toLong()
                            val dur = (o.optDouble("duration", 0.0) * 1000).toLong()
                            bridge.state = bridge.state.copy(
                                isPlaying = !o.optBoolean("paused"),
                                isBuffering = o.optBoolean("buffering"),
                                position = pos, duration = dur,
                                isLive = dur <= 0L || dur > 8 * 60 * 60 * 1000L,
                            )
                        }
                        "tracks" -> {
                            bridge.qualities = o.optString("heights")
                                .split(",").mapNotNull { it.toIntOrNull() }
                            bridge.audioLanguages = o.optString("audios")
                                .split(",").filter { it.isNotBlank() }
                        }
                        "error" -> bridge.lastError = o.optString("message")
                    }
                }
                callback?.success("ok")
                return true
            }
        }
        router.addHandler(handler, true)
        client.addMessageRouter(router)
        // The HTML uses `window.AndroidHybrid.updateState(...)`. Inject a tiny
        // shim in the page that forwards to CEF's queryable endpoint.
        bridge.execute = { js -> browser.executeJavaScript(js, browser.url, 0) }
        onDispose { client.removeMessageRouter(router) }
    }

    // After load, install the AndroidHybrid shim so the Android-shipped HTML
    // works unchanged.
    LaunchedEffect(browser) {
        kotlinx.coroutines.delay(400)
        val shim = """
            (function(){
              if (window.AndroidHybrid) return;
              function send(o){ try { window.cefQuery({request: JSON.stringify(o), onSuccess:function(){}, onFailure:function(){}}); } catch(e){} }
              window.AndroidHybrid = {
                updateState: function(p,d,paused,buffering){ send({type:'state',position:p,duration:d,paused:paused,buffering:buffering}); },
                updateTracks: function(heights,audios){ send({type:'tracks',heights:heights,audios:audios}); },
                error: function(m){ send({type:'error',message:String(m)}); }
              };
            })();
        """.trimIndent()
        browser.executeJavaScript(shim, browser.url, 0)
    }

    // Inject custom headers on every request the browser makes for the stream URL.
    DisposableEffect(stream) {
        val handler = object : CefRequestHandlerAdapter() {
            override fun onBeforeBrowse(
                browser: CefBrowser?, frame: org.cef.browser.CefFrame?,
                request: CefRequest?, user_gesture: Boolean, is_redirect: Boolean
            ): Boolean {
                applyHeaders(request, stream)
                return false
            }
        }
        client.addRequestHandler(handler)
        onDispose { client.removeRequestHandler() }
    }

    SwingPanel(
        modifier = modifier.fillMaxSize(),
        factory = {
            JPanel(BorderLayout()).apply { add(browser.uiComponent, BorderLayout.CENTER) }
        }
    )
}

private fun applyHeaders(req: CefRequest?, s: StreamConfig) {
    req ?: return
    val map = HashMap<String, String>()
    s.userAgent?.takeIf { it.isNotBlank() }?.let { map["User-Agent"] = it }
    s.referer?.takeIf { it.isNotBlank() }?.let { map["Referer"] = it }
    s.customHeaders?.forEach { (k, v) -> map[k] = v }
    if (map.isNotEmpty()) req.setHeaderMap(map)
}
