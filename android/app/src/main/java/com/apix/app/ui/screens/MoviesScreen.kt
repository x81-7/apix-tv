package com.apix.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem

/** Movies tab — a poster grid of all `vod` items from the cinema catalog. */
@Composable
fun MoviesScreen(
    data: HomeData?,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    CinemaGrid(
        title = "أفلام",
        items = data?.itemsForSection("vod") ?: emptyList(),
        onItemClick = onItemClick,
        modifier = modifier
    )
}
