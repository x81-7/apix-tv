package com.apix.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem

/** Series tab — a poster grid of all `series` items from the cinema catalog. */
@Composable
fun SeriesScreen(
    data: HomeData?,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    CinemaGrid(
        title = "مسلسلات",
        items = data?.itemsForSection("series") ?: emptyList(),
        onItemClick = onItemClick,
        modifier = modifier
    )
}
