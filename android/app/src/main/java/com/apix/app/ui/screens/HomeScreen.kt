package com.apix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import androidx.tv.foundation.lazy.list.items
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem

/**
 * Cinema Home — built with androidx.tv.material3 so it works identically on
 * phone and Android TV. A hero carousel sits on top, with focus-aware poster
 * rows beneath. Data is loaded once by [CinemaShell] and passed in, so the
 * Movies/Series/Anime tabs reuse the same fetch.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    data: HomeData?,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(CinemaBlack)) {
        when {
            data == null || data.isEmpty -> {
                Text(
                    "لا يوجد محتوى متاح حالياً",
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            else -> {
                TvLazyColumn(
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    if (data.hero.isNotEmpty()) {
                        item { CinemaHero(items = data.hero, onItemClick = onItemClick) }
                    }
                    items(data.rows) { row ->
                        CinemaPosterRow(row = row, onItemClick = onItemClick)
                    }
                }
            }
        }
    }
}
