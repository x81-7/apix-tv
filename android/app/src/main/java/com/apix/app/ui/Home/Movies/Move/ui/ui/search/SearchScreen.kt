package com.lagradost.cloudstream3.apix.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.apix.data.*
import com.lagradost.cloudstream3.apix.ui.components.*
import com.lagradost.cloudstream3.apix.ui.theme.*

private enum class SearchFilter(val label: String, val kind: ApixKind?) {
    ALL("الكل", null),
    MOVIES("أفلام", ApixKind.MOVIE),
    SERIES("مسلسلات", ApixKind.SERIES),
    ANIME("أنمي", ApixKind.ANIME),
}

/**
 * Dedicated search results screen: results are grouped by type (movies / series / anime),
 * with a filter row, a back button and a "المزيد" button that pulls the next page.
 */
@Composable
fun SearchScreen(
    state: ApixUiState,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onPlay: (ApixItem) -> Unit,
    onFavorite: (String) -> Unit,
    onSearch: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
) {
    var query by remember { mutableStateOf(state.searchQuery) }
    var filter by remember { mutableStateOf(SearchFilter.ALL) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val results = remember(state.searchResults, filter) {
        state.searchResults.filter { filter.kind == null || it.kind == filter.kind }
    }
    val movies = results.filter { it.kind == ApixKind.MOVIE }
    val series = results.filter { it.kind == ApixKind.SERIES }
    val anime = results.filter { it.kind == ApixKind.ANIME }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AppTopBar(title = "البحث", onBack = onBack)

        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp).fillMaxWidth()
                .background(Color(0xFF151515), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = Color.Gray)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium),
                cursorBrush = SolidColor(ApixGold),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = ""; onSearch("") }) {
                    Icon(Icons.Default.Close, null, tint = Color.Gray)
                }
            }
            Button(
                onClick = { onSearch(query) },
                colors = ButtonDefaults.buttonColors(containerColor = ApixGold, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp),
            ) { Text("بحث", fontWeight = FontWeight.Bold) }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        ) {
            items(SearchFilter.values().toList()) { f ->
                CategoryPill(title = f.label, selected = f == filter, onClick = { filter = f })
            }
        }

        if (state.searchLoading && state.searchResults.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ApixGold, strokeWidth = 3.dp)
            }
            return@Column
        }

        if (state.searchResults.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (state.searchQuery.isBlank()) "اكتب اسم فيلم أو مسلسل ثم اضغط بحث"
                    else "لا توجد نتائج لـ \"${state.searchQuery}\"",
                    color = ApixGray,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            contentPadding = PaddingValues(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            listOf(
                "الأفلام" to movies,
                "المسلسلات" to series,
                "الأنمي" to anime,
            ).forEach { (title, list) ->
                if (list.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            SectionHeader("$title (${list.size})")
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    items(list) { item ->
                        PosterCard(item = item, onClick = { onOpenItem(item.id) })
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LoadMoreButton(loading = state.searchLoading, onClick = onLoadMore)
            }
        }
    }
}
