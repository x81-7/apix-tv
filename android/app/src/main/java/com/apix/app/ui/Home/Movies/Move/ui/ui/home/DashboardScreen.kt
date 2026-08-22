package com.lagradost.cloudstream3.apix.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.apix.ApixRoute
import com.lagradost.cloudstream3.apix.data.*
import com.lagradost.cloudstream3.apix.ui.components.*
import com.lagradost.cloudstream3.apix.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    state: ApixUiState,
    isTv: Boolean,
    onOpen: (ApixRoute) -> Unit,
    onPlay: (ApixItem) -> Unit,
    onFavorite: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val featured = state.catalog.featuredItems()
    var index by remember(featured) { mutableIntStateOf(0) }
    LaunchedEffect(featured) {
        while (true) {
            delay(4200)
            if (featured.isNotEmpty()) index = (index + 1) % featured.size
        }
    }
    val now = remember { Date() }
    val dateText = remember(now) { SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now) }

    Column(modifier = Modifier.fillMaxSize().background(ApixBackground).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.CalendarMonth, null, tint = ApixGold)
                Text("$dateText", color = ApixGold, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onSearch) { Icon(Icons.Default.Search, null, tint = Color.White); Spacer(Modifier.width(8.dp)); Text("بحث", color = Color.White) }
        }
        if (featured.isNotEmpty()) {
            HeroCard(item = featured[index], favorite = state.favorites.contains(featured[index].id), onPlay = { onPlay(featured[index]) }, onFav = { onFavorite(featured[index].id) }, modifier = Modifier.padding(horizontal = 18.dp))
        }
        Spacer(Modifier.height(18.dp))
        SectionHeader("الشركات والشبكات")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
            items(state.catalog.studios) { studio ->
                StudioCard(studio = studio, onClick = { onOpen(ApixRoute.Movies) })
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionHeader("أفلام حديثة") { onOpen(ApixRoute.Movies) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
            items(state.catalog.itemsOf(ApixKind.MOVIE).take(10)) { item ->
                PosterCard(item = item, onClick = { onOpen(ApixRoute.Detail(item.id)) })
            }
        }
        Spacer(Modifier.height(6.dp))
        SectionHeader("مسلسلات رائعة") { onOpen(ApixRoute.Series) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
            items(state.catalog.itemsOf(ApixKind.SERIES).take(10)) { item ->
                PosterCard(item = item, onClick = { onOpen(ApixRoute.Detail(item.id)) })
            }
        }
        Spacer(Modifier.height(6.dp))
        SectionHeader("أنمي مختار") { onOpen(ApixRoute.Anime) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
            items(state.catalog.itemsOf(ApixKind.ANIME).take(10)) { item ->
                PosterCard(item = item, onClick = { onOpen(ApixRoute.Detail(item.id)) })
            }
        }
        Spacer(Modifier.height(6.dp))
        SectionHeader("سلاسل الأفلام") { onOpen(ApixRoute.Browse(ApixSectionCategory.MOVIE_COLLECTIONS.key)) }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)) {
            val collections = state.catalog.sectionById(ApixSectionCategory.MOVIE_COLLECTIONS.key)?.items.orEmpty().ifEmpty { state.catalog.itemsOf(ApixKind.MOVIE).take(10) }
            items(collections.take(10)) { item ->
                PosterCard(item = item, onClick = { onOpen(ApixRoute.Detail(item.id)) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!isTv) Spacer(Modifier.height(18.dp))
    }
}
