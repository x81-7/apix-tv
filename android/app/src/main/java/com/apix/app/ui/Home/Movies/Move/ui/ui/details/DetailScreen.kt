package com.lagradost.cloudstream3.apix.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.apix.data.*
import com.lagradost.cloudstream3.apix.ui.components.*
import com.lagradost.cloudstream3.apix.ui.theme.*

@Composable
fun DetailScreen(
    state: ApixUiState,
    itemId: String,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onPlay: (ApixItem) -> Unit,
    onPlayEpisode: (ApixItem, ApixEpisode) -> Unit,
    onLoadEpisodes: (ApixItem) -> Unit,
    onFavorite: (String) -> Unit,
) {
    val item = state.catalog.itemById(itemId)
        ?: state.searchResults.firstOrNull { it.id == itemId }
    val similar = remember(item) { item?.similarIds.orEmpty().mapNotNull { state.catalog.itemById(it) } }

    // Season currently selected in the season picker.
    var selectedSeason by remember(itemId) { mutableStateOf<Int?>(null) }
    val seasons = item?.seasons.orEmpty()
    val activeSeason = seasons.firstOrNull { it.number == selectedSeason } ?: seasons.firstOrNull()

    LaunchedEffect(itemId) { item?.let(onLoadEpisodes) }


    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AppTopBar(title = item?.title ?: "التفاصيل", onBack = onBack)
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                if (item != null) {
                    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                        HeroCard(
                            item = item,
                            favorite = state.favorites.contains(item.id),
                            onPlay = { onPlay(item) },
                            onFav = { onFavorite(item.id) },
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(18.dp))
                if (item != null) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (item.year.isNotBlank()) Text(item.year, color = ApixGold, fontWeight = FontWeight.Bold)
                        if (item.genre.isNotBlank()) { Text("•", color = ApixGray); Text(item.genre, color = ApixGray) }
                        if (item.duration.isNotBlank()) { Text("•", color = ApixGray); Text(item.duration, color = ApixGray) }
                        if (item.rating.isNotBlank()) { Text("•", color = ApixGray); Text("★ ${item.rating}", color = ApixGray) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        item.overview, color = Color(0xFFE8E8E8), fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 18.dp),
                        maxLines = 6, overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (item != null && item.kind != ApixKind.MOVIE) {
                item {
                    Spacer(Modifier.height(22.dp))
                    SectionHeader("الأجزاء والحلقات")
                }
                if (state.detailLoading && item.seasons.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = ApixGold, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        }
                    }
                } else if (item.seasons.isEmpty()) {
                    item {
                        Text(
                            "لا توجد حلقات متاحة لهذا العمل",
                            color = ApixGray,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    // Season picker — only the selected season's episodes are shown.
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        ) {
                            items(seasons) { season ->
                                CategoryPill(
                                    title = season.title.ifBlank { "الموسم ${season.number}" },
                                    selected = season.number == (activeSeason?.number ?: -1),
                                    onClick = { selectedSeason = season.number },
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            "${activeSeason?.episodes?.size ?: 0} حلقة",
                            color = ApixGray, fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                        )
                    }
                    items(activeSeason?.episodes.orEmpty()) { ep ->
                        Box(modifier = Modifier.padding(horizontal = 18.dp)) {
                            EpisodeRow(episode = ep, onPlay = { onPlayEpisode(item, ep) })
                        }
                    }
                }

            }

            if (similar.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    SectionHeader("عناصر مشابهة")
                }
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp),
                    ) {
                        items(similar) { s -> PosterCard(item = s, onClick = { onOpenItem(s.id) }) }
                    }
                }
            }
        }
    }
}
