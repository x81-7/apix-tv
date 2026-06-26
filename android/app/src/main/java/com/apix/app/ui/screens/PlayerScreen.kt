package com.apix.app.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.Rational
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
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
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.rememberAsyncImagePainter
import com.apix.app.data.*
import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.MediumRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

// ===== Custom Helper =====

@Composable
fun isSystemInTvMode(): Boolean {
    val context = LocalContext.current
    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
}

// ===== Custom Outline Icons =====

private val PlayOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PlayOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(8f, 6f); lineTo(8f, 18f); lineTo(18f, 12f); close()
        }
    }.build()
}

private val PauseOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PauseOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(6f, 7f); arcTo(2f, 2f, 0f, false, true, 10f, 7f); lineTo(10f, 17f); arcTo(2f, 2f, 0f, false, true, 6f, 17f); close()
            moveTo(14f, 7f); arcTo(2f, 2f, 0f, false, true, 18f, 7f); lineTo(18f, 17f); arcTo(2f, 2f, 0f, false, true, 14f, 17f); close()
        }
    }.build()
}

private val ForwardOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ForwardOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 7f); lineTo(14f, 12f); lineTo(9f, 17f)
            moveTo(15f, 7f); lineTo(20f, 12f); lineTo(15f, 17f)
        }
    }.build()
}

private val RewindOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "RewindOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(15f, 7f); lineTo(10f, 12f); lineTo(15f, 17f)
            moveTo(9f, 7f); lineTo(4f, 12f); lineTo(9f, 17f)
        }
    }.build()
}

private val SettingsOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SettingsOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19.14f, 12.94f)
            curveTo(19.18f, 12.63f, 19.2f, 12.31f, 19.2f, 12f)
            curveTo(19.2f, 11.69f, 19.18f, 11.37f, 19.14f, 11.06f)
            lineTo(21.17f, 9.48f)
            curveTo(21.35f, 9.34f, 21.4f, 9.07f, 21.29f, 8.87f)
            lineTo(19.37f, 5.55f)
            curveTo(19.25f, 5.33f, 19f, 5.26f, 18.78f, 5.33f)
            lineTo(16.39f, 6.29f)
            curveTo(15.89f, 5.91f, 15.36f, 5.59f, 14.77f, 5.35f)
            lineTo(14.41f, 2.81f)
            curveTo(14.37f, 2.57f, 14.17f, 2.4f, 13.93f, 2.4f)
            lineTo(10.09f, 2.4f)
            curveTo(9.85f, 2.4f, 9.66f, 2.57f, 9.62f, 2.81f)
            lineTo(9.26f, 5.35f)
            curveTo(8.67f, 5.59f, 8.13f, 5.92f, 7.64f, 6.29f)
            lineTo(5.25f, 5.33f)
            curveTo(5.03f, 5.25f, 4.78f, 5.33f, 4.66f, 5.55f)
            lineTo(2.74f, 8.87f)
            curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
            lineTo(4.89f, 11.06f)
            curveTo(4.85f, 11.37f, 4.81f, 11.69f, 4.81f, 12f)
            curveTo(4.81f, 12.31f, 4.83f, 12.63f, 4.87f, 12.94f)
            lineTo(2.84f, 14.52f)
            curveTo(2.66f, 14.66f, 2.61f, 14.93f, 2.73f, 15.13f)
            lineTo(4.65f, 18.45f)
            curveTo(4.77f, 18.67f, 5.02f, 18.74f, 5.24f, 18.67f)
            lineTo(7.63f, 17.71f)
            curveTo(8.13f, 18.09f, 8.66f, 18.41f, 9.25f, 18.65f)
            lineTo(9.61f, 21.19f)
            curveTo(9.66f, 21.43f, 9.85f, 21.6f, 10.09f, 21.6f)
            lineTo(13.93f, 21.6f)
            curveTo(14.17f, 21.6f, 14.37f, 21.43f, 14.4f, 21.19f)
            lineTo(14.76f, 18.65f)
            curveTo(15.35f, 18.41f, 15.89f, 18.09f, 16.38f, 17.71f)
            lineTo(18.77f, 18.67f)
            curveTo(18.99f, 18.75f, 19.24f, 18.67f, 19.36f, 18.45f)
            lineTo(21.28f, 15.13f)
            curveTo(21.4f, 14.91f, 21.35f, 14.66f, 21.16f, 14.52f)
            lineTo(19.14f, 12.94f)
            close()
            moveTo(12f, 15.6f)
            curveTo(10.02f, 15.6f, 8.4f, 13.98f, 8.4f, 12f)
            curveTo(8.4f, 10.02f, 10.02f, 8.4f, 12f, 8.4f)
            curveTo(13.98f, 8.4f, 15.6f, 10.02f, 15.6f, 12f)
            curveTo(15.6f, 13.98f, 13.98f, 15.6f, 12f, 15.6f)
            close()
        }
    }.build()
}

private val PipOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PipOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(9f, 19f); lineTo(5f, 19f); arcTo(2f, 2f, 0f, false, true, 3f, 17f)
            lineTo(3f, 7f); arcTo(2f, 2f, 0f, false, true, 5f, 5f)
            lineTo(19f, 5f); arcTo(2f, 2f, 0f, false, true, 21f, 7f); lineTo(21f, 10f)
            moveTo(13f, 13f); lineTo(19f, 13f); arcTo(2f, 2f, 0f, false, true, 21f, 15f)
            lineTo(21f, 17f); arcTo(2f, 2f, 0f, false, true, 19f, 19f)
            lineTo(13f, 19f); arcTo(2f, 2f, 0f, false, true, 11f, 17f)
            lineTo(11f, 15f); arcTo(2f, 2f, 0f, false, true, 13f, 13f); close()
        }
    }.build()
}

private val ResizeOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ResizeOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(15f, 3f); lineTo(21f, 3f); lineTo(21f, 9f)
            moveTo(9f, 21f); lineTo(3f, 21f); lineTo(3f, 15f)
            moveTo(21f, 3f); lineTo(14f, 10f)
            moveTo(3f, 21f); lineTo(10f, 14f)
        }
    }.build()
}

private val CastOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CastOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(2f, 16.1f); arcTo(5f, 5f, 0f, false, true, 5.9f, 20f)
            moveTo(2f, 12.05f); arcTo(9f, 9f, 0f, false, true, 9.95f, 20f)
            moveTo(2f, 8f); arcTo(13f, 13f, 0f, false, true, 14f, 20f)
            moveTo(2f, 20f); lineTo(2.01f, 20f)
            moveTo(20f, 4f); lineTo(4f, 4f)
            moveTo(20f, 4f); lineTo(20f, 20f); lineTo(14f, 20f)
        }
    }.build()
}

private val CellTowerOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "CellTowerOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(7.05f, 16.95f); arcTo(7f, 7f, 0f, false, true, 7.05f, 7.05f)
            moveTo(4.22f, 19.78f); arcTo(11f, 11f, 0f, false, true, 4.22f, 4.22f)
            moveTo(16.95f, 7.05f); arcTo(7f, 7f, 0f, false, true, 16.95f, 16.95f)
            moveTo(19.78f, 4.22f); arcTo(11f, 11f, 0f, false, true, 19.78f, 19.78f)
            moveTo(10.5f, 11f); lineTo(8f, 22f)
            moveTo(13.5f, 11f); lineTo(16f, 22f)
            moveTo(9f, 18f); lineTo(15f, 18f)
            moveTo(12f, 10f); arcTo(2f, 2f, 0f, true, true, 12.01f, 10f); close()
        }
    }.build()
}

private val BackOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "BackOutline", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Transparent),
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.5f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19f, 12f); lineTo(5f, 12f)
            moveTo(12f, 19f); lineTo(5f, 12f); lineTo(12f, 5f)
        }
    }.build()
}

// ===== Main Player Screen =====

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    config: PlayerConfig,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isTv = isSystemInTvMode()

    // ── إجبار الشاشة الكاملة وتخطي النتوء (Notch) لشاومي وغيرها ──
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        }
        onDispose {
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    }
                }
            }
        }
    }
    // ─────────────────────────────────────────────────────────────

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentResizeMode by remember { mutableStateOf(0) }
    var showTrackDialog by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var currentServerUrl by remember { mutableStateOf(config.url) }
    var showAudioSourceDialog by remember { mutableStateOf(false) }
    var showFallbackServerDialog by remember { mutableStateOf(false) }
    var latestPlaybackError by remember { mutableStateOf<String?>(null) }
    var currentFallbackIndex by remember { mutableStateOf(-1) }
    var retryCountSameServer by remember { mutableStateOf(0) }
    var showServersButtonEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val sp = context.getSharedPreferences("apix_player_ui", android.content.Context.MODE_PRIVATE)
                val cached = sp.getBoolean("show_servers_button", false)
                showServersButtonEnabled = cached
                try {
                    val url = java.net.URL(com.apix.app.Net.base() +
                        "/rest/v1/system_settings?key=eq.playerUiConfig&select=value")
                    val c = url.openConnection() as java.net.HttpURLConnection
                    c.setRequestProperty("apikey", com.apix.app.Net.anon())
                    c.setRequestProperty("Authorization", "Bearer " + com.apix.app.Net.anon())
                    c.connectTimeout = 5000; c.readTimeout = 5000
                    com.apix.app.Net.verifyPins(c)
                    if (c.responseCode == 200) {
                        val body = c.inputStream.bufferedReader().use { it.readText() }
                        val arr = com.google.gson.JsonParser.parseString(body).asJsonArray
                        if (arr.size() > 0) {
                            val v = arr.get(0).asJsonObject.getAsJsonObject("value")
                            val flag = v?.get("showServersButton")?.asBoolean ?: false
                            showServersButtonEnabled = flag
                            sp.edit().putBoolean("show_servers_button", flag).apply()
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    var controlsResetKey by remember { mutableStateOf(0L) }
    val pipFocusRequester = remember { FocusRequester() }

    val initialResizeMode = remember {
        when (config.forcedAspectRatio) {
            "fit" -> 0
            "stretch", "fill" -> 1
            "16:9", "4:3" -> 2
            else -> 0
        }
    }

    val canChangeResize = !config.lockAspectRatio

    LaunchedEffect(Unit) { currentResizeMode = initialResizeMode }

    val resizeModes = remember {
        intArrayOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        )
    }

    val trackSelector = remember { buildOptimalTrackSelector(context) }

    val player = remember {
        val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .build()
    }
    val externalAudioPlayer = remember {
        val audioTrackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            )
        }
        ExoPlayer.Builder(context).setTrackSelector(audioTrackSelector).build()
    }

    var resolvedConfig by remember { mutableStateOf(config) }

    suspend fun loadStream(streamUrl: String, cfg: PlayerConfig) {
        try {
            latestPlaybackError = null

            if (streamUrl.lowercase().contains(".json")) {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    var success = false
                    var attempts = 0
                    var realUrl = ""
                    val dynamicHeaders = mutableMapOf<String, String>()

                    while (attempts < 3 && !success) {
                        try {
                            val connection = java.net.URL(streamUrl).openConnection() as java.net.HttpURLConnection
                            connection.requestMethod = "GET"
                            connection.connectTimeout = 10000
                            connection.readTimeout = 10000

                            cfg.headers?.referer?.let { connection.setRequestProperty("Referer", it) }
                            cfg.customHeaders?.forEach { (k, v) -> connection.setRequestProperty(k, v) }

                            val response = connection.inputStream.bufferedReader().use { it.readText() }
                            val jsonElement = com.google.gson.JsonParser.parseString(response)
                            val jsonObject = if (jsonElement.isJsonArray) {
                                jsonElement.asJsonArray.get(0).asJsonObject
                            } else {
                                jsonElement.asJsonObject
                            }

                            realUrl = jsonObject.get("url")?.asString ?: ""

                            if (jsonObject.has("Referer")) dynamicHeaders["Referer"] = jsonObject.get("Referer").asString
                            if (jsonObject.has("userAgent")) dynamicHeaders["User-Agent"] = jsonObject.get("userAgent").asString

                            if (jsonObject.has("otherHeaders")) {
                                val others = jsonObject.get("otherHeaders").asString
                                others.split("|").forEach { part ->
                                    val pair = part.split(":", limit = 2)
                                    if (pair.size == 2) dynamicHeaders[pair[0].trim()] = pair[1].trim()
                                }
                            }

                            if (realUrl.isNotEmpty()) success = true
                        } catch (e: Exception) {
                            attempts++
                            kotlinx.coroutines.delay(1000)
                        }
                    }

                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (success) {
                            val updatedHeaders = (cfg.customHeaders ?: emptyMap()).toMutableMap()
                            updatedHeaders.putAll(dynamicHeaders)
                            val newCfg = cfg.copy(url = realUrl, customHeaders = updatedHeaders)
                            loadStream(realUrl, newCfg)
                        } else {
                            val backup = cfg.backupUrl
                            if (!backup.isNullOrEmpty() && streamUrl != backup) {
                                loadStream(backup, cfg)
                            } else {
                                errorMessage = "فشل تحليل رابط الـ JSON للحصول على البث."
                            }
                        }
                    }
                }
                return
            }

            var playUrl = streamUrl

            val isYouTubeStream = streamUrl.contains("videoplayback") || streamUrl.contains("googlevideo.com")

            if (isYouTubeStream && (streamUrl.contains("manifest") || streamUrl.contains("m3u8"))) {
                if (!playUrl.endsWith("#hls")) {
                    playUrl += "#hls"
                }
            }

            if (cfg.useLocalProxy && !isYouTubeStream && !com.apix.app.LocalStreamServer.shouldBypass(streamUrl)) {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val hdrs = HashMap<String, String>()
                    cfg.headers?.userAgent?.let { hdrs["User-Agent"] = it }
                    cfg.headers?.referer?.let { hdrs["Referer"] = it }
                    cfg.headers?.cookie?.let { hdrs["Cookie"] = it }
                    cfg.headers?.origin?.let { hdrs["Origin"] = it }
                    cfg.customHeaders?.forEach { (k, v) -> hdrs[k] = v }
                    hdrs["Connection"] = "close"
                    com.apix.app.LocalStreamServer.setHeaders(hdrs)
                    com.apix.app.LocalStreamServer.ensureStarted()
                    playUrl = com.apix.app.LocalStreamServer.wrap(streamUrl)
                }
            }


            player.stop()
            player.clearMediaItems()
            val effectiveConfig = cfg.copy(url = playUrl)
            val mediaSource = buildMediaSourceWithDrm(context, effectiveConfig, playUrl)

            player.setMediaSource(mediaSource)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            errorMessage = "فشل إعداد البث: ${e.message}"
            latestPlaybackError = e.message
        }
    }

    LaunchedEffect(config) {
        try {
            val apixResolved = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                resolveApixIfNeeded(config)
            }

            if (apixResolved.dynamicApi?.enabled == true && !apixResolved.dynamicApi?.endpoint.isNullOrEmpty()) {
                val apiConfig = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    fetchDynamicStreamConfig(apixResolved)
                }
                val finalConfig = apiConfig ?: apixResolved
                resolvedConfig = finalConfig
                currentServerUrl = finalConfig.url
                loadStream(finalConfig.url, finalConfig)
            } else {
                resolvedConfig = apixResolved
                currentServerUrl = apixResolved.url
                loadStream(apixResolved.url, apixResolved)
            }
        } catch (e: Exception) {
            Log.e("PlayerScreen", "Error resolving config", e)
            loadStream(config.url, config)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (externalAudioPlayer.mediaItemCount > 0) externalAudioPlayer.playWhenReady = playing
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.w("PlayerScreen", "onPlayerError code=${error.errorCodeName} idx=$currentFallbackIndex retry=$retryCountSameServer msg=${error.message}")
                if (retryCountSameServer < 1) {
                    retryCountSameServer += 1
                    Log.d("PlayerScreen", "→ retrying same server (attempt #${retryCountSameServer + 1})")
                    kotlinx.coroutines.MainScope().launch {
                        kotlinx.coroutines.delay(800)
                        loadStream(currentServerUrl, resolvedConfig)
                    }
                    return
                }
                retryCountSameServer = 0
                val fbList = resolvedConfig.fallbackServers ?: emptyList()
                val nextIdx = currentFallbackIndex + 1
                val nextFb = fbList.getOrNull(nextIdx)
                if (nextFb != null && !nextFb.url.isNullOrEmpty()) {
                    currentFallbackIndex = nextIdx
                    val merged = resolvedConfig.copy(
                        url = nextFb.url!!,
                        headers = PlayerHeaders(
                            userAgent = nextFb.userAgent ?: resolvedConfig.headers?.userAgent,
                            referer = nextFb.referer ?: resolvedConfig.headers?.referer,
                            cookie = nextFb.cookie ?: resolvedConfig.headers?.cookie,
                            origin = nextFb.origin ?: resolvedConfig.headers?.origin
                        ),
                        customHeaders = nextFb.customHeaders?.mapNotNull {
                            val k = it.key; val v = it.value
                            if (k != null && v != null) k to v else null
                        }?.toMap() ?: resolvedConfig.customHeaders,
                        drm = run {
                            val scheme = nextFb.drmScheme
                            if (scheme.isNullOrEmpty()) resolvedConfig.drm
                            else {
                                var kid = nextFb.drmKeyId
                                var key = nextFb.drmKey
                                if (nextFb.drmClearKeyMode == "combined" && !nextFb.drmClearKeyCombined.isNullOrEmpty()) {
                                    val parts = nextFb.drmClearKeyCombined!!.split(":")
                                    if (parts.size == 2) { kid = parts[0]; key = parts[1] }
                                }
                                PlayerDrm(licenseUrl = nextFb.drmLicenseUrl, scheme = scheme, keyId = kid, key = key)
                            }
                        },
                        drmLicenseHeaders = nextFb.drmLicenseHeaders?.mapNotNull {
                            val k = it.key; val v = it.value
                            if (k != null && v != null) k to v else null
                        }?.toMap() ?: resolvedConfig.drmLicenseHeaders
                    )
                    resolvedConfig = merged
                    currentServerUrl = nextFb.url!!
                    Log.d("PlayerScreen", "→ trying fallback #$nextIdx: ${nextFb.name ?: nextFb.url}")
                    kotlinx.coroutines.MainScope().launch { loadStream(nextFb.url!!, merged) }
                    return
                }
                val backup = resolvedConfig.backupUrl
                if (!backup.isNullOrEmpty() && currentServerUrl != backup) {
                    currentServerUrl = backup
                    kotlinx.coroutines.MainScope().launch { loadStream(backup, resolvedConfig) }
                    return
                }
                val causeMsg = error.cause?.message ?: ""
                errorMessage = "خطأ تقني: ${error.errorCodeName}\n$causeMsg"
                latestPlaybackError = error.message
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            externalAudioPlayer.release()
            player.stop()
            player.clearMediaItems()
            player.release()
            try {
                // تدمير الكاش والمفاتيح بمجرد الخروج من القناة!
                com.apix.app.LocalStreamServer.clearCache()
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(500)
        }
    }

    LaunchedEffect(showControls, controlsResetKey, showTrackDialog, showServerDialog, showAudioSourceDialog, showFallbackServerDialog) {
        // 1. مؤقت إخفاء أزرار المشغل بعد 3 ثواني
        if (showControls) { 
            delay(3000)
            showControls = false 
        }
        
        // 2. [الحل السحري]: إجبار أزرار الهاتف (الرجوع وغيرها) على الاختفاء 
        // بمجرد انتهاء الـ 3 ثواني أو عند إغلاق أي نافذة (دقة/سيرفر)
        if (!showControls && !showTrackDialog && !showServerDialog && !showAudioSourceDialog && !showFallbackServerDialog) {
            val window = activity?.window
            if (window != null) {
                WindowInsetsControllerCompat(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(showControls) {
        if (showControls && isTv) {
            delay(100)
            try { pipFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showControls = !showControls }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        subtitleView?.setApplyEmbeddedFontSizes(false)
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setResizeMode(resizeModes[initialResizeMode])
                    }
                },
                update = { view ->
                    view.player = player
                    view.resizeMode = resizeModes[currentResizeMode]
                },
                modifier = Modifier.fillMaxSize()
            )

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
                        modifier = Modifier
                            .align(alignment)
                            .padding(
                                start = (logo.offsetX).dp, top = (logo.offsetY).dp,
                                end = (logo.offsetX).dp, bottom = (logo.offsetY).dp
                            )
                    ) {
                        Image(
                            painter = coil.compose.rememberAsyncImagePainter(logo.url),
                            contentDescription = "Logo",
                            modifier = Modifier.size(width = (logo.width ?: 80).dp, height = (logo.height ?: 40).dp),
                            alpha = logo.opacity ?: 1.0f
                        )
                    }
                }
            }

            if (isBuffering) {
                CircularProgressIndicator(
                    color = MediumRed, strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.Center)
                )
            }

            errorMessage?.let { err ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Error, null, tint = MediumRed, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(err, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(32.dp))
                        Spacer(Modifier.height(24.dp))
                        PlayerControlButton(icon = BackOutlineIcon, contentDescription = "إغلاق", onClick = onBack)
                    }
                }
            }

            AnimatedVisibility(visible = showControls && errorMessage == null, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(0.7f),
                                        Color.Transparent
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerControlButton(icon = BackOutlineIcon, contentDescription = "Back", size = 36, onClick = onBack)
                        Text(text = resolvedConfig.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 300.dp))
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(0.8f)
                                    )
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(formatTime(currentPosition), color = Color.White, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(end = 8.dp))
                            Slider(
                                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                                onValueChange = { player.seekTo((it * duration).toLong()) },
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color(0xFFE50914), inactiveTrackColor = Color(0x44FFFFFF)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp)
                                    .focusable()
                            )
                            Text(formatTime(duration), color = Color.White, fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                PlayerControlButton(icon = RewindOutlineIcon, contentDescription = "Rewind", size = 38, onClick = { player.seekBack() })
                                PlayerControlButton(icon = if (isPlaying) PauseOutlineIcon else PlayOutlineIcon, contentDescription = "Play/Pause", size = 44, onClick = { player.playWhenReady = !player.playWhenReady })
                                PlayerControlButton(icon = ForwardOutlineIcon, contentDescription = "Forward", size = 38, onClick = { player.seekForward() })
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (!resolvedConfig.audioSources.isNullOrEmpty()) {
                                    PlayerControlButton(icon = Icons.Default.Audiotrack, contentDescription = "Audio", size = 32) { showAudioSourceDialog = true }
                                }

                                if (!resolvedConfig.servers.isNullOrEmpty()) {
                                    PlayerControlButton(icon = CastOutlineIcon, contentDescription = "Server", size = 32) { showServerDialog = true }
                                }

                                if (showServersButtonEnabled && !resolvedConfig.fallbackServers.isNullOrEmpty()) {
                                    PlayerControlButton(icon = CellTowerOutlineIcon, contentDescription = "Fallback Servers", size = 32) { showFallbackServerDialog = true }
                                }

                                PlayerControlButton(icon = SettingsOutlineIcon, contentDescription = "Quality", size = 32) { showTrackDialog = true }

                                if (canChangeResize) {
                                    PlayerControlButton(icon = ResizeOutlineIcon, contentDescription = "Resize", size = 32) { currentResizeMode = (currentResizeMode + 1) % resizeModes.size }
                                }

                                PlayerControlButton(icon = PipOutlineIcon, contentDescription = "PiP", size = 32, focusRequester = pipFocusRequester, onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                        try { activity.enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) } catch (_: Exception) {}
                                    }
                                })
                            }
                        }
                    }
                }
            }

            if (showTrackDialog) TrackSelectionDialog(player = player, trackSelector = trackSelector, onDismiss = { showTrackDialog = false })
            if (showServerDialog && !resolvedConfig.servers.isNullOrEmpty()) {
                ServerSelectionDialog(
                    servers = resolvedConfig.servers!!,
                    currentUrl = currentServerUrl,
                    onSelect = { server ->
                        showServerDialog = false
                        currentServerUrl = server.url ?: return@ServerSelectionDialog
                        currentFallbackIndex = -1 
                        kotlinx.coroutines.MainScope().launch { loadStream(server.url!!, resolvedConfig) }
                    },
                    onDismiss = { showServerDialog = false }
                )
            }
            if (showAudioSourceDialog && !resolvedConfig.audioSources.isNullOrEmpty()) {
                AudioSourceDialog(
                    sources = resolvedConfig.audioSources!!,
                    onSelect = { source ->
                        showAudioSourceDialog = false
                        val audioUrl = source.url ?: return@AudioSourceDialog
                        kotlinx.coroutines.MainScope().launch {
                            try {
                                player.volume = 0f
                                externalAudioPlayer.stop()
                                externalAudioPlayer.clearMediaItems()
                                val audioConfig = resolvedConfig.copy(url = audioUrl, drm = null, subtitleUrl = null)
                                externalAudioPlayer.setMediaSource(buildMediaSourceWithDrm(context, audioConfig, audioUrl))
                                externalAudioPlayer.prepare()
                                externalAudioPlayer.playWhenReady = player.playWhenReady
                            } catch (e: Exception) { Log.e("PlayerScreen", "External audio failed", e) }
                        }
                    },
                    onDismiss = { showAudioSourceDialog = false }
                )
            }
            if (showFallbackServerDialog && !resolvedConfig.fallbackServers.isNullOrEmpty()) {
                FallbackServerSelectionDialog(
                    servers = resolvedConfig.fallbackServers!!,
                    currentUrl = currentServerUrl,
                    primaryUrl = config.url,
                    onSelectPrimary = {
                        showFallbackServerDialog = false
                        currentFallbackIndex = -1
                        retryCountSameServer = 0
                        currentServerUrl = config.url
                        resolvedConfig = config
                        kotlinx.coroutines.MainScope().launch { loadStream(config.url, config) }
                    },
                    onSelect = { idx, fb ->
                        showFallbackServerDialog = false
                        retryCountSameServer = 0
                        currentFallbackIndex = idx
                        val u = fb.url ?: return@FallbackServerSelectionDialog
                        val merged = resolvedConfig.copy(
                            url = u,
                            headers = PlayerHeaders(
                                userAgent = fb.userAgent ?: resolvedConfig.headers?.userAgent,
                                referer = fb.referer ?: resolvedConfig.headers?.referer,
                                cookie = fb.cookie ?: resolvedConfig.headers?.cookie,
                                origin = fb.origin ?: resolvedConfig.headers?.origin
                            ),
                            customHeaders = fb.customHeaders?.mapNotNull {
                                val k = it.key; val v = it.value
                                if (k != null && v != null) k to v else null
                            }?.toMap() ?: resolvedConfig.customHeaders,
                            drm = run {
                                val scheme = fb.drmScheme
                                if (scheme.isNullOrEmpty()) resolvedConfig.drm
                                else {
                                    var kid = fb.drmKeyId
                                    var key = fb.drmKey
                                    if (fb.drmClearKeyMode == "combined" && !fb.drmClearKeyCombined.isNullOrEmpty()) {
                                        val parts = fb.drmClearKeyCombined!!.split(":")
                                        if (parts.size == 2) { kid = parts[0]; key = parts[1] }
                                    }
                                    PlayerDrm(licenseUrl = fb.drmLicenseUrl, scheme = scheme, keyId = kid, key = key)
                                }
                            },
                            drmLicenseHeaders = fb.drmLicenseHeaders?.mapNotNull {
                                val k = it.key; val v = it.value
                                if (k != null && v != null) k to v else null
                            }?.toMap() ?: resolvedConfig.drmLicenseHeaders
                        )
                        resolvedConfig = merged
                        currentServerUrl = u
                        kotlinx.coroutines.MainScope().launch { loadStream(u, merged) }
                    },
                    onDismiss = { showFallbackServerDialog = false }
                )
            }
        }
    }
}

// ===== Fallback Server Selection Dialog =====
@Composable
fun FallbackServerSelectionDialog(
    servers: List<com.apix.app.data.FallbackServer>,
    currentUrl: String,
    primaryUrl: String,
    onSelectPrimary: () -> Unit,
    onSelect: (Int, com.apix.app.data.FallbackServer) -> Unit,
    onDismiss: () -> Unit
) {
    val isTv = isSystemInTvMode()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier
            .fillMaxWidth(0.5f)
            .fillMaxHeight(if (isTv) 0.6f else 0.85f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111111))
            .border(if (isTv) 2.dp else 0.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))) {
            Column(Modifier.fillMaxSize()) {
                Text("اختر السيرفر", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                HorizontalDivider(color = Color(0xFF222222))
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .padding(8.dp)) {
                    item {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val isActive = currentUrl == primaryUrl
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isTv && isFocused) Color.White.copy(0.1f) else if (isActive) Color(0xFF2A2A2A) else Color.Transparent)
                                .then(if (isTv && isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable(interactionSource = interactionSource, indication = null) { onSelectPrimary() }
                                .focusable(interactionSource = interactionSource)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("السيرفر الأساسي", color = if (isActive || isFocused) Gold else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (isActive) Icon(Icons.Default.CheckCircle, null, tint = Gold, modifier = Modifier.size(18.dp))
                        }
                    }
                    itemsIndexed(servers) { idx, server ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val isActive = server.url == currentUrl
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isTv && isFocused) Color.White.copy(0.1f) else if (isActive) Color(0xFF2A2A2A) else Color.Transparent)
                                .then(if (isTv && isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable(interactionSource = interactionSource, indication = null) { onSelect(idx, server) }
                                .focusable(interactionSource = interactionSource)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(server.name ?: "سيرفر بديل ${idx + 1}", color = if (isActive || isFocused) Gold else Color.White, fontSize = 14.sp)
                            if (isActive) Icon(Icons.Default.CheckCircle, null, tint = Gold, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF222222))
                Box(Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(12.dp), contentAlignment = Alignment.Center) {
                    Text("إغلاق", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ===== Server Selection Dialog =====
@Composable
fun ServerSelectionDialog(servers: List<com.apix.app.data.Server>, currentUrl: String, onSelect: (com.apix.app.data.Server) -> Unit, onDismiss: () -> Unit) {
    val isTv = isSystemInTvMode()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier
            .fillMaxWidth(0.45f)
            .fillMaxHeight(if (isTv) 0.5f else 0.85f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111111))
            .border(if (isTv) 2.dp else 0.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))) {
            Column(Modifier.fillMaxSize()) {
                Text("اختر السيرفر", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                HorizontalDivider(color = Color(0xFF222222))
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .padding(8.dp)) {
                    itemsIndexed(servers) { _, server ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        val isActive = server.url == currentUrl
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isTv && isFocused) Color.White.copy(0.1f) else if (isActive) Color(0xFF2A2A2A) else Color.Transparent)
                                .then(if (isTv && isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable(interactionSource = interactionSource, indication = null) { onSelect(server) }
                                .focusable(interactionSource = interactionSource)
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(server.name ?: "Server", color = if (isActive || isFocused) Gold else Color.White, fontSize = 14.sp)
                            if (isActive) Icon(Icons.Default.CheckCircle, null, tint = Gold, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF222222))
                Box(Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(12.dp), contentAlignment = Alignment.Center) { Text("إغلاق", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ===== Audio Source Dialog =====
@Composable
fun AudioSourceDialog(sources: List<com.apix.app.data.AudioSource>, onSelect: (com.apix.app.data.AudioSource) -> Unit, onDismiss: () -> Unit) {
    val isTv = isSystemInTvMode()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier
            .fillMaxWidth(0.45f)
            .fillMaxHeight(if (isTv) 0.5f else 0.85f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111111))
            .border(if (isTv) 2.dp else 0.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))) {
            Column(Modifier.fillMaxSize()) {
                Text("مصادر الصوت", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                HorizontalDivider(color = Color(0xFF222222))
                LazyColumn(
                    Modifier
                        .weight(1f)
                        .padding(8.dp)) {
                    itemsIndexed(sources) { _, source ->
                        val interactionSource = remember { MutableInteractionSource() }
                        val isFocused by interactionSource.collectIsFocusedAsState()
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isTv && isFocused) Color.White.copy(0.1f) else Color.Transparent)
                            .then(if (isTv && isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable(interactionSource = interactionSource, indication = null) { onSelect(source) }
                            .focusable(interactionSource = interactionSource)
                            .padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Icon(Icons.Default.Audiotrack, null, tint = Gold, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(source.name ?: "Audio", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF222222))
                Box(Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss() }
                    .padding(12.dp), contentAlignment = Alignment.Center) { Text("إغلاق", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ===== Track Selection Dialog =====
@OptIn(UnstableApi::class)
@Composable
fun TrackSelectionDialog(player: ExoPlayer, trackSelector: DefaultTrackSelector, onDismiss: () -> Unit) {
    val isTv = isSystemInTvMode()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("الجودة", "الصوت")

    val videoTracks = remember(player.currentTracks) {
        val tracks = mutableListOf<TrackInfo>()
        tracks.add(TrackInfo("تلقائي", -1, -1, true))
        try {
            player.currentTracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val label = getTrackLabel(format)
                        tracks.add(TrackInfo(label, groupIndex, i, group.isTrackSelected(i)))
                    }
                }
            }
        } catch (_: Exception) {}
        tracks
    }

    val audioTracks = remember(player.currentTracks) {
        val tracks = mutableListOf<TrackInfo>()
        try {
            player.currentTracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val lang = format.language ?: "Unknown"
                        val label = format.label ?: lang.uppercase()
                        val bitrate = format.bitrate
                        val displayLabel = if (bitrate > 0) "$label · ${bitrate / 1000}kbps" else label
                        tracks.add(TrackInfo(displayLabel, groupIndex, i, group.isTrackSelected(i)))
                    }
                }
            }
        } catch (_: Exception) {}
        if (tracks.isEmpty()) tracks.add(TrackInfo("افتراضي", -1, -1, true))
        tracks
    }

    var selectedVideoIndex by remember { mutableStateOf(videoTracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)) }
    var selectedAudioIndex by remember { mutableStateOf(audioTracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier
            .fillMaxWidth(0.45f)
            .fillMaxHeight(if (isTv) 0.65f else 0.85f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111111))
            .border(if (isTv) 2.dp else 0.dp, Color.White.copy(0.2f), RoundedCornerShape(12.dp))) {
            Column(Modifier.fillMaxSize()) {
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0A0A))
                    .padding(top = 8.dp)) {
                    tabs.forEachIndexed { index, title ->
                        val isActive = selectedTab == index
                        val tabInteraction = remember { MutableInteractionSource() }
                        val tabFocused by tabInteraction.collectIsFocusedAsState()
                        Column(modifier = Modifier
                            .weight(1f)
                            .clickable(interactionSource = tabInteraction, indication = null) { selectedTab = index }
                            .focusable(interactionSource = tabInteraction)
                            .then(if (tabFocused) Modifier.border(2.dp, Gold, RoundedCornerShape(4.dp)) else Modifier)
                            .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = title, color = if (isActive) Gold else Color(0xFF888888), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            if (isActive) { Spacer(Modifier.height(6.dp)); Box(Modifier.width(32.dp).height(2.dp).background(Gold, RoundedCornerShape(1.dp))) }
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
                
                val currentTracks = if (selectedTab == 0) videoTracks else audioTracks
                val currentSelected = if (selectedTab == 0) selectedVideoIndex else selectedAudioIndex
                
                LazyColumn(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)) {
                    itemsIndexed(currentTracks) { index, track ->
                        val itemInteraction = remember { MutableInteractionSource() }
                        val itemFocused by itemInteraction.collectIsFocusedAsState()
                        val isItemSelected = index == currentSelected
                        Row(modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isTv && itemFocused -> Color.White.copy(0.1f)
                                    isItemSelected -> Color(0xFF2A2A2A)
                                    itemFocused -> Color(0xFF1E1E1E)
                                    else -> Color.Transparent
                                }
                            )
                            .then(if (isTv && itemFocused) Modifier.border(1.5.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                            .clickable(interactionSource = itemInteraction, indication = null) {
                                try {
                                    if (selectedTab == 0) {
                                        selectedVideoIndex = index
                                        applyVideoTrackSafe(trackSelector, track, player)
                                    } else {
                                        selectedAudioIndex = index
                                        applyAudioTrackSafe(trackSelector, track, player)
                                    }
                                } catch (_: Exception) {}
                            }
                            .focusable(interactionSource = itemInteraction)
                            .padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = track.label, color = if (isItemSelected || (isTv && itemFocused)) Gold else Color.White, fontSize = 13.sp, fontWeight = if (isItemSelected) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (isItemSelected) { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF222222), thickness = 1.dp)
                val closeInteraction = remember { MutableInteractionSource() }
                val closeFocused by closeInteraction.collectIsFocusedAsState()
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .then(if (closeFocused) Modifier.border(1.5.dp, Gold, RoundedCornerShape(4.dp)) else Modifier)
                    .clickable(interactionSource = closeInteraction, indication = null) { onDismiss() }
                    .focusable(interactionSource = closeInteraction)
                    .padding(12.dp), contentAlignment = Alignment.Center) { Text("إغلاق", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

data class TrackInfo(val label: String, val groupIndex: Int, val trackIndex: Int, val isSelected: Boolean)

@OptIn(UnstableApi::class)
private fun applyVideoTrackSafe(trackSelector: DefaultTrackSelector, track: TrackInfo, player: ExoPlayer) {
    try {
        if (track.groupIndex == -1) { 
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearVideoSizeConstraints()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .build()
            )
            return 
        }
        val trackGroup = player.currentTracks.groups[track.groupIndex].mediaTrackGroup
        val override = TrackSelectionOverride(trackGroup, listOf(track.trackIndex))
        
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearVideoSizeConstraints()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .addOverride(override)
                .build()
        )
    } catch (e: Exception) { Log.e("PlayerScreen", "Error applying video track", e) }
}

@OptIn(UnstableApi::class)
private fun applyAudioTrackSafe(trackSelector: DefaultTrackSelector, track: TrackInfo, player: ExoPlayer) {
    try {
        if (track.groupIndex == -1) {
            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                    .build()
            )
            return
        }
        val trackGroup = player.currentTracks.groups[track.groupIndex].mediaTrackGroup
        val override = TrackSelectionOverride(trackGroup, listOf(track.trackIndex))
        
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .addOverride(override)
                .build()
        )
    } catch (e: Exception) { Log.e("PlayerScreen", "Error applying audio track", e) }
}

@Composable
fun PlayerControlButton(icon: ImageVector, contentDescription: String, size: Int = 44, focusRequester: FocusRequester? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isTv = isSystemInTvMode()
    val isHighlighted = isFocused || isPressed
    val scale by animateFloatAsState(if (isHighlighted) 1.2f else 1f, label = "playerBtnScale")
    Box(modifier = Modifier
        .size(size.dp)
        .scale(scale)
        .then(if (isTv && isFocused) Modifier.border(3.dp, Color.White, CircleShape) else if (isFocused) Modifier.border(2.dp, Gold, CircleShape) else Modifier)
        .clip(CircleShape)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester).focusable(interactionSource = interactionSource) else Modifier.focusable(interactionSource = interactionSource)), contentAlignment = Alignment.Center) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size((size * 0.65f).dp))
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, seconds) else String.format("%02d:%02d", minutes, seconds)
}

// ===== Player utilities =====

@OptIn(UnstableApi::class)
private fun buildDataSourceFactory(config: PlayerConfig): DefaultHttpDataSource.Factory {
    val factory = DefaultHttpDataSource.Factory()
    factory.setConnectTimeoutMs(30000)
    factory.setReadTimeoutMs(30000)
    factory.setAllowCrossProtocolRedirects(true)

    val headers = mutableMapOf<String, String>()
    
    config.headers?.let { h ->
        h.userAgent?.let { factory.setUserAgent(it) } ?: factory.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        h.referer?.let { headers["Referer"] = it }
        h.cookie?.let { headers["Cookie"] = it }
        h.origin?.let { headers["Origin"] = it }
    } ?: factory.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

    config.customHeaders?.forEach { (k, v) -> 
        if (k.equals("referer", ignoreCase = true)) {
            headers["Referer"] = v
        } else if (k.equals("user-agent", ignoreCase = true)) {
            factory.setUserAgent(v)
        } else {
            headers[k] = v 
        }
    }
    
    if (headers.isNotEmpty()) factory.setDefaultRequestProperties(headers)
    return factory
}

// بناء وإعداد الـ TrackSelector لدعم الـ 4K والـ 120fps على نطاق محرك الميديا بالكامل تلقائياً
@OptIn(UnstableApi::class)
private fun buildOptimalTrackSelector(context: Context): DefaultTrackSelector {
    val params = DefaultTrackSelector.Parameters.Builder(context)
        .setMaxVideoSize(3840, 2160) // فك قفل دقة 4K
        .setMaxVideoFrameRate(120)  // دعم 120 إطار في الثانية
        .setMaxVideoBitrate(Int.MAX_VALUE)
        .setForceHighestSupportedBitrate(false)
        .setAllowVideoMixedMimeTypeAdaptiveness(true)
        .setAllowVideoNonSeamlessAdaptiveness(true)
        .build()
    return DefaultTrackSelector(context, params)
}

// استخراج FPS وعرض اسم الدقة بدقة عالية داخل حوار الجودات
@OptIn(UnstableApi::class)
private fun getTrackLabel(format: Format): String {
    val height = format.height
    val fps = if (format.frameRate > 0) format.frameRate.toInt() else 0
    val bitrate = if (format.bitrate > 0) " (${format.bitrate / 1000}k)" else ""

    val quality = when {
        height >= 2160 -> "4K"
        height >= 1440 -> "2K (1440p)"
        height >= 1080 -> "FHD (1080p)"
        height >= 720  -> "HD (720p)"
        height >= 480  -> "SD (480p)"
        height >= 360  -> "360p"
        height > 0     -> "${height}p"
        else           -> "تلقائي"
    }

    return if (fps > 0) "$quality · ${fps}fps$bitrate" else "$quality$bitrate"
}

@OptIn(UnstableApi::class)
private fun buildMediaSourceWithDrm(context: Context, config: PlayerConfig, streamUrl: String): MediaSource {
    val dataSourceFactory = buildDataSourceFactory(config)
    val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
    
    val jsonKeys = resolveClearKeySync(config)
    if (jsonKeys != null) {
        val callback = LocalMediaDrmCallback(jsonKeys.toByteArray(StandardCharsets.UTF_8))
        val drmSessionManager = DefaultDrmSessionManager.Builder()
            .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
            .setMultiSession(false)
            .build(callback)
        mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
    }

    val mediaItemBuilder = MediaItem.Builder().setUri(Uri.parse(streamUrl))
    val format = detectStreamFormat(streamUrl)

    when (format) {
        "dash" -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
        "hls" -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
    }

    val drm = config.drm
    if (drm != null && drm.scheme?.lowercase() == "widevine" && !drm.licenseUrl.isNullOrEmpty()) {
        val drmBuilder = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
            .setLicenseUri(drm.licenseUrl)
        if (!config.drmLicenseHeaders.isNullOrEmpty()) {
            drmBuilder.setLicenseRequestHeaders(config.drmLicenseHeaders!!)
        }
        mediaItemBuilder.setDrmConfiguration(drmBuilder.build())
    }

    if (!config.subtitleUrl.isNullOrEmpty()) {
        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(config.subtitleUrl!!))
            .setMimeType(if (config.subtitleUrl!!.contains(".srt")) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
            .setLanguage("ar")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
            .build()
        mediaItemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
    }

    return mediaSourceFactory.createMediaSource(mediaItemBuilder.build())
}

private fun detectStreamFormat(url: String): String {
    val lower = url.lowercase()
    val pathWithoutQuery = lower.substringBefore("?")
    
    return when {
        // 1. روابط HLS المباشرة والصور الملغمة
        lower.endsWith("#hls") || lower.contains(".png") || lower.contains(".jpg") || pathWithoutQuery.endsWith(".m3u8") || lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains("format=m3u8") -> "hls"
        
        // 2. روابط DASH الحقيقية وملفات Manifest فقط من يوتيوب
        pathWithoutQuery.endsWith(".mpd") || lower.contains(".mpd") || lower.contains("/dash/") || lower.contains("format=mpd") || lower.contains("/pltv/") || lower.contains("manifest(format=mpd") || lower.contains("manifest.googlevideo.com") -> "dash"
        
        // 3. أي رابط آخر (بما في ذلك videoplayback المباشر من يوتيوب) يتم تشغيله كفيديو عادي
        else -> "progressive"
    }
}

private fun resolveClearKeySync(config: PlayerConfig): String? {
    val drm = config.drm ?: return null
    val keyId = drm.keyId
    val key = drm.key

    if (!keyId.isNullOrEmpty() && !key.isNullOrEmpty()) {
        val cleanKid = keyId.replace(Regex("[^a-fA-F0-9]"), "").take(32)
        val cleanKey = key.replace(Regex("[^a-fA-F0-9]"), "").take(32)
        if (cleanKid.length >= 16 && cleanKey.length >= 16) {
            return buildClearKeyJson(cleanKid, cleanKey)
        }
    }
    if (!keyId.isNullOrEmpty() && keyId.contains(":") && !keyId.contains("http")) {
        val parts = keyId.split(":")
        if (parts.size >= 2) {
            val kid = parts[0].replace(Regex("[^a-fA-F0-9]"), "").take(32)
            val k = parts[1].replace(Regex("[^a-fA-F0-9]"), "").take(32)
            if (kid.length >= 16 && k.length >= 16) {
                return buildClearKeyJson(kid, k)
            }
        }
    }
    return null
}

private fun buildClearKeyJson(keyId: String, key: String): String {
    val cleanKid = keyId.replace(Regex("[^a-fA-F0-9]"), "").take(32)
    val cleanKey = key.replace(Regex("[^a-fA-F0-9]"), "").take(32)
    if (cleanKid.length != 32 || cleanKey.length != 32) return ""

    val keyIdB64 = hexToBase64Url(cleanKid)
    val keyB64 = hexToBase64Url(cleanKey)

    val reversedKid = cleanKid.substring(6, 8) + cleanKid.substring(4, 6) +
            cleanKid.substring(2, 4) + cleanKid.substring(0, 2) +
            cleanKid.substring(10, 12) + cleanKid.substring(8, 10) +
            cleanKid.substring(14, 16) + cleanKid.substring(12, 14) +
            cleanKid.substring(16)
    val reversedKidB64 = hexToBase64Url(reversedKid)

    return """{"keys":[{"kty":"oct","k":"$keyB64","kid":"$keyIdB64"},{"kty":"oct","k":"$keyB64","kid":"$reversedKidB64"}],"type":"temporary"}"""
}

private fun hexToBase64Url(hex: String): String {
    val clean = hex.replace(Regex("[:\\s-]"), "")
    val data = ByteArray(clean.length / 2) { i ->
        ((Character.digit(clean[i * 2], 16) shl 4) + Character.digit(clean[i * 2 + 1], 16)).toByte()
    }
    return Base64.encodeToString(data, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
}

private fun resolveApixIfNeeded(config: PlayerConfig): PlayerConfig {
    val url = config.url ?: return config
    if (!com.apix.app.data.ApixStreamResolver.isApixStream(url)) return config
    return com.apix.app.data.ApixStreamResolver.resolve(url, config) ?: config
}

private fun fetchDynamicStreamConfig(config: PlayerConfig): PlayerConfig? {
    val api = config.dynamicApi ?: return null
    val endpoint = api.endpoint ?: return null
    var conn: HttpURLConnection? = null
    try {
        val urlStr = if (!api.channelIdParam.isNullOrEmpty()) {
            if (endpoint.contains("?")) "$endpoint&${api.channelIdParam}=${config.title}"
            else "$endpoint?${api.channelIdParam}=${config.title}"
        } else endpoint

        conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = api.method ?: "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        api.headers?.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.instanceFollowRedirects = true

        if (conn.responseCode == 200) {
            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText().trim()
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            val newConfig = config.copy()

            if (!api.tokenParam.isNullOrEmpty()) {
                val field = api.tokenJsonField?.takeIf { it.isNotEmpty() } ?: "token"
                val token = when {
                    json.has(field) -> json[field].asString
                    json.has("data") && json["data"].isJsonObject && json["data"].asJsonObject.has(field) ->
                        json["data"].asJsonObject[field].asString
                    else -> ""
                }
                if (token.isNotEmpty()) {
                    val sep = if (config.url.contains("?")) "&" else "?"
                    newConfig.url = "${config.url}$sep${api.tokenParam}=$token"
                    Log.i("PlayerScreen", "Dynamic token appended (param=${api.tokenParam})")
                    return newConfig
                }
            }

            if (json.has("url")) newConfig.url = json["url"].asString
            if (json.has("headers")) {
                val h = json["headers"].asJsonObject
                val headers = mutableMapOf<String, String>()
                h.entrySet().forEach { headers[it.key] = it.value.asString }
                newConfig.customHeaders = headers
            }
            if (json.has("drm")) {
                val d = json["drm"].asJsonObject
                newConfig.drm = PlayerDrm(
                    licenseUrl = d.get("licenseUrl")?.asString,
                    scheme = d.get("scheme")?.asString,
                    keyId = d.get("keyId")?.asString,
                    key = d.get("key")?.asString
                )
            }
            return newConfig
        }
    } catch (e: Exception) {
        Log.e("PlayerScreen", "Dynamic API fetch failed", e)
    } finally { conn?.disconnect() }
    return null
}


