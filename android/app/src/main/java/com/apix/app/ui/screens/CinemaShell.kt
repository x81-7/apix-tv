package com.apix.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem
import com.apix.app.data.HomeRow
import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.DarkBackground

data class NavItem(val title: String, val icon: ImageVector)

@Composable
fun CinemaShell(
    homeData: HomeData,
    moviesRows: List<HomeRow>,
    seriesRows: List<HomeRow>,
    animeRows: List<HomeRow>,
    liveChannels: List<MediaItem>,
    isLoading: Boolean,
    onItemClick: (MediaItem) -> Unit,
    onLiveChannelClick: (MediaItem) -> Unit,
    onSeeMoreClick: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
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
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> HomeTabContent(homeData, onItemClick, onSeeMoreClick)
                        1 -> CategorizedTabContent(moviesRows, onItemClick, onSeeMoreClick)
                        2 -> CategorizedTabContent(seriesRows, onItemClick, onSeeMoreClick)
                        3 -> CategorizedTabContent(animeRows, onItemClick, onSeeMoreClick)
                        4 -> LiveTvTabContent(liveChannels, onLiveChannelClick)
                    }
                }
                // شريط التنقل السفلي الاحترافي العائم
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xE6121212)) // أسود شفاف أنيق
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(30.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
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
                                    modifier = Modifier.size(24.dp)
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(modifier = Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(Gold))
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
fun HomeTabContent(data: HomeData, onItemClick: (MediaItem) -> Unit, onSeeMoreClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)) {
        item {
            if (data.hero.isNotEmpty()) {
                // البانر العلوي (الهيرو)
                Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    CinemaPosterCard(item = data.hero[0], onClick = { onItemClick(data.hero[0]) }, modifier = Modifier.fillMaxSize())
                }
            }
        }
        items(data.rows) { row ->
            MediaRowSection(row, onItemClick, onSeeMoreClick)
        }
    }
}

@Composable
fun CategorizedTabContent(rows: List<HomeRow>, onItemClick: (MediaItem) -> Unit, onSeeMoreClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 80.dp)) {
        items(rows) { row ->
            MediaRowSection(row, onItemClick, onSeeMoreClick)
        }
    }
}

@Composable
fun MediaRowSection(row: HomeRow, onItemClick: (MediaItem) -> Unit, onSeeMoreClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(4.dp, 18.dp).background(Gold, RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = row.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = "المزيد",
                color = Gold,
                fontSize = 14.sp,
                modifier = Modifier.clickable { onSeeMoreClick(row.title) }
            )
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(row.items) { item ->
                CinemaPosterCard(item = item, onClick = { onItemClick(item) }, modifier = Modifier.width(120.dp).height(180.dp))
            }
        }
    }
}

// تبويب البث المباشر (أبعاد 16:9 الاحترافية للقنوات الرياضية)
@Composable
fun LiveTvTabContent(channels: List<MediaItem>, onChannelClick: (MediaItem) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp, bottom = 80.dp)) {
        Text(
            text = "قنوات البث المباشر",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(channels) { channel ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f) // 👈 السر هنا: شعارات 16:9 للقنوات!
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                        .clickable { onChannelClick(channel) }
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp))
                ) {
                    coil.compose.AsyncImage(
                        model = channel.poster,
                        contentDescription = channel.title,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                }
            }
        }
    }
}
