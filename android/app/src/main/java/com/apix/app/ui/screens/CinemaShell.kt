package com.apix.app.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem
import com.apix.app.data.HomeRow
import com.apix.app.data.Category
import com.apix.app.ui.theme.Gold
import kotlinx.coroutines.launch

data class NavItem(val title: String, val icon: ImageVector)

@Composable
fun CinemaShell(
    homeData: HomeData,
    moviesRows: List<HomeRow>,
    seriesRows: List<HomeRow>,
    animeRows: List<HomeRow>,
    liveCategories: List<Category>,
    isLoading: Boolean,
    onItemClick: (MediaItem) -> Unit,
    onLiveChannelClick: (MediaItem) -> Unit,
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Gold)
        } else if (viewingMoreRow != null) {
            // شاشة "عرض المزيد" مع الترقيم
            ExpandedCategoryScreen(
                row = viewingMoreRow!!,
                onItemClick = onItemClick,
                onBack = { viewingMoreRow = null },
                fetchMore = fetchMore
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> HomeTabContent(homeData, onItemClick) { viewingMoreRow = it }
                        1 -> CategorizedTabContent(moviesRows, onItemClick) { viewingMoreRow = it }
                        2 -> CategorizedTabContent(seriesRows, onItemClick) { viewingMoreRow = it }
                        3 -> CategorizedTabContent(animeRows, onItemClick) { viewingMoreRow = it }
                        4 -> LiveTvTabContent(liveCategories, onLiveChannelClick)
                    }
                }
                
                // شريط التنقل السفلي الزجاجي الأنيق
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(Color(0xCC1A1A1A)) 
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(40.dp))
                        .padding(vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, item ->
                            val isSelected = index == selectedTab
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedTab = index },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = if (isSelected) Gold else Color.Gray,
                                    modifier = Modifier.size(26.dp)
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(modifier = Modifier.size(5.dp).clip(RoundedCornerShape(2.5.dp)).background(Gold))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpandedCategoryScreen(
    row: HomeRow, 
    onItemClick: (MediaItem) -> Unit, 
    onBack: () -> Unit,
    fetchMore: suspend (String, String, Int) -> List<MediaItem>
) {
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
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(items) { item ->
                CinemaPosterCard(item = item, onClick = { onItemClick(item) }, modifier = Modifier.padding(8.dp).aspectRatio(0.66f))
            }
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (loadingMore) {
                        CircularProgressIndicator(color = Gold)
                    } else {
                        Button(
                            onClick = {
                                loadingMore = true
                                scope.launch {
                                    val newItems = fetchMore(row.id, items.firstOrNull()?.section ?: "vod", page)
                                    if (newItems.isNotEmpty()) {
                                        items = items + newItems
                                        page++
                                    }
                                    loadingMore = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                        ) {
                            Text("تحميل المزيد", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTabContent(data: HomeData, onItemClick: (MediaItem) -> Unit, onSeeMore: (HomeRow) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 90.dp)) {
        item {
            if (data.hero.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                    CinemaPosterCard(item = data.hero[0], onClick = { onItemClick(data.hero[0]) }, modifier = Modifier.fillMaxSize())
                    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black))))
                }
            }
        }
        items(data.rows) { row -> MediaRowSection(row, onItemClick, onSeeMore) }
    }
}

@Composable
fun CategorizedTabContent(rows: List<HomeRow>, onItemClick: (MediaItem) -> Unit, onSeeMore: (HomeRow) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 90.dp)) {
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

// البث المباشر: يعرض الأقسام بشكل أفقي، وقنوات 16:9 
@Composable
fun LiveTvTabContent(categories: List<Category>, onChannelClick: (MediaItem) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 90.dp)) {
        items(categories) { cat ->
            val channels = cat.channels?.values?.filter { !it.hidden }?.sortedBy { it.sortOrder } ?: emptyList()
            if (channels.isNotEmpty()) {
                Text(
                    text = "بث مباشر: ${cat.name}",
                    color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(channels) { ch ->
                        Box(
                            modifier = Modifier
                                .width(160.dp).aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E1E))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                                .clickable { 
                                    onChannelClick(MediaItem(id = ch.id, title = ch.name, poster = ch.imageUrl, section = "live", directUrl = ch.stream?.url, useLocalProxy = ch.useLocalProxy)) 
                                }
                        ) {
                            coil.compose.AsyncImage(
                                model = ch.imageUrl, contentDescription = ch.name,
                                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
