package com.apix.app

import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext

import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp

import com.apix.app.data.*
import com.apix.app.ui.screens.*
import com.apix.app.data.SupabaseRepository
import com.apix.app.ui.theme.APiXTheme
import com.apix.app.viewmodel.MainViewModel

class ComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        SupabaseRepository.init(application)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            var isDarkMode by remember { mutableStateOf(true) }
            var isInPlayer by remember { mutableStateOf(false) }

            LaunchedEffect(isInPlayer) {
                if (isInPlayer) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
                        ctrl.hide(WindowInsetsCompat.Type.systemBars())
                        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    WindowCompat.setDecorFitsSystemWindows(window, true)
                    WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            APiXTheme(darkTheme = isDarkMode) {
                AppNavigation(
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = { isDarkMode = it },
                    onPlayerStateChanged = { isInPlayer = it },
                    initialStreamConfigJson = intent.getStringExtra("streamConfig")
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val restart = Intent(this, ComposeActivity::class.java).apply {
            data = intent.data
            action = intent.action
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            intent.extras?.let { putExtras(it) }
        }
        startActivity(restart)
    }
}

sealed class Screen {
    data object Main : Screen()
    data class SubChannels(val menuName: String, val channels: List<Channel>) : Screen()
    data object Search : Screen()
    data class Player(val config: PlayerConfig, val isExternal: Boolean = false) : Screen()
    data class HybridPlayer(val config: PlayerConfig, val isExternal: Boolean = false) : Screen()
    data class WebViewPlayer(val url: String, val title: String, val orientation: String?, val isExternal: Boolean = false) : Screen()
    data class YouTubeSniffer(val youtubeUrl: String, val config: PlayerConfig) : Screen()
    data class PinLock(val menuName: String, val pin: String, val onUnlocked: () -> Unit) : Screen()
}

private const val SETTINGS_CATEGORY_ID = "__settings"

@Composable
fun AppNavigation(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onPlayerStateChanged: (Boolean) -> Unit,
    initialStreamConfigJson: String? = null
) {
    val viewModel: MainViewModel = viewModel()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val uiState by viewModel.uiState.collectAsState()
    val sideMenus by viewModel.sideMenus.collectAsState()

    val navigationStack = remember { mutableStateListOf<Screen>() }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var isSettings by remember { mutableStateOf(false) }
    var notificationHandled by remember { mutableStateOf(false) }
    var isNavigatingBack by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val navigateTo: (Screen) -> Unit = { screen ->
        isNavigatingBack = false 
        navigationStack.add(currentScreen)
        currentScreen = screen
    }

    // Cross-engine switch: replace the current player screen in-place (keeps the
    // navigation stack intact so Back still returns to the channel list).
    // engine: "hybrid" -> Shaka/JW WebView player, otherwise -> native ExoPlayer.
    val switchEngine: (PlayerConfig, String) -> Unit = { cfg, engine ->
        isNavigatingBack = false
        val external = (currentScreen as? Screen.Player)?.isExternal
            ?: (currentScreen as? Screen.HybridPlayer)?.isExternal ?: false
        currentScreen = if (engine == "hybrid") Screen.HybridPlayer(cfg, isExternal = external)
                        else Screen.Player(cfg, isExternal = external)
    }

    val openChannelAfterGate: (Channel) -> Unit = { channel ->
        when (channel.actionType) {
            "open_submenu" -> {
                val menu = channel.sideMenuId?.let { sideMenus[it] }
                    ?: sideMenus.values.firstOrNull { it.name.trim() == channel.name.trim() }
                    ?: sideMenus.values.firstOrNull { it.name.contains(channel.name, true) || channel.name.contains(it.name, true) }

                if (menu != null) {
                    val openMenu = {
                        val subChannels = menu.channels?.values
                            ?.filter { !it.hidden }
                            ?.sortedBy { it.sortOrder }
                            ?.map { sc ->
                                Channel(
                                    id = sc.id, name = sc.name, imageUrl = sc.imageUrl,
                                    sortOrder = sc.sortOrder, actionType = "direct_play",
                                    stream = sc.stream, androidStream = sc.androidStream,
                                    androidActionType = sc.androidActionType,
                                    forcedAspectRatio = sc.forcedAspectRatio,
                                    lockAspectRatio = sc.lockAspectRatio
                                )
                            } ?: emptyList()
                        navigateTo(Screen.SubChannels(channel.name, subChannels))
                    }
                    val pin = menu.pinCode
                    if (!pin.isNullOrBlank()) {
                        navigateTo(Screen.PinLock(menu.name, pin) {
                            if (navigationStack.isNotEmpty()) currentScreen = navigationStack.removeAt(navigationStack.lastIndex)
                            openMenu()
                        })
                    } else {
                        openMenu()
                    }
                }
            }
            "external_link" -> Unit
            else -> {
                val config = viewModel.buildPlayerConfig(channel)
                if (config != null) {
                    val rawUrl = channel.androidStream?.url ?: channel.stream?.url ?: ""

                    if (com.apix.app.OkRuExtractor.isOkRuUrl(rawUrl)) {
                        val videoId = com.apix.app.OkRuExtractor.extractVideoId(rawUrl)
                        if (videoId != null) {
                            // نفتح المشغل فوراً برابط مؤقت وندع الاستخراج يحدث في الخلفية
                            val embedUrl = com.apix.app.OkRuExtractor.buildEmbedUrl(videoId)
                            val initialConfig = config.copy(
                                url           = embedUrl,
                                drm           = null,
                                useLocalProxy = false,
                                customHeaders = mapOf(
                                    "User-Agent" to "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36",
                                    "Referer" to "https://ok.ru/"
                                ),
                                okruVideoId   = videoId,
                                okruChannel   = channel.name
                            )
                            navigateTo(Screen.Player(initialConfig))
                        }
                    } else {
                        when (channel.androidActionType ?: "native") {
                            "shaka_web", "jw_web" -> navigateTo(Screen.HybridPlayer(config))
                            "webview" -> {
                                if (rawUrl.isNotBlank()) navigateTo(Screen.WebViewPlayer(rawUrl, channel.name, config.webViewOrientation))
                            }
                            "youtube" -> {
                                if (rawUrl.isNotBlank()) navigateTo(Screen.YouTubeSniffer(rawUrl, config))
                            }
                            else -> navigateTo(Screen.Player(config))
                        }
                    }
                }
            }
        }
    }

    val handleChannelClick: (Channel, Boolean) -> Unit = { channel, isSub ->
        val host = activity
        val proceed = {
            if (host != null) {
                com.apix.app.AdManager.maybeRunUnlockGate(host, channel.id, isSub) {
                    host.runOnUiThread { openChannelAfterGate(channel) }
                }
            } else {
                openChannelAfterGate(channel)
            }
        }
        val channelPin = channel.pinCode
        if (!channelPin.isNullOrBlank()) {
            navigateTo(Screen.PinLock(channel.name, channelPin) {
                if (navigationStack.isNotEmpty()) currentScreen = navigationStack.removeAt(navigationStack.lastIndex)
                proceed()
            })
        } else {
            proceed()
        }
    }

    LaunchedEffect(initialStreamConfigJson) {
        if (!initialStreamConfigJson.isNullOrEmpty()) {
            runCatching {
                val stream = com.google.gson.Gson().fromJson(initialStreamConfigJson, com.apix.app.StreamConfig::class.java)
                val config = PlayerConfig(
                    url = stream.url ?: "",
                    title = stream.title ?: "",
                    actionType = stream.actionType,
                    webViewOrientation = stream.webViewOrientation,
                    hybridPlayerType = when (stream.actionType) {
                        "jw_web" -> "jw"
                        else -> "shaka"
                    },
                    headers = PlayerHeaders(
                        userAgent = stream.headers?.userAgent,
                        referer = stream.headers?.referer,
                        cookie = stream.headers?.cookie,
                        origin = stream.headers?.origin
                    ),
                    customHeaders = stream.customHeaders,
                    drmLicenseHeaders = stream.drmLicenseHeaders,
                    backupUrl = stream.backupUrl,
                    subtitleUrl = stream.subtitleUrl,
                    drm = stream.drm?.let {
                        PlayerDrm(
                            licenseUrl = it.licenseUrl,
                            scheme = it.scheme,
                            keyId = it.keyId,
                            key = it.key
                        )
                    }
                )
                currentScreen = when {
                    stream.isHybridAction() -> Screen.HybridPlayer(config)
                    stream.isWebViewAction() -> Screen.WebViewPlayer(config.url, config.title, config.webViewOrientation)
                    else -> Screen.Player(config)
                }
            }
        }
    }

    var externalHandled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (externalHandled) return@LaunchedEffect
        val payload = com.apix.app.util.DataProcessor.extract(activity?.intent) ?: return@LaunchedEffect
        externalHandled = true

        val proceed: () -> Unit = proceed@{
            val obj = com.apix.app.util.DataProcessor.process(payload) ?: return@proceed
            runCatching {
                val url = obj.optString("url")
                if (url.isBlank()) return@runCatching

                val title      = obj.optString("name", obj.optString("title", "External"))
                val playerKind = obj.optString("player", "exoplayer")
                val backupUrl  = obj.optString("backupUrl").takeIf { it.isNotBlank() }
                val subtitleUrl = obj.optString("subtitleUrl").takeIf { it.isNotBlank() }

                // ── Headers ──────────────────────────────────────────
                val h  = obj.optJSONObject("headers")
                val ch = obj.optJSONObject("customHeaders")

                val customMap = mutableMapOf<String, String>()
                h?.keys()?.forEach  { k -> h.optString(k).takeIf  { it.isNotBlank() }?.let { v -> customMap[k] = v } }
                ch?.keys()?.forEach { k -> ch.optString(k).takeIf { it.isNotBlank() }?.let { v -> customMap[k] = v } }

                // ── DRM ───────────────────────────────────────────────
                val drmObj = obj.optJSONObject("drm")
                val drm = drmObj?.let {
                    PlayerDrm(
                        licenseUrl = it.optString("licenseUrl").takeIf { s -> s.isNotBlank() },
                        scheme     = it.optString("scheme", "clearkey").ifEmpty { "clearkey" },
                        keyId      = it.optString("keyId").takeIf { s -> s.isNotBlank() },
                        key        = it.optString("key").takeIf   { s -> s.isNotBlank() }
                    )
                }

                // ── Servers[] — سيرفرات متعددة ─────────────────────
                val serversArr = obj.optJSONArray("servers")
                val fallbackServers = mutableListOf<FallbackServer>()

                // backupUrl يصبح أول fallback تلقائياً
                if (backupUrl != null) {
                    fallbackServers.add(
                        FallbackServer(
                            name      = "احتياطي",
                            url       = backupUrl,
                            userAgent = customMap["User-Agent"],
                            referer   = customMap["Referer"],
                            origin    = customMap["Origin"],
                            cookie    = customMap["Cookie"],
                            drmScheme = drm?.scheme,
                            drmKeyId  = drm?.keyId,
                            drmKey    = drm?.key,
                            drmLicenseUrl = drm?.licenseUrl
                        )
                    )
                }

                // servers[] من الـ JSON
                if (serversArr != null) {
                    for (i in 0 until serversArr.length()) {
                        val s    = serversArr.optJSONObject(i) ?: continue
                        val sUrl = s.optString("url").takeIf { it.isNotBlank() } ?: continue
                        val sName = s.optString("name", "سيرفر ${fallbackServers.size + 1}")

                        // headers خاصة بهذا السيرفر
                        val sHdrMap = mutableMapOf<String, String>()
                        s.optJSONObject("headers")?.keys()?.forEach { k ->
                            s.optJSONObject("headers")?.optString(k)
                                ?.takeIf { it.isNotBlank() }?.let { sHdrMap[k] = it }
                        }

                        // DRM خاص بهذا السيرفر — يرث العام إذا لم يكن موجوداً
                        val sDrm = s.optJSONObject("drm")?.let { d ->
                            PlayerDrm(
                                licenseUrl = d.optString("licenseUrl").takeIf { it.isNotBlank() },
                                scheme     = d.optString("scheme", "clearkey").ifEmpty { "clearkey" },
                                keyId      = d.optString("keyId").takeIf { it.isNotBlank() },
                                key        = d.optString("key").takeIf   { it.isNotBlank() }
                            )
                        } ?: drm

                        val sHeaders = sHdrMap.ifEmpty { customMap }
                        fallbackServers.add(
                            FallbackServer(
                                name      = sName,
                                url       = sUrl,
                                userAgent = sHeaders["User-Agent"],
                                referer   = sHeaders["Referer"],
                                origin    = sHeaders["Origin"],
                                cookie    = sHeaders["Cookie"],
                                drmScheme = sDrm?.scheme,
                                drmKeyId  = sDrm?.keyId,
                                drmKey    = sDrm?.key,
                                drmLicenseUrl = sDrm?.licenseUrl
                            )
                        )
                    }
                }

                // ── AudioSources[] ───────────────────────────────────
                val audioArr = obj.optJSONArray("audioSources")
                val audioSources = if (audioArr != null) {
                    (0 until audioArr.length()).mapNotNull { i ->
                        val a    = audioArr.optJSONObject(i) ?: return@mapNotNull null
                        val aUrl = a.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                        AudioSource(
                            name = a.optString("name", "مصدر ${i + 1}"),
                            url  = aUrl
                        )
                    }
                } else emptyList()

                // ── بناء PlayerConfig الكامل ─────────────────────────
                val cfg = PlayerConfig(
                    url         = url,
                    title       = title,
                    actionType  = if (playerKind == "webview") "shaka_web" else "native",
                    hybridPlayerType = "shaka",
                    headers     = PlayerHeaders(
                        userAgent = h?.optString("User-Agent")?.takeIf { it.isNotBlank() }
                            ?: ch?.optString("User-Agent")?.takeIf { it.isNotBlank() },
                        referer   = h?.optString("Referer")?.takeIf { it.isNotBlank() }
                            ?: ch?.optString("Referer")?.takeIf { it.isNotBlank() },
                        cookie    = h?.optString("Cookie")?.takeIf { it.isNotBlank() },
                        origin    = h?.optString("Origin")?.takeIf { it.isNotBlank() }
                    ),
                    customHeaders   = customMap.ifEmpty { null },
                    drm             = drm,
                    backupUrl       = backupUrl,
                    subtitleUrl     = subtitleUrl,
                    fallbackServers = fallbackServers,
                    audioSources    = audioSources,
                    useLocalProxy   = false
                )

                currentScreen = if (playerKind == "webview")
                    Screen.HybridPlayer(cfg, isExternal = true)
                else
                    Screen.Player(cfg, isExternal = true)
            }
        }

        val host = activity
        if (host != null) {
            com.apix.app.AdManager.maybeRunExternalGate(host) {
                host.runOnUiThread { proceed() }
            }
        } else {
            proceed()
        }
        return@LaunchedEffect
    }

    LaunchedEffect(uiState.showSettingsSection) {
        if (!uiState.showSettingsSection && isSettings) {
            isSettings = false
        }
    }

    LaunchedEffect(uiState.categories, sideMenus, notificationHandled) {
        if (notificationHandled) return@LaunchedEffect
        val raw = activity?.intent?.getStringExtra("notification_action") ?: return@LaunchedEffect
        runCatching {
            val action = org.json.JSONObject(raw)
            when (action.optString("actionType")) {
                "main_channel" -> {
                    val targetId = action.optString("targetId")
                    val channel = uiState.categories.asSequence().flatMap { it.channels?.values?.asSequence() ?: emptySequence() }.firstOrNull { it.id == targetId }
                    if (channel != null) handleChannelClick(channel, false)
                }
                "side_menu" -> {
                    val targetId = action.optString("targetId")
                    val menu = sideMenus[targetId]
                    if (menu != null) {
                        val subChannels = menu.channels?.values?.filter { !it.hidden }?.sortedBy { it.sortOrder }?.map { sc ->
                            Channel(id = sc.id, name = sc.name, imageUrl = sc.imageUrl, sortOrder = sc.sortOrder, actionType = "direct_play", stream = sc.stream, androidStream = sc.androidStream, androidActionType = sc.androidActionType, forcedAspectRatio = sc.forcedAspectRatio, lockAspectRatio = sc.lockAspectRatio)
                        } ?: emptyList()
                        navigateTo(Screen.SubChannels(menu.name, subChannels))
                    }
                }
                "sub_channel" -> {
                    val targetId = action.optString("targetId")
                    val channel = sideMenus.values.asSequence().flatMap { it.channels?.values?.asSequence() ?: emptySequence() }.firstOrNull { it.id == targetId }
                    if (channel != null) handleChannelClick(Channel(id = channel.id, name = channel.name, imageUrl = channel.imageUrl, sortOrder = channel.sortOrder, actionType = "direct_play", stream = channel.stream, androidStream = channel.androidStream, androidActionType = channel.androidActionType, forcedAspectRatio = channel.forcedAspectRatio, lockAspectRatio = channel.lockAspectRatio), true)
                }
                "external_link" -> {
                    val externalUrl = action.optString("externalUrl")
                    if (externalUrl.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(externalUrl)))
                }
            }
            activity?.intent?.removeExtra("notification_action")
            notificationHandled = true
        }
    }

    LaunchedEffect(currentScreen) {
        onPlayerStateChanged(currentScreen is Screen.Player || currentScreen is Screen.HybridPlayer)
    }

    val channels = remember(uiState.selectedCategory) {
        viewModel.getVisibleChannels()
    }

    fun isCurrentExternal(): Boolean = when (val s = currentScreen) {
        is Screen.Player -> s.isExternal
        is Screen.HybridPlayer -> s.isExternal
        is Screen.WebViewPlayer -> s.isExternal
        else -> false
    }

    fun goBack(): Boolean {
        if (isCurrentExternal() && navigationStack.isEmpty()) {
            activity?.finishAffinity()
            return true
        }
        if (navigationStack.isNotEmpty()) {
            isNavigatingBack = true 
            currentScreen = navigationStack.removeAt(navigationStack.lastIndex)
            return true
        }
        return false
    }

    fun handleCategorySelect(cat: Category) {
        if (cat.id == SETTINGS_CATEGORY_ID) {
            isSettings = true
        } else {
            isSettings = false
            viewModel.selectCategory(cat)
        }
    }

    androidx.activity.compose.BackHandler(currentScreen !is Screen.Main || isSettings || isCurrentExternal()) {
        if (isSettings) {
            isSettings = false
        } else {
            goBack()
        }
    }

    androidx.compose.animation.AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            val enter = androidx.compose.animation.fadeIn(
                androidx.compose.animation.core.tween(220)
            ) + androidx.compose.animation.slideInHorizontally(
                animationSpec = androidx.compose.animation.core.tween(220),
                initialOffsetX = { it / 12 }
            )
            val exit = androidx.compose.animation.fadeOut(
                androidx.compose.animation.core.tween(120)
            )
            enter togetherWith exit
        },
        label = "screenTransition"
    ) { screen ->
        when (screen) {
            is Screen.Main -> {
                MainScreen(
                    uiState = uiState,
                    onCategorySelected = { handleCategorySelect(it) },
                    onChannelClick = { handleChannelClick(it, false) },
                    onSearchClick = { navigateTo(Screen.Search) },
                    channels = channels,
                    isSettings = isSettings,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode
                )
            }
            is Screen.SubChannels -> {
                var showLoading by remember { mutableStateOf(!isNavigatingBack) }

                LaunchedEffect(showLoading) {
                    if (showLoading) {
                        delay(1300L) 
                        showLoading = false
                    }
                }

                androidx.compose.animation.Crossfade(targetState = showLoading, label = "subLoad") { loading ->
                    if (loading) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                color = com.apix.app.ui.theme.Gold,
                                strokeWidth = 4.dp,
                                modifier = androidx.compose.ui.Modifier.size(56.dp)
                            )
                        }
                    } else {
                        SubChannelScreen(
                            menuName = screen.menuName,
                            channels = screen.channels,
                            onChannelClick = { handleChannelClick(it, true) },
                            onBack = { goBack() }
                        )
                    }
                }
            }
            is Screen.Search -> {
                SearchScreen(
                    onSearch = { viewModel.searchChannels(it) },
                    onChannelClick = { ch ->
                        val config = viewModel.buildPlayerConfig(ch)
                        if (config != null) {
                            val actionType = ch.androidActionType ?: "native"
                            when (actionType) {
                                "shaka_web", "jw_web" -> {
                                    navigateTo(Screen.HybridPlayer(config))
                                }
                                "webview" -> {
                                    val url = ch.androidStream?.url ?: ch.stream?.url ?: return@SearchScreen
                                    navigateTo(Screen.WebViewPlayer(url, ch.name, config.webViewOrientation))
                                }
                                "youtube" -> {
                                    val url = ch.androidStream?.url ?: ch.stream?.url ?: return@SearchScreen
                                    navigateTo(Screen.YouTubeSniffer(url, config))
                                }
                                else -> navigateTo(Screen.Player(config))
                            }
                        }
                    },
                    onClose = { goBack() }
                )
            }
            is Screen.Player -> {
                PlayerScreen(
                    config = screen.config,
                    onBack = { goBack() },
                    onSwitchEngine = switchEngine
                )
            }
            is Screen.HybridPlayer -> {
                HybridPlayerScreen(
                    config = screen.config,
                    onBack = { goBack() },
                    onSwitchEngine = switchEngine
                )
            }
            is Screen.WebViewPlayer -> {
                WebViewScreen(url = screen.url, title = screen.title, orientation = screen.orientation, onBack = { goBack() })
            }
            is Screen.YouTubeSniffer -> {
                YouTubeSnifferScreen(
                    youtubeUrl = screen.youtubeUrl,
                    config = screen.config,
                    onStreamReady = { sniffedUrl ->
                        val ytHeaders = com.apix.app.data.PlayerHeaders(
                            userAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        )
                        navigationStack.add(Screen.Main)
                        currentScreen = Screen.Player(screen.config.copy(url = sniffedUrl, headers = ytHeaders))
                    },
                    onBack = { goBack() }
                )
            }
            is Screen.PinLock -> {
                PinLockScreen(
                    menuName = screen.menuName,
                    expectedPin = screen.pin,
                    onCancel = { goBack() },
                    onUnlocked = { screen.onUnlocked() }
                )
            }
        }
    } // إغلاق AnimatedContent
} // إغلاق AppNavigation
