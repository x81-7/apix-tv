package com.apix.app

import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
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
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp

import com.apix.app.data.*
import com.apix.app.ui.screens.*
import com.apix.app.data.SupabaseRepository
import com.apix.app.ui.theme.APiXTheme
import com.apix.app.viewmodel.MainViewModel
import com.apix.app.viewmodel.CinemaViewModel

import com.apix.app.vod.plugin.*
import com.apix.app.vod.engine.TMDBRepository

class ComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseRepository.init(application)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            var isDarkMode by remember { mutableStateOf(true) }
            var isInPlayer by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val secureStore = SecureRepositoryStore(applicationContext)
                        val existingRepos = secureStore.getRepositories()
                        val myPluginRepoUrl1 = BuildConfig.PLUGIN_REPO_URL_1 
                        if (myPluginRepoUrl1.isNotEmpty() && !existingRepos.any { it.manifestUrl == myPluginRepoUrl1 }) {
                            secureStore.addRepository("سيرفر الإضافات 1", myPluginRepoUrl1)
                        }
                    } catch (e: Exception) {}
                }
            }

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
}

sealed class Screen {
    data object Main : Screen()
    data class SubChannels(val menuName: String, val channels: List<Channel>) : Screen()
    data object Search : Screen()
    data class Details(val item: MediaItem) : Screen()
    data class Player(
        val config: PlayerConfig, 
        val isExternal: Boolean = false,
        val vodStreams: Map<String, List<com.apix.app.vod.extractors.StreamSource>>? = null,
        val vodSubtitles: List<com.apix.app.vod.extractors.SubtitleSource>? = null
    ) : Screen()
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
    val cinemaViewModel: CinemaViewModel = viewModel()
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    
    val uiState by viewModel.uiState.collectAsState()
    val sideMenus by viewModel.sideMenus.collectAsState()
    
    val cinemaState by cinemaViewModel.homeState.collectAsState()
    val moviesRows by cinemaViewModel.moviesRows.collectAsState()
    val seriesRows by cinemaViewModel.seriesRows.collectAsState()
    val animeRows by cinemaViewModel.animeRows.collectAsState()
    val cinemaLoading by cinemaViewModel.isLoading.collectAsState()

    LaunchedEffect(uiState.appMode, uiState.externalSourceUrl) {
        if (uiState.appMode.isNotEmpty()) {
            cinemaViewModel.loadCinemaData(uiState.appMode, uiState.externalSourceUrl)
        }
    }

    // تجهيز قنوات البث المباشر لإرسالها لتبويب الرياضة (16:9)
    val liveChannels = remember(uiState.categories) {
        uiState.categories.filter { !it.hidden }.flatMap { cat ->
            cat.channels?.values?.filter { !it.hidden }?.sortedBy { it.sortOrder }?.map { ch ->
                MediaItem(
                    id = ch.id, title = ch.name, poster = ch.imageUrl,
                    backdrop = ch.imageUrl, section = "live", directUrl = ch.stream?.url,
                    useLocalProxy = ch.useLocalProxy
                )
            } ?: emptyList()
        }
    }

    val navigationStack = remember { mutableStateListOf<Screen>() }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var isSettings by remember { mutableStateOf(false) }

    val navigateTo: (Screen) -> Unit = { screen ->
        navigationStack.add(currentScreen)
        currentScreen = screen
    }

    val scope = rememberCoroutineScope()
    var resolving by remember { mutableStateOf(false) }

    // 👈 المنطق السحري لربط الإضافات (Plugins) بـ ExoPlayer مع الإشعارات
    val playMediaItem: (MediaItem, Int?, Int?) -> Unit = { item, season, episode ->
        resolving = true
        Toast.makeText(context, "جاري البحث عن روابط الجودة...", Toast.LENGTH_SHORT).show()
        
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val secureStore = SecureRepositoryStore(context)
                val repoManager = RepositoryManager(context)
                val providerLoader = ProviderLoader(context)
                
                val plugins = mutableListOf<Pair<java.io.File, String>>()
                val repos = secureStore.getRepositories()
                
                for (repo in repos) {
                    val manifest = repoManager.fetchManifest(repo)
                    manifest?.plugins?.forEach { entry ->
                        val pluginDir = context.getDir("plugins", android.content.Context.MODE_PRIVATE)
                        var file = java.io.File(pluginDir, "${entry.id}_${entry.version}.apk")
                        if (!file.exists()) {
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                Toast.makeText(context, "يتم تحميل تحديثات السيرفر...", Toast.LENGTH_SHORT).show()
                            }
                            val downloaded = repoManager.downloadPlugin(repo, entry)
                            if (downloaded != null) file = downloaded
                        }
                        if (file.exists()) {
                            plugins.add(Pair(file, entry.className))
                        }
                    }
                }
                
                val providers = providerLoader.loadProviders(plugins)
                val sourceEngine = com.apix.app.vod.engine.SourceEngine(providers)
                
                val request = com.apix.app.vod.extractors.WatchRequest(
                    tmdbId = item.tmdbId.ifBlank { item.id },
                    imdbId = null, title = item.title, originalTitle = item.title,
                    year = item.year.toIntOrNull() ?: 2026,
                    isSeries = item.section == "series" || item.section == "anime",
                    season = season, episode = episode
                )
                
                val streamsMap = sourceEngine.fetchStreams(request)
                val subtitlesList = sourceEngine.fetchSubtitles(request)
                
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    resolving = false
                    if (streamsMap.isNotEmpty()) {
                        val firstSource = streamsMap.values.first().first()
                        val config = PlayerConfig(
                            url = firstSource.url, title = item.title,
                            headers = PlayerHeaders(
                                userAgent = firstSource.headers?.get("User-Agent") ?: firstSource.headers?.get("user-agent"),
                                referer = firstSource.headers?.get("Referer") ?: firstSource.headers?.get("referer")
                            ),
                            customHeaders = firstSource.headers,
                            subtitleUrl = subtitlesList.firstOrNull()?.url
                        )
                        // الانتقال الفعلي لمشغل ExoPlayer!
                        navigateTo(Screen.Player(config, isExternal = false, vodStreams = streamsMap, vodSubtitles = subtitlesList))
                    } else {
                        Toast.makeText(context, "عذراً، لا توجد سيرفرات متاحة لهذا المحتوى حالياً", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) { 
                    resolving = false
                    Toast.makeText(context, "فشل الاتصال بالمزود", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val handleChannelClick: (Channel) -> Unit = { channel ->
        val config = viewModel.buildPlayerConfig(channel)
        if (config != null) navigateTo(Screen.Player(config))
    }

    val handleMediaClick: (com.apix.app.data.MediaItem) -> Unit = { item ->
        if (item.section == "live") {
            val ch = viewModel.getVisibleChannels().find { it.id == item.id } 
                     ?: uiState.categories.flatMap { it.channels?.values ?: emptyList() }.find { it.id == item.id }
            if (ch != null) handleChannelClick(ch)
        } else {
            navigateTo(Screen.Details(item))
        }
    }

    fun goBack(): Boolean {
        if (navigationStack.isEmpty()) { activity?.finishAffinity(); return true }
        currentScreen = navigationStack.removeAt(navigationStack.lastIndex)
        return true
    }

    androidx.activity.compose.BackHandler(currentScreen !is Screen.Main) { goBack() }

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.animation.AnimatedContent(targetState = currentScreen, label = "") { screen ->
            when (screen) {
                is Screen.Main -> {
                    if (uiState.appMode.uppercase() == "SPORTS_ONLY" || uiState.appMode.uppercase() == "LIVE_ONLY") {
                        MainScreen(uiState, {}, {}, {}, viewModel.getVisibleChannels(), isSettings, isDarkMode, {})
                    } else {
                        CinemaShell(
                            homeData = cinemaState,
                            moviesRows = moviesRows,
                            seriesRows = seriesRows,
                            animeRows = animeRows,
                            liveChannels = liveChannels,
                            isLoading = cinemaLoading,
                            onItemClick = handleMediaClick,
                            onLiveChannelClick = handleMediaClick,
                            onSeeMoreClick = { /* مستقبلاً يمكن إضافة واجهة شبكة هنا */ }
                        )
                    }
                }
                is Screen.Details -> {
                    var seasons by remember { mutableStateOf<List<TvSeason>>(emptyList()) }
                    var episodes by remember { mutableStateOf<List<TvEpisode>>(emptyList()) }
                    var selectedSeason by remember { mutableStateOf<TvSeason?>(null) }

                    LaunchedEffect(screen.item.id) {
                        val isSeries = screen.item.section == "series"
                        if (isSeries) {
                            TMDBRepository.getDetails(screen.item.tmdbId, isSeries)?.let {
                                seasons = TMDBRepository.getSeasons(screen.item.tmdbId)
                                selectedSeason = seasons.firstOrNull()
                            }
                        }
                    }
                    LaunchedEffect(selectedSeason) {
                        selectedSeason?.let { episodes = TMDBRepository.getEpisodes(screen.item.tmdbId, it.seasonNumber) }
                    }

                    DetailsScreen(
                        item = screen.item,
                        similarItems = emptyList(), 
                        seasons = seasons,
                        episodes = episodes,
                        onSeasonSelect = { selectedSeason = it },
                        onSimilarItemClick = { navigateTo(Screen.Details(it)) },
                        onPlayClick = { playMediaItem(screen.item, null, null) },
                        onEpisodeClick = { episode -> playMediaItem(screen.item, selectedSeason?.seasonNumber ?: 1, episode.episodeNumber) }
                    )
                }
                is Screen.Player -> PlayerScreen(screen.config, screen.vodStreams, screen.vodSubtitles, { goBack() })
                else -> {}
            }
        } 

        if (resolving) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(color = com.apix.app.ui.theme.Gold, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
            }
        }
    } 
}
