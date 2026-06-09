package com.apix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.apix.app.data.MediaItem
import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.DarkBackground
import com.apix.app.ui.theme.CharcoalCard

data class TvSeason(val seasonNumber: Int, val name: String)
data class TvEpisode(val episodeNumber: Int, val name: String, val stillUrl: String, val duration: String)

@Composable
fun DetailsScreen(
    item: MediaItem,
    similarItems: List<MediaItem>,
    seasons: List<TvSeason>,
    episodes: List<TvEpisode>,
    onPlayClick: (MediaItem) -> Unit,
    onEpisodeClick: (TvEpisode) -> Unit,
    onSeasonSelect: (TvSeason) -> Unit,
    onSimilarItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSeason by remember { mutableStateOf(seasons.firstOrNull()) }

    Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
        AsyncImage(
            model = item.backdrop.ifBlank { item.poster },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.65f).align(Alignment.TopCenter)
        )

        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, DarkBackground.copy(alpha = 0.85f), DarkBackground),
                    startY = 0f,
                    endY = 1200f
                )
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 300.dp, bottom = 60.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 42.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.rating.isNotBlank()) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = item.rating, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        if (item.year.isNotBlank()) {
                            Text(text = item.year, color = Color.LightGray, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                        }
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (item.section == "series") "مسلسل" else if (item.section == "anime") "أنمي" else "فيلم",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    val btnBg = if (isFocused) Gold else Color.Transparent
                    val btnContent = if (isFocused) Color.Black else Color.White
                    val btnBorder = if (isFocused) Color.Transparent else Color.White.copy(alpha = 0.5f)

                    Button(
                        onClick = { onPlayClick(item) },
                        colors = ButtonDefaults.buttonColors(containerColor = btnBg, contentColor = btnContent),
                        shape = RoundedCornerShape(10.dp),
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(54.dp)
                            .border(2.dp, btnBorder, RoundedCornerShape(10.dp))
                            .focusable(interactionSource = interactionSource)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "شاهد الآن", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            color = Color(0xFFCCCCCC),
                            fontSize = 15.sp,
                            lineHeight = 24.sp
                        )
                    }
                }
            }

            if (item.section == "series" || item.section == "anime") {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    if (seasons.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(seasons) { season ->
                                val isSelected = season == selectedSeason
                                val interactionSource = remember { MutableInteractionSource() }
                                val isFocused by interactionSource.collectIsFocusedAsState()
                                val chipBg = if (isSelected) Gold else CharcoalCard
                                val chipText = if (isSelected) Color.Black else Color.White
                                val chipBorder = if (isFocused) Gold else Color.Transparent

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(chipBg)
                                        .border(2.dp, chipBorder, RoundedCornerShape(8.dp))
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = {
                                                selectedSeason = season
                                                onSeasonSelect(season)
                                            }
                                        )
                                        .focusable(interactionSource = interactionSource)
                                        .padding(horizontal = 20.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = season.name,
                                        color = chipText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                items(episodes) { episode ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    val borderC = if (isFocused) Gold else Color.Transparent

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CharcoalCard)
                            .border(2.dp, borderC, RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onEpisodeClick(episode) }
                            )
                            .focusable(interactionSource = interactionSource)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            if (episode.stillUrl.isNotBlank()) {
                                AsyncImage(
                                    model = episode.stillUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(28.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = episode.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = episode.duration, color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }

            if (similarItems.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(40.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Box(modifier = Modifier.size(width = 4.dp, height = 20.dp).background(Gold, RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = "أعمال مشابهة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(similarItems) { simItem ->
                            CinemaPosterCard(
                                item = simItem,
                                onClick = { onSimilarItemClick(simItem) },
                                modifier = Modifier.width(130.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
