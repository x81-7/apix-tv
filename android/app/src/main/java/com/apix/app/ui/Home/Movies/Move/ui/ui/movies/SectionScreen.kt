package com.lagradost.cloudstream3.apix.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.apix.data.*
import com.lagradost.cloudstream3.apix.ui.components.*

@Composable
fun SectionScreen(
    title: String,
    kind: ApixKind,
    state: ApixUiState,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onPlay: (ApixItem) -> Unit,
    onFavorite: (String) -> Unit,
    onLoadMore: (String) -> Unit = {},
    onOpenSection: (String) -> Unit = {},
    sectionId: String? = null,
) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        AppTopBar(title = title, onBack = onBack)
        if (sectionId != null) {
            val sectionItems = state.catalog.sectionById(sectionId)?.items.orEmpty()
            val loadedPages = (state.catalog.pages[sectionId] ?: 1).coerceAtLeast(1)
            val visibleCount = loadedPages * 20
            val visibleItems = sectionItems.take(visibleCount)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visibleItems, key = { it.id }) { item -> PosterCard(item, onClick = { onOpenItem(item.id) }) }
                if (visibleItems.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LoadMoreButton(state.loadingMore) { onLoadMore(sectionId) }
                    }
                }
            }
        } else {
            val sections = state.catalog.categorySections(kind).filter { it.items.isNotEmpty() }
            val moreFocusers = remember(sections.map { it.id }) {
                sections.associate { it.id to FocusRequester() }
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), modifier = Modifier.fillMaxSize()) {
                sections.forEachIndexed { index, section ->
                    item(key = "head:${section.id}") {
                        Spacer(Modifier.height(6.dp))
                    }
                    item(key = "rail:${section.id}") {
                        val moreFocus = moreFocusers.getValue(section.id)
                        val nextMoreFocus = sections.getOrNull(index + 1)?.let { moreFocusers.getValue(it.id) }
                        Column(Modifier.fillMaxWidth()) {
                            SectionHeader(section.title, onMore = { onOpenSection(section.id) }, moreFocusRequester = moreFocus)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            ) {
                                items(section.items.take(10), key = { it.id }) { item ->
                                    PosterCard(
                                        item = item,
                                        modifier = Modifier.focusProperties {
                                            if (nextMoreFocus != null) down = nextMoreFocus
                                        },
                                        onClick = { onOpenItem(item.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
