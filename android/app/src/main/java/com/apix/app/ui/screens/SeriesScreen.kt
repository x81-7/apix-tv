package com.apix.app.ui.screens

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem

@Composable
fun SeriesScreen(
    data: HomeData?,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()
    
    CinemaGrid(
        title = "مسلسلات",
        items = data?.itemsForSection("series") ?: emptyList(),
        gridState = gridState,
        onItemClick = onItemClick,
        onLoadMore = {  },
        modifier = modifier
    )
}
