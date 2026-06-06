package com.apix.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem

/** Anime tab — a poster grid of all `anime` items from the cinema catalog. */
@Composable
fun AnimeScreen(
    data: HomeData?,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    CinemaGrid(
        title = "أنمي",
        items = data?.itemsForSection("anime") ?: emptyList(),
        onItemClick = onItemClick,
        modifier = modifier
    )
}
