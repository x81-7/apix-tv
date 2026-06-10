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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.apix.app.data.*
import com.apix.app.ui.screens.*
import com.apix.app.data.SupabaseRepository
import com.apix.app.ui.theme.APiXTheme
import com.apix.app.ui.theme.Gold
import com.apix.app.viewmodel.MainViewModel
import com.apix.app.viewmodel.CinemaViewModel

import com.apix.app.vod.plugin.*
import com.apix.app.vod.engine.TMDBRepository
import java.io.File

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
    data class Studio(val config: StudioConfig) : Screen() // 👈 شاشة الشركات
    data class Collection(val item: MediaItem) : Screen()  // 👈 شاشة أجزاء السلسلة
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

    // 👈 دالة التشغيل السحرية للإضافات وإشعار عدد السيرفرات الحقيقي
    val playMediaItem: (MediaItem, Int?, Int?) -> Unit = { item, season, episode ->
        resolving = true
        Toast.makeText(context, "جاري البحث في السيرفرات...", Toast.LENGTH_SHORT).show()
        
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val secureStore = SecureRepositoryStore(context)
                val repoManager = RepositoryManager(context)
                val providerLoader = ProviderLoader(context)
                
                val pluginList = mutableListOf<Pair<File, String>>()
                val repos = secureStore.getRepositories()
                var totalLoaded = 0
                
                for (repo in repos) {
                    val manifest = try { repoManager.fetchManifest(repo) } catch (e: Exception) { null }
                    manifest?.plugins?.forEach { entry ->
                        val pluginDir = context.getDir("plugins", android.content.Context.MODE_PRIVATE)
                        var file = File(pluginDir, "${entry.id}_${entry.version}.apk")
                        if (!file.exists()) {
                            withContext(kotlinx.coroutines.Dispatchers.Main) { Toast.makeText(context, "تحميل إضافة جديدة...", Toast.LENGTH_SHORT).show() }
                            val downloaded = try { repoManager.downloadPlugin(repo, entry) } catch (e: Exception) { null }
                            if (downloaded != null) file = downloaded
                        }
                        if (file.exists()) {
                            pluginList.add(Pair(file, entry.className))
                            totalLoaded++
                        }
                    }
                }
                
                withContext(kotlinx.coroutines.Dispatchers.Main) { 
                    if(totalLoaded > 0) Toast.makeText(context, "تم تفعيل $totalLoaded إضافة بنجاح", Toast.LENGTH_SHORT).show() 
                }

                val providers = providerLoader.loadProviders(pluginList) 
                val sourceEngine = com.apix.app.vod.engine.SourceEngine(providers)
                
                val req = com.apix.app.vod.extractors.WatchRequest(
                    tmdbId = item.tmdbId.ifBlank { item.id },
                    imdbId = null, title = item.title, originalTitle = item.title,
                    year = item.year.toIntOrNull() ?: 2026,
                    isSeries = item.section == "series" || item.section == "anime",
                    season = season, episode = episode
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
                                    onError = { resolving = false; Toast.makeText(context, "فشل استخراج الروابط", Toast.LENGTH_SHORT).show() }
                                )
                            }
                        }
                    } else {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            resolving = false
                            Toast.makeText(context, "لا توجد روابط لهذا المحتوى حالياً", Toast.LENGTH_SHORT).show()
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

    val openChannelAfterGate: (Channel) -> Unit = { channel ->
        when (channel.actionType) {
            "open_submenu" -> {
                val menu = channel.sideMenuId?.let { sideMenus[it] } ?: sideMenus.values.firstOrNull { it.name.trim() == channel.name.trim() }
                if (menu != null) {
                    val openMenu = {
                        val subChannels = menu.channels?.values?.filter { !it.hidden }?.sortedBy { it.sortOrder }?.map { sc ->
                            Channel(id = sc.id, name = sc.name, imageUrl = sc.imageUrl, sortOrder = sc.sortOrder, actionType = "direct_play", stream = sc.stream, androidStream = sc.androidStream, androidActionType = sc.androidActionType, forcedAspectRatio = sc.forcedAspectRatio, lockAspectRatio = sc.lockAspectRatio)
                        } ?: emptyList()
                        navigateTo(Screen.SubChannels(channel.name, subChannels))
                    }
                    if (!menu.pinCode.isNullOrBlank()) navigateTo(Screen.PinLock(menu.name, menu.pinCode!!) { currentScreen = navigationStack.removeAt(navigationStack.lastIndex); openMenu() })
                    else openMenu()
                }
            }
            else -> handleChannelClick(channel)
        }
    }

    val gateChannel: (Channel) -> Unit = { channel ->
        val host = activity
        val proceed = {
            if (host != null) {
                com.apix.app.AdManager.maybeRunUnlockGate(host, channel.id) { host.runOnUiThread { openChannelAfterGate(channel) } }
            } else openChannelAfterGate(channel)
        }
        if (!channel.pinCode.isNullOrBlank()) navigateTo(Screen.PinLock(channel.name, channel.pinCode!!) { currentScreen = navigationStack.removeAt(navigationStack.lastIndex); proceed() })
        else proceed()
    }

    val handleMediaClick: (com.apix.app.data.MediaItem) -> Unit = { item ->
        when (item.section) {
            "live" -> {
                val ch = viewModel.getVisibleChannels().find { it.id == item.id } 
                         ?: uiState.categories.flatMap { it.channels?.values ?: emptyList() }.find { it.id == item.id }
                if (ch != null) gateChannel(ch)
            }
            "collection" -> navigateTo(Screen.Collection(item))
            else -> navigateTo(Screen.Details(item))
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
                        MainScreen(uiState = uiState, onCategorySelected = {}, onChannelClick = { gateChannel(it) }, onSearchClick = {}, channels = viewModel.getVisibleChannels(), isSettings = false, isDarkMode = isDarkMode, onToggleDarkMode = {})
                    } else {
                        CinemaShell(
                            homeData = cinemaState, moviesRows = moviesRows, seriesRows = seriesRows, animeRows = animeRows, liveCategories = uiState.categories, isLoading = cinemaLoading,
                            onItemClick = handleMediaClick, onLiveChannelClick = { gateChannel(it) }, onStudioClick = { navigateTo(Screen.Studio(it)) },
                            fetchMore = { endpoint, section, page -> cinemaViewModel.fetchMore(endpoint, section, page) }
                        )
                    }
                }
                
                // 👈 شاشة تفاصيل السلسلة (مجموعة الأفلام)
                is Screen.Collection -> {
                    var parts by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
                    var loading by remember { mutableStateOf(true) }

                    LaunchedEffect(screen.item.tmdbId) {
                        parts = cinemaViewModel.getCollectionParts(screen.item.tmdbId)
                        loading = false
                    }

                    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "عودة", tint = Color.White, modifier = Modifier.clickable { goBack() }.size(30.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = screen.item.title, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        if (loading) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
                        } else {
                            LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(8.dp), modifier = Modifier.weight(1f)) {
                                items(parts) { part ->
                                    Box(modifier = Modifier.padding(8.dp).aspectRatio(0.66f)) {
                                        CinemaPosterCard(item = part, onClick = { navigateTo(Screen.Details(part)) }, modifier = Modifier.fillMaxSize())
                                        // رقم الجزء
                                        Box(modifier = Modifier.align(Alignment.TopStart).background(Gold, RoundedCornerShape(bottomEnd = 8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                            Text(text = "جزء ${parts.indexOf(part) + 1}", color = Color.Black, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 👈 شاشة محتوى الشركات (Netflix وغيرها)
                is Screen.Studio -> {
                    var rows by remember { mutableStateOf<List<HomeRow>>(emptyList()) }
                    var loading by remember { mutableStateOf(true) }

                    LaunchedEffect(screen.config.id) {
                        rows = cinemaViewModel.getStudioData(screen.config.companyId, screen.config.networkId)
                        loading = false
                    }

                    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "عودة", tint = Color.White, modifier = Modifier.clickable { goBack() }.size(30.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(text = "محتوى ${screen.config.name}", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                        if (loading) {
                            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
                                items(rows) { row -> MediaRowSection(row, { handleMediaClick(it) }, {}) }
                            }
                        }
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

                    DetailsScreen(
                        item = screen.item, similarItems = emptyList(), seasons = seasons, episodes = episodes,
                        onSeasonSelect = { selectedSeason = it }, onSimilarItemClick = {},
                        onPlayClick = { playMediaItem(screen.item, null, null) },
                        onEpisodeClick = { episode -> playMediaItem(screen.item, selectedSeason?.seasonNumber ?: 1, episode.episodeNumber) }
                    )
                }
                is Screen.SubChannels -> {
                    SubChannelScreen(menuName = screen.menuName, channels = screen.channels, onChannelClick = { gateChannel(it) }, onBack = { goBack() })
                }
                is Screen.Player -> PlayerScreen(screen.config, screen.vodStreams, screen.vodSubtitles, { goBack() })
                is Screen.HybridPlayer -> HybridPlayerScreen(screen.config, { goBack() })
                is Screen.WebViewPlayer -> WebViewScreen(screen.url, screen.title, screen.orientation, { goBack() })
                else -> {}
            }
        }

        if (resolving) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xCC000000)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = Gold, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
            }
        }
    } 
}
