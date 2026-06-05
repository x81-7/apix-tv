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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.TvLazyRow
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.Carousel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.apix.app.data.CinemaRepository
import com.apix.app.data.HomeData
import com.apix.app.data.HomeRow
import com.apix.app.data.MediaItem
import com.apix.app.ui.theme.Gold

/**
 * Cinema Home — built with androidx.tv.material3 so it works identically on
 * phone and Android TV. A Hero [Carousel] sits on top, with focus-aware
 * [TvLazyRow] poster rows beneath it. Reacts to [appMode]:
 *   • SPORTS_ONLY → live rows
 *   • CINEMA_ONLY / HYBRID → movies/series rows
 *
 * Focus system: each poster scales up and shows a gold border when focused
 * (D-pad on TV, touch on phone).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    appMode: String,
    externalSourceUrl: String,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var data by remember { mutableStateOf<HomeData?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(appMode, externalSourceUrl) {
        loading = true
        data = CinemaRepository.loadHome(
            com.apix.app.data.AppSettings(appMode = appMode, externalSourceUrl = externalSourceUrl)
        )
        loading = false
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        when {
            loading -> {
                CircularProgressIndicator(color = Gold, modifier = Modifier.align(Alignment.Center))
            }
            data == null || data!!.isEmpty -> {
                Text(
                    "لا يوجد محتوى متاح حالياً",
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                TvLazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (data!!.hero.isNotEmpty()) {
                        item { HeroCarousel(items = data!!.hero, onItemClick = onItemClick) }
                    }
                    items(data!!.rows) { row ->
                        PosterRow(row = row, onItemClick = onItemClick)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroCarousel(items: List<MediaItem>, onItemClick: (MediaItem) -> Unit) {
    Carousel(
        itemCount = items.size,
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
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
                            colors = listOf(Color.Transparent, Color(0xCC000000))
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
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = item.description,
                        color = Color(0xFFCCCCCC),
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterRow(row: HomeRow, onItemClick: (MediaItem) -> Unit) {
    Column {
        Text(
            text = row.title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
        )
        TvLazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(row.items) { item ->
                PosterCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun PosterCard(item: MediaItem, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.12f else 1f, label = "posterScale")

    androidx.tv.material3.Card(
        onClick = onClick,
        interactionSource = interaction,
        modifier = Modifier
            .width(130.dp)
            .scale(scale)
    ) {
        Box {
            AsyncImage(
                model = item.poster.ifBlank { item.backdrop },
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(130.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(10.dp))
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
