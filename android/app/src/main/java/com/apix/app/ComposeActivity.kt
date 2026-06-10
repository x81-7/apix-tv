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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment

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

    LaunchedEffect(uiState.appMode) {
        if (uiState.appMode.isNotEmpty()) {
            cinemaViewModel.loadCinemaData(uiState.appMode)
        }
    }

    val navigationStack = remember { mutableStateListOf<Screen>() }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }
    var resolving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val navigateTo: (Screen) -> Unit = { screen ->
        navigationStack.add(currentScreen)
        currentScreen = screen
    }

    val playMediaItem: (MediaItem, Int?, Int?) -> Unit = { item, season, episode ->
        resolving = true
        Toast.makeText(context, "جاري جلب روابط المشاهدة...", Toast.LENGTH_SHORT).show()
        
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val providerLoader = ProviderLoader(context)
                val providers = providerLoader.loadProviders(emptyList()) 
                val sourceEngine = com.apix.app.vod.engine.SourceEngine(providers)
                
                // تم إضافة imdbId و originalTitle لتجنب خطأ الـ Build
                val req = com.apix.app.vod.extractors.WatchRequest(
                    tmdbId = item.tmdbId.ifBlank { item.id },
                    imdbId = null,
                    title = item.title,
                    originalTitle = item.title,
                    year = item.year.toIntOrNull() ?: 2026,
                    isSeries = item.section == "series" || item.section == "anime",
                    season = season, 
                    episode = episode
                )
                
                val streamsMap = try { sourceEngine.fetchStreams(req) } catch(e:Exception){ emptyMap() }
                
                if (streamsMap.isNotEmpty()) {
                    val subList = try { sourceEngine.fetchSubtitles(req) } catch(e:Exception){ emptyList() }
                    val first = streamsMap.values.first().first()
                    val cfg = PlayerConfig(
                        url = first.url, title = item.title,
                        headers = PlayerHeaders(userAgent = first.headers?.get("User-Agent")),
                        customHeaders = first.headers, subtitleUrl = subList.firstOrNull()?.url
                    )
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        resolving = false
                        navigateTo(Screen.Player(cfg, false, streamsMap, subList))
                    }
                } else {
                    val r = CinemaRepository.resolve(item, season ?: 1, episode ?: 1)
                    if (r != null) {
                        if (!r.scrape) {
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                resolving = false
                                navigateTo(Screen.Player(PlayerConfig(url = r.url, title = item.title)))
                            }
                        } else {
                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                VideoExtractor(context).extract(
                                    pageUrl = r.url, referer = r.referer,
                                    onResult = { res ->
                                        resolving = false
                                        val cfg = PlayerConfig(url = res.url, title = item.title, subtitleUrl = res.subtitleUrl)
                                        navigateTo(Screen.Player(cfg))
                                    },
                                    onError = { resolving = false; Toast.makeText(context, "فشل الاستخراج", Toast.LENGTH_SHORT).show() }
                                )
                            }
                        }
                    } else {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            resolving = false
                            Toast.makeText(context, "لا توجد روابط حالياً", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(kotlinx.coroutines.Dispatchers.Main) { resolving = false }
            }
        }
    }

    val handleChannelClick: (Channel) -> Unit = { channel ->
        val cfg = viewModel.buildPlayerConfig(channel)
        if (cfg != null) navigateTo(Screen.Player(cfg))
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
                        // تم استخدام Named Arguments لمنع خطأ ترتيب المتغيرات
                        MainScreen(
                            uiState = uiState,
                            onCategorySelected = {},
                            onChannelClick = {},
                            onSearchClick = {},
                            channels = viewModel.getVisibleChannels(),
                            isSettings = false,
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = {}
                        )
                    } else {
                        CinemaShell(
                            homeData = cinemaState,
                            moviesRows = moviesRows,
                            seriesRows = seriesRows,
                            animeRows = animeRows,
                            liveCategories = uiState.categories,
                            isLoading = cinemaLoading,
                            onItemClick = handleMediaClick,
                            onLiveChannelClick = handleMediaClick,
                            fetchMore = { endpoint, section, page -> cinemaViewModel.fetchMore(endpoint, section, page) }
                        )
                    }
                }
                is Screen.Details -> {
                    var seasons by remember { mutableStateOf<List<TvSeason>>(emptyList()) }
                    var episodes by remember { mutableStateOf<List<TvEpisode>>(emptyList()) }
                    var selectedSeason by remember { mutableStateOf<TvSeason?>(null) }

                    LaunchedEffect(screen.item.id) {
                        val isSeries = screen.item.section == "series" || screen.item.section == "anime"
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

                    // تم إضافة similarItems و onSimilarItemClick لمنع خطأ الـ Build
                    DetailsScreen(
                        item = screen.item,
                        similarItems = emptyList(),
                        seasons = seasons,
                        episodes = episodes,
                        onSeasonSelect = { selectedSeason = it },
                        onSimilarItemClick = {},
                        onPlayClick = { playMediaItem(screen.item, null, null) },
                        onEpisodeClick = { episode -> playMediaItem(screen.item, selectedSeason?.seasonNumber ?: 1, episode.episodeNumber) }
                    )
                }
                is Screen.Player -> PlayerScreen(screen.config, screen.vodStreams, screen.vodSubtitles, { goBack() })
                is Screen.HybridPlayer -> HybridPlayerScreen(screen.config, { goBack() })
                is Screen.WebViewPlayer -> WebViewScreen(screen.url, screen.title, screen.orientation, { goBack() })
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
