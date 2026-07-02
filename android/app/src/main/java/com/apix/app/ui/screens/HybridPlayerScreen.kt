package com.apix.app.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.apix.app.data.PlayerConfig

import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.MediumRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL


// ===== Custom Outline Icons =====
private val PlayOutlineIcon: ImageVector by lazy { ImageVector.Builder(name = "PlayOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply { path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.White), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { moveTo(8f, 6f); lineTo(8f, 18f); lineTo(18f, 12f); close() } }.build() }
private val PauseOutlineIcon: ImageVector by lazy { ImageVector.Builder(name = "PauseOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply { path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.White), strokeLineWidth = 1.5f, strokeLineJoin = StrokeJoin.Round) { moveTo(6f, 7f); arcTo(2f, 2f, 0f, false, true, 10f, 7f); lineTo(10f, 17f); arcTo(2f, 2f, 0f, false, true, 6f, 17f); close(); moveTo(14f, 7f); arcTo(2f, 2f, 0f, false, true, 18f, 7f); lineTo(18f, 17f); arcTo(2f, 2f, 0f, false, true, 14f, 17f); close() } }.build() }
private val ForwardOutlineIcon: ImageVector by lazy { ImageVector.Builder(name = "ForwardOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply { path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.White), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { moveTo(9f, 7f); lineTo(14f, 12f); lineTo(9f, 17f); moveTo(15f, 7f); lineTo(20f, 12f); lineTo(15f, 17f) } }.build() }
private val RewindOutlineIcon: ImageVector by lazy { ImageVector.Builder(name = "RewindOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply { path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.White), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { moveTo(15f, 7f); lineTo(10f, 12f); lineTo(15f, 17f); moveTo(9f, 7f); lineTo(4f, 12f); lineTo(9f, 17f) } }.build() }
private val SettingsOutlineIcon: ImageVector by lazy { ImageVector.Builder(name = "SettingsOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply { path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.White), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { moveTo(19.14f, 12.94f); curveTo(19.18f, 12.63f, 19.2f, 12.31f, 19.2f, 12f); curveTo(19.2f, 11.69f, 19.18f, 11.37f, 19.14f, 11.06f); lineTo(21.17f, 9.48f); curveTo(21.35f, 9.34f, 21.4f, 9.07f, 21.29f, 8.87f); lineTo(19.37f, 5.55f); curveTo(19.25f, 5.33f, 19f, 5.26f, 18.78f, 5.33f); lineTo(16.39f, 6.29f); curveTo(15.89f, 5.91f, 15.36f, 5.59f, 14.77f, 5.35f); lineTo(14.41f, 2.81f); curveTo(14.37f, 2.57f, 14.17f, 2.4f, 13.93f, 2.4f); lineTo(10.09f, 2.4f); curveTo(9.85f, 2.4f, 9.66f, 2.57f, 9.62f, 2.81f); lineTo(9.26f, 5.35f); curveTo(8.67f, 5.59f, 8.13f, 5.92f, 7.64f, 6.29f); lineTo(5.25f, 5.33f); curveTo(5.03f, 5.25f, 4.78f, 5.33f, 4.66f, 5.55f); lineTo(2.74f, 8.87f); curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f); lineTo(4.89f, 11.06f); curveTo(4.85f, 11.37f, 4.81f, 11.69f, 4.81f, 12f); curveTo(4.81f, 12.31f, 4.83f, 12.63f, 4.87f, 12.94f); lineTo(2.84f, 14.52f); curveTo(2.66f, 14.66f, 2.61f, 14.93f, 2.73f, 15.13f); lineTo(4.65f, 18.45f); curveTo(4.77f, 18.67f, 5.02f, 18.74f, 5.24f, 18.67f); lineTo(7.63f, 17.71f); curveTo(8.13f, 18.09f, 8.66f, 18.41f, 9.25f, 18.65f); lineTo(9.61f, 21.19f); curveTo(9.66f, 21.43f, 9.85f, 21.6f, 10.09f, 21.6f); lineTo(13.93f, 21.6f); curveTo(14.17f, 21.6f, 14.37f, 21.43f, 14.4f, 21.19f); lineTo(14.76f, 18.65f); curveTo(15.35f, 18.41f, 15.89f, 18.09f, 16.38f, 17.71f); lineTo(18.77f, 18.67f); curveTo(18.99f, 18.75f, 19.24f, 18.67f, 19.36f, 18.45f); lineTo(21.28f, 15.13f); curveTo(21.4f, 14.91f, 21.35f, 14.66f, 21.16f, 14.52f); lineTo(19.14f, 12.94f); close(); moveTo(12f, 15.6f); curveTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12f); curveTo(8.4f, 10.02f, 10.02f, 8.4f, 12f, 8.4f); curveTo(13.98f, 8.4f, 15.6f, 10.02f, 15.6f, 12f); curveTo(15.6f, 13.98f, 13.98f, 15.6f, 12f, 15.6f); close() } }.build() }
private val PipOutlineIcon: ImageVector by lazy { ImageVector.Builder(name = "PipOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply { path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.White), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { moveTo(9f, 19f); lineTo(5f, 19f); arcTo(2f, 2f, 0f, false, true, 3f, 17f); lineTo(3f, 7f); arcTo(2f, 2f, 0f, false, true, 5f, 5f); lineTo(19f, 5f); arcTo(2f, 2f, 0f, false, true, 21f, 7f); lineTo(21f, 10f); moveTo(13f, 13f); lineTo(19f, 13f); arcTo(2f, 2f, 0f, false, true, 21f, 15f); lineTo(21f, 17f); arcTo(2f, 2f, 0f, false, true, 19f, 19f); lineTo(13f, 19f); arcTo(2f, 2f, 0f, false, true, 11f, 17f); lineTo(11f, 15f); arcTo(2f, 2f, 0f, false, true, 13f, 13f); close() } }.build() }
private val ResizeOutlineIcon: ImageVector by lazy { ImageVector.Builder(name = "ResizeOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply { path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.White), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { moveTo(15f, 3f); lineTo(21f, 3f); lineTo(21f, 9f); moveTo(9f, 21f); lineTo(3f, 21f); lineTo(3f, 15f); moveTo(21f, 3f); lineTo(14f, 10f); moveTo(3f, 21f); lineTo(10f, 14f) } }.build() }
private val CastOutlineIcon: ImageVector by lazy { ImageVector.Builder(name = "CastOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply { path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.White), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { moveTo(2f, 16.1f); arcTo(5f, 5f, 0f, false, true, 5.9f, 20f); moveTo(2f, 12.05f); arcTo(9f, 9f, 0f, false, true, 9.95f, 20f); moveTo(2f, 8f); arcTo(13f, 13f, 0f, false, true, 14f, 20f); moveTo(2f, 20f); lineTo(2.01f, 20f); moveTo(20f, 4f); lineTo(4f, 4f); moveTo(20f, 4f); lineTo(20f, 20f); lineTo(14f, 20f) } }.build() }
private val BackOutlineIcon: ImageVector by lazy { ImageVector.Builder(name = "BackOutline", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply { path(fill = SolidColor(Color.Transparent), stroke = SolidColor(Color.White), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) { moveTo(19f, 12f); lineTo(5f, 12f); moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f) } }.build() }

@Composable
private fun HybridControlButton(icon: ImageVector, contentDescription: String, size: Int = 44, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isTv = isSystemInTvMode()
    val isHighlighted = isFocused || isPressed
    val scale by animateFloatAsState(if (isHighlighted) 1.2f else 1f, label = "btnScale")
    
    Box(
        modifier = Modifier
            .size(size.dp)
            .scale(scale)
            .then(if (isTv && isFocused) Modifier.border(3.dp, Color.White, CircleShape) else if (isFocused) Modifier.border(2.dp, Gold, CircleShape) else Modifier)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester).focusable(interactionSource = interactionSource) else Modifier.focusable(interactionSource = interactionSource)), 
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size((size * 0.65f).dp))
    }
}

private fun formatHybridTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun HybridPlayerScreen(config: PlayerConfig, onBack: () -> Unit, onSwitchEngine: ((PlayerConfig, String) -> Unit)? = null) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isTv = isSystemInTvMode()

    // Edge-to-edge + draw behind the display cutout (notch) + immersive so the
    // Shaka/JW player fills the whole screen with no notch bar. Restored on exit.
    DisposableEffect(activity) {
        val window = activity?.window
        var prevCutout = -100
        if (window != null) {
            try {
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    prevCutout = window.attributes.layoutInDisplayCutoutMode
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode =
                            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } catch (_: Throwable) {}
        }
        onDispose {
            if (window != null) {
                try {
                    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, true)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && prevCutout != -100) {
                        window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = prevCutout }
                    }
                    val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                } catch (_: Throwable) {}
            }
        }
    }



    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    // State variables
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // UI Dialogs
    var showTrackDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showFallbackServerDialog by remember { mutableStateOf(false) }
    var currentFallbackIndex by remember { mutableIntStateOf(-1) }
    var showAudioSourceDialog by remember { mutableStateOf(false) }
    
    // Lists from Web
    var qualities by remember { mutableStateOf<List<Int>>(emptyList()) }
    var audios by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedQuality by remember { mutableIntStateOf(-1) }
    var selectedAudio by remember { mutableStateOf("") }
    
    var currentServerUrl by remember { mutableStateOf(config.url) }
    var resolvedConfig by remember { mutableStateOf(config) }

    var controlsResetKey by remember { mutableLongStateOf(0L) }
    val pipFocusRequester = remember { FocusRequester() }

    val initialResizeMode = remember {
        when (config.forcedAspectRatio) {
            "fit" -> 0
            "stretch", "fill" -> 1
            "16:9", "4:3" -> 2
            else -> 0
        }
    }
    var currentResizeMode by remember { mutableIntStateOf(initialResizeMode) }
    val canChangeResize = !config.lockAspectRatio

    LaunchedEffect(showControls, controlsResetKey) {
        if (showControls) { delay(4000); showControls = false }
    }

    LaunchedEffect(showControls) {
        if (showControls && isTv) {
            delay(100)
            try { pipFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // Function to reload stream
    fun loadWebViewStream(streamUrl: String, cfg: PlayerConfig) {
        errorMessage = null
        val baseUrl = if (cfg.hybridPlayerType == "jw") {
            "file:///android_asset/jw_player.html"
        } else {
            "file:///android_asset/shaka_player.html"
        }
        val enc: (String) -> String = { java.net.URLEncoder.encode(it, "UTF-8") }
        val sb = StringBuilder("$baseUrl?url=${enc(streamUrl)}")

        cfg.drm?.let { drm ->
            val currentKeyId = drm.keyId
            val currentKey = drm.key
            if (!currentKeyId.isNullOrEmpty() && !currentKey.isNullOrEmpty()) {
                val kid = currentKeyId.replace(Regex("[^a-fA-F0-9]"), "")
                val finalKey = currentKey.replace(Regex("[^a-fA-F0-9]"), "")
                sb.append("&ck=$kid:$finalKey")
            }
        }
        // Aspect mode + lock from panel → applied by HTML
        if (!cfg.forcedAspectRatio.isNullOrBlank()) sb.append("&aspect=").append(enc(cfg.forcedAspectRatio!!))
        if (cfg.lockAspectRatio) sb.append("&lock=1")
        // Smart logo overlay → forwarded to <img id="logo">
        cfg.logoOverlay?.let { l ->
            if (!l.url.isNullOrBlank()) {
                sb.append("&logo=").append(enc(l.url!!))
                if (!l.position.isNullOrBlank()) sb.append("&logoPos=").append(enc(l.position!!))
                sb.append("&logoX=").append(l.offsetX)
                sb.append("&logoY=").append(l.offsetY)
                sb.append("&logoW=").append(l.width)
                sb.append("&logoOpa=").append(l.opacity)
            }
        }
        // External audio: keep main video muted, headless audio in <video id="audio2">
        cfg.audioSources?.firstOrNull()?.url?.takeIf { it.isNotBlank() }?.let { audioUrl ->
            sb.append("&audio2=").append(enc(audioUrl))
        }
        webViewRef?.loadUrl(sb.toString())
    }

    // Multi-server fallback for the Hybrid/Shaka engine. Returns true if a next
    // server was found (either loaded here or handed to another engine).
    fun tryNextFallback(): Boolean {
        val fbList = resolvedConfig.fallbackServers ?: emptyList()
        val nextIdx = currentFallbackIndex + 1
        val nextFb = fbList.getOrNull(nextIdx) ?: return false
        if (nextFb.url.isNullOrEmpty()) return false
        currentFallbackIndex = nextIdx
        val merged = mergeFallbackConfig(resolvedConfig, nextFb).copy(
            fallbackServers = resolvedConfig.fallbackServers
        )
        // Cross-engine: hand a native (Exo) fallback back to the native player.
        if (engineForPlayerType(nextFb.playerType) == ENGINE_NATIVE && onSwitchEngine != null) {
            onSwitchEngine.invoke(merged, ENGINE_NATIVE)
            return true
        }
        resolvedConfig = merged
        currentServerUrl = merged.url
        isBuffering = true
        loadWebViewStream(merged.url, merged)
        return true
    }


    // Dynamic API fetcher
    LaunchedEffect(config) {
        if (config.dynamicApi?.enabled == true && !config.dynamicApi?.endpoint.isNullOrEmpty()) {
            try {
                val apiConfig = withContext(Dispatchers.IO) {
                    var conn: HttpURLConnection? = null
                    try {
                        val endpoint = config.dynamicApi!!.endpoint!!
                        val urlStr = if (!config.dynamicApi!!.channelIdParam.isNullOrEmpty()) {
                            if (endpoint.contains("?")) "$endpoint&${config.dynamicApi!!.channelIdParam}=${config.title}"
                            else "$endpoint?${config.dynamicApi!!.channelIdParam}=${config.title}"
                        } else endpoint

                        conn = URL(urlStr).openConnection() as HttpURLConnection
                        conn.requestMethod = config.dynamicApi!!.method ?: "GET"
                        conn.connectTimeout = 15000
                        conn.readTimeout = 15000
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                        config.dynamicApi!!.headers?.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                        conn.instanceFollowRedirects = true

                        if (conn.responseCode == 200) {
                            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText().trim()
                            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
                            val newConfig = config.copy()

                            if (json.has("url")) newConfig.url = json["url"].asString
                            if (json.has("headers")) {
                                val h = json["headers"].asJsonObject
                                val headers = mutableMapOf<String, String>()
                                h.entrySet().forEach { headers[it.key] = it.value.asString }
                                newConfig.customHeaders = headers
                            }
                            if (json.has("drm")) {
                                val d = json["drm"].asJsonObject
                                newConfig.drm = com.apix.app.data.PlayerDrm(
                                    licenseUrl = d.get("licenseUrl")?.asString,
                                    scheme = d.get("scheme")?.asString,
                                    keyId = d.get("keyId")?.asString,
                                    key = d.get("key")?.asString
                                )
                            }
                            newConfig
                        } else null
                    } finally { conn?.disconnect() }
                }
                if (apiConfig != null) {
                    resolvedConfig = apiConfig
                    currentServerUrl = apiConfig.url
                    loadWebViewStream(apiConfig.url, apiConfig)
                } else {
                    loadWebViewStream(config.url, config)
                }
            } catch (e: Exception) {
                loadWebViewStream(config.url, config)
            }
        } else {
            loadWebViewStream(config.url, config)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { keyEvent ->
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        val key = keyEvent.nativeKeyEvent?.keyCode ?: 0
                        val isDpadKey = key == KeyEvent.KEYCODE_DPAD_UP ||
                                key == KeyEvent.KEYCODE_DPAD_DOWN ||
                                key == KeyEvent.KEYCODE_DPAD_LEFT ||
                                key == KeyEvent.KEYCODE_DPAD_RIGHT ||
                                key == KeyEvent.KEYCODE_DPAD_CENTER ||
                                key == KeyEvent.KEYCODE_ENTER

                        if (isDpadKey) {
                            if (!showControls) showControls = true
                            controlsResetKey = System.currentTimeMillis()
                        }
                    }
                    false
                }
        ) {
            
            // 1. المتصفح المخفي
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewRef = this
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(android.graphics.Color.BLACK)

                        settings.apply {
                            javaScriptEnabled = true; domStorageEnabled = true; mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = true; allowFileAccessFromFileURLs = true; allowUniversalAccessFromFileURLs = true
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                        }
                        CookieManager.getInstance().setAcceptCookie(true)

                        addJavascriptInterface(object : Any() {
                            @JavascriptInterface
                            fun updateState(time: Float, dur: Float, paused: Boolean, buffering: Boolean) {
                                Handler(Looper.getMainLooper()).post {
                                    currentPosition = (time * 1000).toLong()
                                    duration = if (dur.isNaN()) 0L else (dur * 1000).toLong()
                                    isPlaying = !paused
                                    isBuffering = buffering
                                }
                            }
                            @JavascriptInterface
                            fun updateTracks(qStr: String, aStr: String) {
                                Handler(Looper.getMainLooper()).post {
                                    qualities = if(qStr.isEmpty()) emptyList() else qStr.split(",").mapNotNull { it.toIntOrNull() }
                                    audios = if(aStr.isEmpty()) emptyList() else aStr.split(",")
                                }
                            }
                            @JavascriptInterface
                            fun error(msg: String) {
                                Handler(Looper.getMainLooper()).post {
                                    // Try the next fallback server before surfacing the error.
                                    if (!tryNextFallback()) errorMessage = msg
                                }
                            }
                        }, "AndroidHybrid")

                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        // URL is loaded via LaunchedEffect
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. الطبقة الزجاجية للتحكم باللمس فوق الـ WebView
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showControls = !showControls }
            )

            // اللوجو (Logo) المطابق للأساسي
            resolvedConfig.logoOverlay?.let { logo ->
                if (!logo.url.isNullOrEmpty()) {
                    val alignment = when (logo.position) {
                        "top-left" -> Alignment.TopStart
                        "top-right" -> Alignment.TopEnd
                        "bottom-left" -> Alignment.BottomStart
                        "bottom-right" -> Alignment.BottomEnd
                        else -> Alignment.TopEnd
                    }
                    Box(
                        modifier = Modifier.align(alignment).padding(
                            start = (logo.offsetX).dp, top = (logo.offsetY).dp,
                            end = (logo.offsetX).dp, bottom = (logo.offsetY).dp
                        )
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(logo.url),
                            contentDescription = "Logo",
                            modifier = Modifier.size(width = (logo.width ?: 80).dp, height = (logo.height ?: 40).dp),
                            alpha = logo.opacity ?: 1.0f
                        )
                    }
                }
            }

            // علامة التحميل
            if (isBuffering && currentPosition < 1000L && errorMessage == null) {
                CircularProgressIndicator(color = MediumRed, strokeWidth = 3.dp, modifier = Modifier.size(44.dp).align(Alignment.Center))
            }

            // رسالة الخطأ
            errorMessage?.let { err ->
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, null, tint = MediumRed, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(err, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(32.dp))
                        Spacer(Modifier.height(24.dp))
                        HybridControlButton(icon = BackOutlineIcon, contentDescription = "إغلاق", onClick = onBack)
                    }
                }
            }

            // ===== تصميم المشغل الأصلي (Compose) =====
            AnimatedVisibility(visible = showControls && errorMessage == null, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize()) {
                    // الشريط العلوي
                    Row(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent))).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        HybridControlButton(icon = BackOutlineIcon, contentDescription = "Back", size = 36, onClick = onBack)
                        Text(text = resolvedConfig.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 300.dp))
                    }

                    // الشريط السفلي والتحكم
                    Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f)))).padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatHybridTime(currentPosition), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp))
                            Slider(
                                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                                onValueChange = { webViewRef?.evaluateJavascript("window.seekTo(${(it * duration) / 1000f});", null) },
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFE50914), inactiveTrackColor = Color(0x44FFFFFF)),
                                modifier = Modifier.weight(1f).height(16.dp).focusable()
                            )
                            Text(formatHybridTime(duration), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            // الأزرار اليسرى: تقديم وتأخير
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                HybridControlButton(icon = RewindOutlineIcon, contentDescription = "Rewind", size = 38) { webViewRef?.evaluateJavascript("window.seekVid(-10);", null) }
                                HybridControlButton(icon = if (isPlaying) PauseOutlineIcon else PlayOutlineIcon, contentDescription = "Play/Pause", size = 44) {
                                    webViewRef?.evaluateJavascript(if (isPlaying) "window.pauseVid();" else "window.playVid();", null)
                                }
                                HybridControlButton(icon = ForwardOutlineIcon, contentDescription = "Forward", size = 38) { webViewRef?.evaluateJavascript("window.seekVid(10);", null) }
                            }

                            // الأزرار اليمنى
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                // الصوتيات الخارجية (لوحة التحكم)
                                if (!resolvedConfig.audioSources.isNullOrEmpty()) {
                                    HybridControlButton(icon = Icons.Default.Audiotrack, contentDescription = "External Audio", size = 32) { showAudioSourceDialog = true }
                                }
                                // السيرفرات الديناميكية
                                if (!resolvedConfig.servers.isNullOrEmpty()) {
                                    HybridControlButton(icon = CastOutlineIcon, contentDescription = "Servers", size = 32) { showServerDialog = true }
                                }
                                // سيرفرات احتياطية متعددة (Fallback) مع دعم تبديل المشغل
                                if (!resolvedConfig.fallbackServers.isNullOrEmpty()) {
                                    HybridControlButton(icon = CastOutlineIcon, contentDescription = "Fallback Servers", size = 32) { showFallbackServerDialog = true }
                                }
                                // قائمة الجودة والصوت المدمج
                                HybridControlButton(icon = SettingsOutlineIcon, contentDescription = "Settings", size = 32) { showTrackDialog = true }

                                // تغيير الأبعاد
                                if (canChangeResize) {
                                    HybridControlButton(icon = ResizeOutlineIcon, contentDescription = "Resize", size = 32) {
                                        currentResizeMode = (currentResizeMode + 1) % 3
                                        webViewRef?.evaluateJavascript("window.setFit($currentResizeMode);", null)
                                    }
                                }
                                // وضع الشاشة المصغرة (PiP)
                                HybridControlButton(icon = PipOutlineIcon, contentDescription = "PiP", size = 32, focusRequester = pipFocusRequester) {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                        try { activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ===== النوافذ المنبثقة (Dialogs) المطابقة 100% =====
            
            // 1. الجودة والصوت الداخلي (TrackSelectionDialog Hybrid)
            if (showTrackDialog) {
                var selectedTab by remember { mutableIntStateOf(0) }
                val tabs = listOf("الجودة", "الصوت")
                
                Dialog(onDismissRequest = { showTrackDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                    Box(modifier = Modifier.fillMaxWidth(0.45f).fillMaxHeight(if (isTv) 0.65f else 0.85f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111)).border(if (isTv) 2.dp else 0.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))) {
                        Column(Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0A0A0A)).padding(top = 8.dp)) {
                                tabs.forEachIndexed { index, title ->
                                    val isActive = selectedTab == index
                                    val tabInteraction = remember { MutableInteractionSource() }
                                    val tabFocused by tabInteraction.collectIsFocusedAsState()
                                    Column(modifier = Modifier.weight(1f).clickable(interactionSource = tabInteraction, indication = null) { selectedTab = index }.focusable(interactionSource = tabInteraction).then(if (tabFocused) Modifier.border(2.dp, Gold, RoundedCornerShape(4.dp)) else Modifier).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = title, color = if (isActive) Gold else Color(0xFF888888), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        if (isActive) { Spacer(Modifier.height(6.dp)); Box(Modifier.width(32.dp).height(2.dp).background(Gold, RoundedCornerShape(1.dp))) }
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
                            
                            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                                if (selectedTab == 0) { // الجودة
                                    item {
                                        val itemInteraction = remember { MutableInteractionSource() }
                                        val itemFocused by itemInteraction.collectIsFocusedAsState()
                                        val isItemSelected = selectedQuality == -1
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(when { isTv && itemFocused -> Color.White.copy(0.1f); isItemSelected -> Color(0xFF2A2A2A); itemFocused -> Color(0xFF1E1E1E); else -> Color.Transparent }).then(if (isTv && itemFocused) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier).clickable(interactionSource = itemInteraction, indication = null) { selectedQuality = -1; webViewRef?.evaluateJavascript("window.setQ(-1);", null) }.focusable(interactionSource = itemInteraction).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("تلقائي", color = if (isItemSelected || (isTv && itemFocused)) Gold else Color.White, fontSize = 13.sp, fontWeight = if (isItemSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                            if (isItemSelected) Icon(Icons.Default.CheckCircle, null, tint = Gold, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    items(qualities) { h ->
                                        val itemInteraction = remember { MutableInteractionSource() }
                                        val itemFocused by itemInteraction.collectIsFocusedAsState()
                                        val isItemSelected = selectedQuality == h
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(when { isTv && itemFocused -> Color.White.copy(0.1f); isItemSelected -> Color(0xFF2A2A2A); itemFocused -> Color(0xFF1E1E1E); else -> Color.Transparent }).then(if (isTv && itemFocused) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier).clickable(interactionSource = itemInteraction, indication = null) { selectedQuality = h; webViewRef?.evaluateJavascript("window.setQ($h);", null) }.focusable(interactionSource = itemInteraction).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("${h}p", color = if (isItemSelected || (isTv && itemFocused)) Gold else Color.White, fontSize = 13.sp, fontWeight = if (isItemSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                            if (isItemSelected) Icon(Icons.Default.CheckCircle, null, tint = Gold, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                } else { // الصوت
                                    if (audios.isEmpty()) {
                                        item { Text("افتراضي", color = Gold, fontSize = 13.sp, modifier = Modifier.padding(14.dp)) }
                                    }
                                    items(audios) { lang ->
                                        val itemInteraction = remember { MutableInteractionSource() }
                                        val itemFocused by itemInteraction.collectIsFocusedAsState()
                                        val isItemSelected = selectedAudio == lang
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(when { isTv && itemFocused -> Color.White.copy(0.1f); isItemSelected -> Color(0xFF2A2A2A); itemFocused -> Color(0xFF1E1E1E); else -> Color.Transparent }).then(if (isTv && itemFocused) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier).clickable(interactionSource = itemInteraction, indication = null) { selectedAudio = lang; webViewRef?.evaluateJavascript("window.setA('$lang');", null) }.focusable(interactionSource = itemInteraction).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(lang.uppercase(), color = if (isItemSelected || (isTv && itemFocused)) Gold else Color.White, fontSize = 13.sp, fontWeight = if (isItemSelected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
                                            if (isItemSelected) Icon(Icons.Default.CheckCircle, null, tint = Gold, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
                            val closeInteraction = remember { MutableInteractionSource() }
                            val closeFocused by closeInteraction.collectIsFocusedAsState()
                            Box(modifier = Modifier.fillMaxWidth().then(if (closeFocused) Modifier.border(1.5.dp, Gold, RoundedCornerShape(4.dp)) else Modifier).clickable(interactionSource = closeInteraction, indication = null) { showTrackDialog = false }.focusable(interactionSource = closeInteraction).padding(12.dp), contentAlignment = Alignment.Center) { Text("إغلاق", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // 2. السيرفرات
            if (showServerDialog && !resolvedConfig.servers.isNullOrEmpty()) {
                HybridServerDialog(
                    servers = resolvedConfig.servers!!,
                    currentUrl = currentServerUrl,
                    isTv = isTv,
                    onSelect = { server ->
                        showServerDialog = false
                        currentServerUrl = server.url ?: return@HybridServerDialog
                        loadWebViewStream(server.url!!, resolvedConfig)
                    },
                    onDismiss = { showServerDialog = false }
                )
            }

            // 3. مصادر الصوت الخارجية
            if (showAudioSourceDialog && !resolvedConfig.audioSources.isNullOrEmpty()) {
                HybridAudioSourceDialog(
                    sources = resolvedConfig.audioSources!!,
                    isTv = isTv,
                    onSelect = { source ->
                        showAudioSourceDialog = false
                        // تمرير رابط الصوت الخارجي إلى الويب (نحتاج لدالة setExtAudio في HTML)
                        val aUrl = source.url ?: return@HybridAudioSourceDialog
                        webViewRef?.evaluateJavascript("if(window.selectExternalAudio) window.selectExternalAudio('$aUrl');", null)
                    },
                    onDismiss = { showAudioSourceDialog = false }
                )
            }

        }
    }
}

@Composable
private fun HybridServerDialog(servers: List<com.apix.app.data.Server>, currentUrl: String, isTv: Boolean, onSelect: (com.apix.app.data.Server) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.45f).fillMaxHeight(if (isTv) 0.5f else 0.85f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111)).border(if (isTv) 2.dp else 0.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))) {
            Column(Modifier.fillMaxSize()) {
                Text("اختر السيرفر", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                HorizontalDivider(color = Color(0xFF222222))
                LazyColumn(Modifier.weight(1f).padding(8.dp)) {
                    itemsIndexed(servers) { _, server ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val isActive = server.url == currentUrl
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (isTv && isFocused) Color.White.copy(0.1f) else if (isActive) Color(0xFF2A2A2A) else Color.Transparent).then(if (isTv && isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier).clickable(interactionSource = interactionSource, indication = null) { onSelect(server) }.focusable(interactionSource = interactionSource).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(server.name ?: "Server", color = if (isActive || isFocused) Gold else Color.White, fontSize = 14.sp)
                            if (isActive) Icon(Icons.Default.CheckCircle, null, tint = Gold, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF222222))
                Box(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(12.dp), contentAlignment = Alignment.Center) { Text("إغلاق", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun HybridAudioSourceDialog(sources: List<com.apix.app.data.AudioSource>, isTv: Boolean, onSelect: (com.apix.app.data.AudioSource) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxWidth(0.45f).fillMaxHeight(if (isTv) 0.5f else 0.85f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111)).border(if (isTv) 2.dp else 0.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))) {
            Column(Modifier.fillMaxSize()) {
                Text("مصادر الصوت", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                HorizontalDivider(color = Color(0xFF222222))
                LazyColumn(Modifier.weight(1f).padding(8.dp)) {
                    itemsIndexed(sources) { _, source ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp)).background(if (isTv && isFocused) Color.White.copy(0.1f) else Color.Transparent).then(if (isTv && isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier).clickable(interactionSource = interactionSource, indication = null) { onSelect(source) }.focusable(interactionSource = interactionSource).padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Icon(Icons.Default.Audiotrack, null, tint = Gold, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(source.name ?: "Audio", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF222222))
                Box(Modifier.fillMaxWidth().clickable { onDismiss() }.padding(12.dp), contentAlignment = Alignment.Center) { Text("إغلاق", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
