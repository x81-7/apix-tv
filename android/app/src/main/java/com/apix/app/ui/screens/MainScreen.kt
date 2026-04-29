package com.apix.app.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.app.data.Category
import com.apix.app.data.Channel
import com.apix.app.ui.components.*
import com.apix.app.ui.theme.Gold
import com.apix.app.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainScreen(
    uiState: UiState,
    onCategorySelected: (Category) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onSearchClick: () -> Unit,
    channels: List<Channel>,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    isSettings: Boolean = false,
    isDarkMode: Boolean = true,
    onToggleDarkMode: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val displayCategories = remember(uiState.categories, uiState.showSettingsSection) {
        buildList {
            addAll(uiState.categories)
            if (uiState.showSettingsSection) {
                add(Category(id = "__settings", name = "الإعدادات", sortOrder = Int.MAX_VALUE))
            }
        }
    }

    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        if (uiState.isLoading) {
            FullScreenLoader()
            return@CompositionLocalProvider
        }

        if (uiState.error != null) {
            Box(
                modifier = Modifier.fillMaxSize().background(bg),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.error, color = Color.Red, fontSize = 18.sp)
            }
            return@CompositionLocalProvider
        }

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            LandscapeLayout(
                uiState = uiState,
                categories = displayCategories,
                channels = channels,
                onCategorySelected = onCategorySelected,
                onChannelClick = onChannelClick,
                onSearchClick = onSearchClick,
                isSettings = isSettings,
                isDarkMode = isDarkMode,
                onToggleDarkMode = onToggleDarkMode,
                bg = bg,
                onBg = onBg
            )
        } else {
            PortraitLayout(
                uiState = uiState,
                categories = displayCategories,
                channels = channels,
                onCategorySelected = onCategorySelected,
                onChannelClick = onChannelClick,
                onSearchClick = onSearchClick,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                isSettings = isSettings,
                isDarkMode = isDarkMode,
                onToggleDarkMode = onToggleDarkMode,
                bg = bg,
                onBg = onBg
            )
        }
    }
}

@Composable
private fun PortraitLayout(
    uiState: UiState,
    categories: List<Category>,
    channels: List<Channel>,
    onCategorySelected: (Category) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onSearchClick: () -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    isSettings: Boolean = false,
    isDarkMode: Boolean = true,
    onToggleDarkMode: (Boolean) -> Unit = {},
    bg: Color = Color.Black,
    onBg: Color = Color.White
) {
    val selectedIndex = categories.indexOfFirst { it.id == uiState.selectedCategory?.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isSettings) ApixLogo(fontSize = 32) else Spacer(Modifier.width(1.dp))
                if (!isSettings) {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.Search, "Search", tint = onBg, modifier = Modifier.size(28.dp))
                    }
                }
            }

            if (isSettings) {
                SettingsInline(
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = onToggleDarkMode,
                    onBg = onBg
                )
            } else {
                uiState.selectedCategory?.let {
                    Text(
                        text = it.name.uppercase(),
                        color = onBg,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(channels, key = { it.id }) { channel ->
                        ChannelCard(channel = channel, onClick = { onChannelClick(channel) })
                    }
                }
            }
        }

        // Bottom navigation — always visible
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDarkMode) Color(0xFF111111) else Color(0xFFEFEFEF))
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            categories.forEachIndexed { index, cat ->
                BottomNavCategoryItem(
                    category = cat,
                    isSelected = index == selectedIndex,
                    onClick = { onCategorySelected(cat) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LandscapeLayout(
    uiState: UiState,
    categories: List<Category>,
    channels: List<Channel>,
    onCategorySelected: (Category) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onSearchClick: () -> Unit,
    isSettings: Boolean = false,
    isDarkMode: Boolean = true,
    onToggleDarkMode: (Boolean) -> Unit = {},
    bg: Color = Color.Black,
    onBg: Color = Color.White
) {
    val selectedIndex = categories.indexOfFirst { it.id == uiState.selectedCategory?.id }

    // Force LTR for layout positioning, then RTL content inside
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier = Modifier.fillMaxSize().background(bg)) {
            // Main content area (left)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        uiState.selectedCategory?.let {
                            Text(
                                text = if (isSettings) "SETTINGS" else it.name.uppercase(),
                                color = onBg,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val time = remember { mutableStateOf("") }
                            LaunchedEffect(Unit) {
                                while (true) {
                                    time.value = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                                    kotlinx.coroutines.delay(30000)
                                }
                            }
                            Text(
                                text = time.value, color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.border(1.dp, Gold, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            if (!isSettings) {
                                IconButton(onClick = onSearchClick) {
                                    Icon(Icons.Default.Search, "Search", tint = onBg)
                                }
                            }
                        }
                    }
                }

                if (isSettings) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        SettingsInline(isDarkMode, onToggleDarkMode, onBg)
                    }
                } else {
                    val config = LocalConfiguration.current
                    val cols = if (config.screenWidthDp > 900) 4 else 2
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(cols),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(channels, key = { it.id }) { channel ->
                                ChannelCard(channel = channel, onClick = { onChannelClick(channel) })
                            }
                        }
                    }
                }
            }

            // Right sidebar
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(if (isDarkMode) Color(0xFF111111) else Color(0xFFEFEFEF))
                    .padding(vertical = 16.dp, horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Gold bar + APiX
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .background(Gold, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.height(4.dp))
                ApixLogo(fontSize = 24)

                Spacer(Modifier.height(24.dp))

                // Category list
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val idx = categories.indexOf(cat)
                            SidebarCategoryItem(
                                category = cat,
                                isSelected = idx == selectedIndex,
                                onClick = { onCategorySelected(cat) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsInline(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onBg: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val cardBg = if (isDarkMode) Color(0xFF1A1A1A) else Color(0xFFEAEAEA)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Text("SETTINGS", color = onBg, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Switch(
                checked = isDarkMode,
                onCheckedChange = onToggleDarkMode,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Gold,
                    checkedTrackColor = Gold.copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color(0xFF333333)
                )
            )
            Text("Dark Mode", color = onBg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(12.dp))
                .clickable {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/apix_tv")))
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("←", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Telegram Channel", color = onBg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(12.dp))
                .clickable {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/apix_support")))
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("←", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Contact Us", color = onBg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}
