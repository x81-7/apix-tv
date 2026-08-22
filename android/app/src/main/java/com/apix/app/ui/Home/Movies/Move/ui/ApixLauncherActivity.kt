package com.lagradost.cloudstream3.apix

import android.app.Activity
import android.content.res.Configuration
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberSaveableStateHolder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lagradost.cloudstream3.apix.data.*
import com.lagradost.cloudstream3.apix.ui.components.BottomNav
import com.lagradost.cloudstream3.apix.ui.components.SideNav
import com.lagradost.cloudstream3.apix.ui.details.DetailScreen
import com.lagradost.cloudstream3.apix.ui.home.DashboardScreen
import com.lagradost.cloudstream3.apix.ui.movies.SectionScreen
import com.lagradost.cloudstream3.apix.ui.search.SearchScreen
import com.lagradost.cloudstream3.apix.ui.settings.SettingsScreen
import com.lagradost.cloudstream3.apix.ui.theme.APiXTheme
import com.lagradost.cloudstream3.apix.ui.theme.ApixBackground
import com.lagradost.cloudstream3.apix.ui.theme.ApixGold
import com.lagradost.cloudstream3.ui.player.DownloadedPlayerActivity
import com.lagradost.cloudstream3.ui.player.OfflinePlaybackHelper
import kotlinx.coroutines.launch

sealed class ApixRoute {
    data object Home : ApixRoute()
    data object Movies : ApixRoute()
    data object Series : ApixRoute()
    data object Anime : ApixRoute()
    data object Settings : ApixRoute()
    data object Search : ApixRoute()
    data class Browse(val sectionId: String) : ApixRoute()
    data class Detail(val id: String) : ApixRoute()
}

class ApixLauncherActivity : ComponentActivity() {
    private val vm by lazy {
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))[ApixViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val openMovies = intent.getBooleanExtra("open_movies", false)
        setContent { APiXTheme { ApixShell(vm, if (openMovies) "movies" else "home") } }
    }
}

@Composable
private fun ApixShell(vm: ApixViewModel, initialRoute: String) {
    val state = vm.state.value
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val uiMode = context.resources.configuration.uiMode
    val isTelevision = (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useSideNav = isTelevision || isLandscape
    val isTv = isTelevision
    val activity = context as? ApixLauncherActivity
    val saveableStateHolder = rememberSaveableStateHolder()

    var routeStack by rememberSaveable(initialRoute) { mutableStateOf(listOf(initialRoute)) }
    var route by remember(initialRoute) { mutableStateOf(parseRoute(initialRoute)) }

    LaunchedEffect(routeStack.lastOrNull()) { route = parseRoute(routeStack.lastOrNull()) }
    LaunchedEffect(state.catalog.updatedAt, state.catalog.extensions.size) {
        if (activity != null) vm.syncExtensions(activity)
    }

    var resolving by remember { mutableStateOf(false) }
    var resolveLabel by remember { mutableStateOf("") }

    fun push(next: ApixRoute) {
        val key = routeKey(next)
        if (routeStack.lastOrNull() == key) return
        routeStack = routeStack + key
    }

    fun replaceRoot(next: ApixRoute) {
        routeStack = listOf(routeKey(next))
    }

    fun popOne(): Boolean {
        if (routeStack.size <= 1) return false
        routeStack = routeStack.dropLast(1)
        return true
    }

    fun startPlayback(item: ApixItem, season: Int?, episode: Int?) {
        if (resolving) return
        resolving = true
        resolveLabel = "فتح المشغل والبحث في الإضافات…"

        val request = com.lagradost.cloudstream3.apix.player.ApixPlaybackSession.Request(
            id = (item.id + (episode ?: 0)).hashCode(),
            title = item.title,
            resolver = com.lagradost.cloudstream3.apix.player.ApixPlaybackSession.Resolver { onLink, onSub ->
                vm.resolvePlaybackStreaming(item, season, episode, onLink, onSub) { progress ->
                    resolveLabel = progress
                }
            },
        )
        com.lagradost.cloudstream3.apix.player.ApixPlaybackSession.pending = request
        runCatching {
            context.startActivity(
                Intent(context, DownloadedPlayerActivity::class.java).apply {
                    action = OfflinePlaybackHelper.APIX_PLAY_ACTION
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            resolving = false
        }.onFailure {
            com.lagradost.cloudstream3.apix.player.ApixPlaybackSession.pending = null
            resolving = false
            resolveLabel = ""
            Toast.makeText(context, "تعذر فتح المشغل", Toast.LENGTH_LONG).show()
        }
    }

    fun openPlay(item: ApixItem) {
        val firstEpisode = item.seasons.firstOrNull()?.episodes?.firstOrNull()
        startPlayback(item, firstEpisode?.season, firstEpisode?.number)
    }

    fun openPlayEpisode(item: ApixItem, episode: ApixEpisode) = startPlayback(item, episode.season, episode.number)

    BackHandler(enabled = true) {
        if (!popOne()) {
            (context as? Activity)?.finish()
        }
    }

    val currentTab = when (route) {
        ApixRoute.Home -> ApixTab.HOME
        ApixRoute.Movies -> ApixTab.MOVIES
        is ApixRoute.Browse -> when (routeSectionKind(state, route.sectionId)) {
            ApixKind.MOVIE -> ApixTab.MOVIES
            ApixKind.SERIES -> ApixTab.SERIES
            ApixKind.ANIME -> ApixTab.ANIME
            null -> ApixTab.HOME
        }
        ApixRoute.Series -> ApixTab.SERIES
        ApixRoute.Anime -> ApixTab.ANIME
        ApixRoute.Settings -> ApixTab.SETTINGS
        else -> ApixTab.HOME
    }
    val onTab: (ApixTab) -> Unit = { tab ->
        replaceRoot(
            when (tab) {
                ApixTab.HOME -> ApixRoute.Home
                ApixTab.MOVIES -> ApixRoute.Movies
                ApixTab.SERIES -> ApixRoute.Series
                ApixTab.ANIME -> ApixRoute.Anime
                ApixTab.SETTINGS -> ApixRoute.Settings
            },
        )
    }

    @Composable
    fun ContentBody(modifier: Modifier) {
        saveableStateHolder.SaveableStateProvider(routeKey(route)) {
            Box(modifier = modifier.statusBarsPadding()) {
                when (val current = route) {
                    ApixRoute.Home -> DashboardScreen(state, isTv, onOpen = ::push, onPlay = ::openPlay, onFavorite = vm::toggleFavorite, onSearch = { push(ApixRoute.Search) })
                    ApixRoute.Movies -> SectionScreen("الأفلام", ApixKind.MOVIE, state, onBack = { popOne() }, onOpenItem = { push(ApixRoute.Detail(it)) }, onPlay = ::openPlay, onFavorite = vm::toggleFavorite, onLoadMore = vm::loadMore, onOpenSection = { push(ApixRoute.Browse(it)) })
                    ApixRoute.Series -> SectionScreen("المسلسلات", ApixKind.SERIES, state, onBack = { popOne() }, onOpenItem = { push(ApixRoute.Detail(it)) }, onPlay = ::openPlay, onFavorite = vm::toggleFavorite, onLoadMore = vm::loadMore, onOpenSection = { push(ApixRoute.Browse(it)) })
                    ApixRoute.Anime -> SectionScreen("الأنمي", ApixKind.ANIME, state, onBack = { popOne() }, onOpenItem = { push(ApixRoute.Detail(it)) }, onPlay = ::openPlay, onFavorite = vm::toggleFavorite, onLoadMore = vm::loadMore, onOpenSection = { push(ApixRoute.Browse(it)) })
                    ApixRoute.Settings -> SettingsScreen(state, onBack = { popOne() }, onToggleLanguage = vm::setLanguage, onToggleDownloads = vm::setDownloadsEnabled, onToggleBypass = vm::setBypassIsp, onReload = { vm.reload(true) })
                    ApixRoute.Search -> SearchScreen(state, onBack = { popOne() }, onOpenItem = { push(ApixRoute.Detail(it)) }, onPlay = ::openPlay, onFavorite = vm::toggleFavorite, onSearch = vm::search, onLoadMore = vm::searchMore)
                    is ApixRoute.Browse -> {
                        val section = state.catalog.sectionById(current.sectionId)
                        SectionScreen(section?.title ?: "القائمة", section?.kind ?: ApixKind.MOVIE, state, sectionId = current.sectionId, onBack = { popOne() }, onOpenItem = { push(ApixRoute.Detail(it)) }, onPlay = ::openPlay, onFavorite = vm::toggleFavorite, onLoadMore = vm::loadMore, onOpenSection = {})
                    }
                    is ApixRoute.Detail -> DetailScreen(state, current.id, onBack = { popOne() }, onOpenItem = { push(ApixRoute.Detail(it)) }, onPlay = ::openPlay, onPlayEpisode = ::openPlayEpisode, onLoadEpisodes = vm::ensureEpisodes, onFavorite = vm::toggleFavorite)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(ApixBackground)) {
        AnimatedVisibility(visible = state.booting || state.loading || resolving) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(if (resolving) resolveLabel.ifBlank { "جاري البحث" } else state.bootLabel.ifBlank { "جاري التحميل" }, color = ApixGold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress = { state.bootProgress.coerceIn(0.02f, 1f) }, modifier = Modifier.fillMaxWidth(), color = ApixGold, trackColor = Color(0xFF2A2A2A))
            }
        }
        if (useSideNav) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    SideNav(currentTab, onTab, Modifier.fillMaxHeight().width(196.dp).navigationBarsPadding().statusBarsPadding())
                    Box(Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF1E1E1E)))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { ContentBody(Modifier.weight(1f).fillMaxHeight()) }
                }
            }
        } else {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                ContentBody(Modifier.weight(1f).fillMaxWidth())
                BottomNav(currentTab, onTab, modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

private fun routeKey(route: ApixRoute): String = when (route) {
    ApixRoute.Home -> "home"
    ApixRoute.Movies -> "movies"
    ApixRoute.Series -> "series"
    ApixRoute.Anime -> "anime"
    ApixRoute.Settings -> "settings"
    ApixRoute.Search -> "search"
    is ApixRoute.Browse -> "browse:${route.sectionId}"
    is ApixRoute.Detail -> "detail:${route.id}"
}

private fun parseRoute(key: String?): ApixRoute = when {
    key == "movies" -> ApixRoute.Movies
    key == "series" -> ApixRoute.Series
    key == "anime" -> ApixRoute.Anime
    key == "settings" -> ApixRoute.Settings
    key == "search" -> ApixRoute.Search
    key?.startsWith("browse:") == true -> ApixRoute.Browse(key.removePrefix("browse:"))
    key?.startsWith("detail:") == true -> ApixRoute.Detail(key.removePrefix("detail:"))
    else -> ApixRoute.Home
}

private fun routeSectionKind(state: ApixUiState, sectionId: String): ApixKind? = state.catalog.sectionById(sectionId)?.kind
