package com.apix.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem
import com.apix.app.data.HomeRow
import com.apix.app.data.Category
import com.apix.app.data.Channel
import com.apix.app.ui.theme.Gold
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class NavItem(val title: String, val icon: ImageVector)

// 👈 تم تغيير الاسم لتجنب أي تعارض مع HomeScreen القديم!
data class CinemaStudioConfig(val id: String, val name: String, val logoUrl: String, val companyId: Int, val networkId: Int)

object CinemaConstants {
    val netflix = CinemaStudioConfig("netflix", "Netflix", "https://image.tmdb.org/t/p/w300/wwemzKWzjKYJFfCeiB57q3r4Bcm.png", 6194, 213)
    val disney = CinemaStudioConfig("disney", "Disney", "https://image.tmdb.org/t/p/w300/wdrCwmRnLFJhEoG8GSfymY85KHT.png", 2, 2739)
    val marvel = CinemaStudioConfig("marvel", "Marvel", "https://image.tmdb.org/t/p/w300/hUzeosd33nzE5MCNsZxCGEKTxwQ.png", 420, -1)
    val hbo = CinemaStudioConfig("hbo", "HBO", "https://image.tmdb.org/t/p/w300/tuomPhY2UtuPTqqFnKMVHvZ1QI1.png", -1, 49)
    val apple = CinemaStudioConfig("apple", "Apple TV+", "https://image.tmdb.org/t/p/w300/4KAy34EHvRM25Ih8pz82ARhnYQM.png", -1, 2552)
    val list = listOf(netflix, disney, marvel, hbo, apple)
}

@Composable
fun CinemaShell(
    homeData: HomeData,
    moviesRows: List<HomeRow>,
    seriesRows: List<HomeRow>,
    animeRows: List<HomeRow>,
    liveCategories: List<Category>,
    isLoading: Boolean,
    onItemClick: (MediaItem) -> Unit,
    onLiveChannelClick: (Channel) -> Unit,
    onStudioClick: (CinemaStudioConfig) -> Unit,
    fetchMore: suspend (String, String, Int) -> List<MediaItem>
) {
    var selectedTab by remember { mutableStateOf(0) }
    var viewingMoreRow by remember { mutableStateOf<HomeRow?>(null) }
    
    val tabs = listOf(
        NavItem("الرئيسية", Icons.Default.Home),
        NavItem("أفلام", Icons.Default.Movie),
        NavItem("مسلسلات", Icons.Default.Tv),
        NavItem("أنمي", Icons.Default.Animation),
        NavItem("بث مباشر", Icons.Default.LiveTv)
    )

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                MainContentArea(isLoading, viewingMoreRow, selectedTab, homeData, moviesRows, seriesRows, animeRows, liveCategories, onItemClick, onLiveChannelClick, onStudioClick, { viewingMoreRow = it }, fetchMore, { viewingMoreRow = null })
            }
            NavigationRail(
                containerColor = Color(0xFF141414),
                contentColor = Color.Gray,
                modifier = Modifier.width(90.dp).border(0.5.dp, Color(0xFF2A2A2A))
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                tabs.forEachIndexed { index, item ->
                    NavigationRailItem(
                        icon = { Icon(item.icon, contentDescription = item.title, modifier = Modifier.size(28.dp)) },
                        label = { Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index; viewingMoreRow = null },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Gold, selectedTextColor = Gold,
                            unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                if (viewingMoreRow == null) {
                    NavigationBar(
                        containerColor = Color(0xFF141414),
                        contentColor = Color.Gray,
                        tonalElevation = 0.dp,
                        modifier = Modifier.border(width = 0.5.dp, color = Color(0xFF2A2A2A))
                    ) {
                        tabs.forEachIndexed { index, item ->
                            NavigationBarItem(
                                icon = { Icon(item.icon, contentDescription = item.title, modifier = Modifier.size(24.dp)) },
                                label = { Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Gold, selectedTextColor = Gold,
                                    unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            },
            containerColor = Color.Black
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                MainContentArea(isLoading, viewingMoreRow, selectedTab, homeData, moviesRows, seriesRows, animeRows, liveCategories, onItemClick, onLiveChannelClick, onStudioClick, { viewingMoreRow = it }, fetchMore, { viewingMoreRow = null })
            }
        }
    }
}

@Composable
fun MainContentArea(
    isLoading: Boolean, viewingMoreRow: HomeRow?, selectedTab: Int,
    homeData: HomeData, moviesRows: List<HomeRow>, seriesRows: List<HomeRow>, animeRows: List<HomeRow>, liveCategories: List<Category>,
    onItemClick: (MediaItem) -> Unit, onLiveChannelClick: (Channel) -> Unit, onStudioClick: (CinemaStudioConfig) -> Unit, onSeeMore: (HomeRow) -> Unit, fetchMore: suspend (String, String, Int) -> List<MediaItem>, onBack: () -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Gold) }
    } else if (viewingMoreRow != null) {
        ExpandedCategoryScreen(row = viewingMoreRow, onItemClick = onItemClick, onBack = onBack, fetchMore = fetchMore)
    } else {
        when (selectedTab) {
            0 -> HomeTabContent(homeData, onItemClick, onStudioClick, onSeeMore)
            1 -> CategorizedTabContent(moviesRows, onItemClick, onSeeMore)
            2 -> CategorizedTabContent(seriesRows, onItemClick, onSeeMore)
            3 -> CategorizedTabContent(animeRows, onItemClick, onSeeMore)
            4 -> LiveTvTabContent(liveCategories, onLiveChannelClick)
        }
    }
}

@Composable
fun ExpandedCategoryScreen(row: HomeRow, onItemClick: (MediaItem) -> Unit, onBack: () -> Unit, fetchMore: suspend (String, String, Int) -> List<MediaItem>) {
    var items by remember { mutableStateOf(row.items) }
    var page by remember { mutableStateOf(2) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, contentDescription = "عودة", tint = Color.White, modifier = Modifier.clickable { onBack() }.size(30.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = row.title, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(8.dp), modifier = Modifier.weight(1f)) {
            items(items) { item -> CinemaPosterCard(item = item, onClick = { onItemClick(item) }, modifier = Modifier.padding(8.dp).aspectRatio(0.66f)) }
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (loadingMore) { CircularProgressIndicator(color = Gold) } 
                    else {
                        Button(
                            onClick = {
                                loadingMore = true
                                scope.launch {
                                    val newItems = fetchMore(row.id, items.firstOrNull()?.section ?: "vod", page)
                                    if (newItems.isNotEmpty()) { items = items + newItems; page++ }
                                    loadingMore = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF222222))
                        ) { Text("تحميل المزيد", color = Color.White) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeTabContent(data: HomeData, onItemClick: (MediaItem) -> Unit, onStudioClick: (CinemaStudioConfig) -> Unit, onSeeMore: (HomeRow) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            if (data.hero.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { data.hero.size })
                LaunchedEffect(pagerState) {
                    while(true) {
                        delay(4000)
                        if (pagerState.pageCount > 0) pagerState.animateScrollToPage((pagerState.currentPage + 1) % pagerState.pageCount)
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(480.dp)) { page ->
                    val item = data.hero[page]
                    Box(modifier = Modifier.fillMaxSize()) {
                        CinemaPosterCard(item = item, onClick = { onItemClick(item) }, modifier = Modifier.fillMaxSize())
                        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
                        
                        Box(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp, vertical = 32.dp)) {
                            Button(
                                onClick = { onItemClick(item) },
                                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("مشاهدة الآن", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
        }
        
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(CinemaConstants.list) { studio ->
                    Box(modifier = Modifier.size(70.dp).clip(CircleShape).background(Color(0xFF1E1E1E)).border(1.dp, Color(0x33FFFFFF), CircleShape).clickable { onStudioClick(studio) }, contentAlignment = Alignment.Center) {
                        coil.compose.AsyncImage(model = studio.logoUrl, contentDescription = studio.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp))
                    }
                }
            }
        }

        items(data.rows) { row -> MediaRowSection(row, onItemClick, onSeeMore) }
    }
}

@Composable
fun CategorizedTabContent(rows: List<HomeRow>, onItemClick: (MediaItem) -> Unit, onSeeMore: (HomeRow) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        items(rows) { row -> MediaRowSection(row, onItemClick, onSeeMore) }
    }
}

@Composable
fun MediaRowSection(row: HomeRow, onItemClick: (MediaItem) -> Unit, onSeeMore: (HomeRow) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(4.dp, 20.dp).background(Gold, RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = row.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = "عرض المزيد", color = Gold, fontSize = 14.sp, modifier = Modifier.clickable { onSeeMore(row) })
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(row.items) { item ->
                CinemaPosterCard(item = item, onClick = { onItemClick(item) }, modifier = Modifier.width(130.dp).height(195.dp))
            }
        }
    }
}

@Composable
fun LiveTvTabContent(categories: List<Category>, onChannelClick: (Channel) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
        items(categories) { cat ->
            val channels = cat.channels?.values?.filter { !it.hidden }?.sortedBy { it.sortOrder } ?: emptyList()
            if (channels.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Box(modifier = Modifier.size(4.dp, 20.dp).background(Gold, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = cat.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(channels) { ch ->
                        Box(
                            modifier = Modifier
                                .width(280.dp) 
                                .aspectRatio(16f / 9f) 
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E1E))
                                .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                                .clickable { onChannelClick(ch) }
                        ) {
                            coil.compose.AsyncImage(
                                model = ch.imageUrl, contentDescription = ch.name,
                                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
