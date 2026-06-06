package com.apix.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Carousel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.apix.app.data.HomeRow
import com.apix.app.data.MediaItem
import com.apix.app.ui.theme.Gold

/** Pure black APiX cinema background. */
val CinemaBlack = Color(0xFF0A0A0A)

/**
 * Shared cinema UI primitives used by HomeScreen / MoviesScreen / SeriesScreen /
 * AnimeScreen. The visual language is APiX's: pure #0A0A0A background, a gold
 * focus border, a soft neon gold glow and a smooth scale-up on focus (D-pad on
 * TV, touch on phone).
 */

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinemaHero(items: List<MediaItem>, onItemClick: (MediaItem) -> Unit) {
    if (items.isEmpty()) return
    Carousel(
        itemCount = items.size,
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) { index ->
        val item = items[index]
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = item.backdrop.ifBlank { item.poster },
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xE6000000))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.rating.isNotBlank() || item.year.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = listOf("★ ${item.rating}".takeIf { item.rating.isNotBlank() }, item.year.takeIf { it.isNotBlank() })
                            .filterNotNull().joinToString("  •  "),
                        color = Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.description,
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.65f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinemaPosterRow(row: HomeRow, onItemClick: (MediaItem) -> Unit) {
    Column {
        Text(
            text = row.title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
        )
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(row.items) { item ->
                CinemaPosterCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinemaPosterCard(item: MediaItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.12f else 1f, label = "posterScale")

    androidx.tv.material3.Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .width(132.dp)
            .scale(scale)
    ) {
        Box {
            AsyncImage(
                model = item.poster.ifBlank { item.backdrop },
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(2f / 3f)
                    .then(
                        if (isFocused) Modifier.shadow(18.dp, RoundedCornerShape(10.dp), spotColor = Gold, ambientColor = Gold)
                        else Modifier
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF161616))
                    .then(
                        if (isFocused) Modifier.border(3.dp, Gold, RoundedCornerShape(10.dp))
                        else Modifier
                    )
            )
            if (item.rating.isNotBlank()) {
                Text(
                    text = "★ ${item.rating}",
                    color = Gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** A simple poster grid used by the Movies / Series / Anime tabs. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CinemaGrid(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(CinemaBlack)) {
        if (items.isEmpty()) {
            Text(
                "لا يوجد محتوى متاح حالياً",
                color = Color.White,
                fontSize = 16.sp,
                modifier = Modifier.align(Alignment.Center)
            )
            return
        }
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    CinemaPosterCard(item = item, onClick = { onItemClick(item) })
                }
            }
        }
    }
}
