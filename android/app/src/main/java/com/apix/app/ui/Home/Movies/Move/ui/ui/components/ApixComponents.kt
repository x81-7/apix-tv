package com.lagradost.cloudstream3.apix.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.apix.data.*
import com.lagradost.cloudstream3.apix.ui.theme.*

private data class NavSpec(val tab: ApixTab, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun AppTopBar(title: String, onSearch: (() -> Unit)? = null, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text(text = title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (onSearch != null) IconButton(onClick = onSearch) { Icon(Icons.Default.Search, null, tint = Color.White) }
    }
}

/**
 * Transparent action button that highlights in gold when focused (TV remote) or pressed (touch).
 */
@Composable
fun ApixActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val active = focused || pressed
    val scale by animateFloatAsState(if (active) 1.06f else 1f, label = "action_scale")
    val bg by animateColorAsState(if (active) ApixGold else Color.Transparent, label = "action_bg")
    val borderColor by animateColorAsState(
        when { active -> ApixGold; highlighted -> ApixGold; else -> Color(0xFF4A4A4A) },
        label = "action_border",
    )
    val fg = if (active) Color.Black else Color.White
    Surface(
        color = bg,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.scale(scale)
            .border(if (active) 3.dp else 1.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, tint = if (active) Color.Black else ApixGold)
            Spacer(Modifier.width(8.dp))
            Text(text, color = fg, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HeroCard(item: ApixItem, favorite: Boolean, onPlay: () -> Unit, onFav: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val heroBorder by animateColorAsState(if (focused) ApixGold else Color.Transparent, label = "hero_border")
    Box(
        modifier = modifier.fillMaxWidth().height(360.dp).clip(shape)
            .border(if (focused) 3.dp else 0.dp, heroBorder, shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF2A241A), Color(0xFF0B0B0B))))
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay)
            .focusable(interactionSource = interaction)
    ) {
        val art = item.backdropUrl.ifBlank { item.posterUrl }
        if (art.isNotBlank()) {
            AsyncImage(
                model = art,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.92f)))))
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(item.subtitle.ifBlank { "الأبرز الآن" }, color = ApixGold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(item.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(item.overview, color = Color(0xFFE6E6E6), fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ApixActionButton(text = "شاهد الآن", icon = Icons.Default.PlayArrow, onClick = onPlay)
                ApixActionButton(
                    text = if (favorite) "في المفضلة" else "أضف للمفضلة",
                    icon = if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    onClick = onFav,
                    highlighted = favorite,
                )
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    onMore: (() -> Unit)? = null,
    moreFocusRequester: FocusRequester? = null,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).height(24.dp).background(ApixGold, RoundedCornerShape(999.dp)))
            Spacer(Modifier.width(10.dp))
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }
        if (onMore != null) {
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            Surface(
                color = if (focused) ApixGold else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .then(if (moreFocusRequester != null) Modifier.focusRequester(moreFocusRequester) else Modifier)
                    .border(if (focused) 3.dp else 1.dp, if (focused) ApixGold else Color(0xFF3A3A3A), RoundedCornerShape(16.dp))
                    .clickable(interactionSource = interaction, indication = null, onClick = onMore)
                    .focusable(interactionSource = interaction),
            ) {
                Text("المزيد", color = if (focused) Color.Black else ApixGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
fun StudioCard(studio: ApixStudio, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "studio_scale")
    val borderColor by animateColorAsState(if (focused) ApixGold else Color(0xFF303030), label = "studio_border")
    Surface(modifier = modifier.scale(scale).size(width = 170.dp, height = 104.dp).border(if (focused) 3.dp else 1.dp, borderColor, RoundedCornerShape(22.dp)).clickable(interactionSource = interaction, indication = null, onClick = onClick).focusable(interactionSource = interaction), color = ApixSurface, shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(studio.name, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
            Text(studio.overview, color = ApixGray, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
        }
    }
}

@Composable
fun PosterCard(item: ApixItem, focused: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hasFocus by interaction.collectIsFocusedAsState()
    val active = focused || hasFocus
    val scale by animateFloatAsState(if (active) 1.08f else 1f, label = "poster_scale")
    val border by animateColorAsState(if (active) ApixGold else Color(0xFF2A2A2A), label = "poster_border")
    val borderWidth = if (active) 3.dp else 1.dp
    Column(modifier = modifier.width(160.dp).scale(scale).clickable(interactionSource = interaction, indication = null, onClick = onClick).focusable(interactionSource = interaction)) {
        Box(modifier = Modifier.height(240.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF202020)).border(borderWidth, border, RoundedCornerShape(22.dp)), contentAlignment = Alignment.BottomStart) {

            if (item.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                )
            }
            Column(modifier = Modifier.padding(14.dp)) {
                if (item.rating.isNotBlank()) {
                    Text("★ ${item.rating}", color = ApixGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                }
                Text(item.year.ifBlank { "" }, color = ApixGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (item.posterUrl.isBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(item.title, color = Color.White, fontWeight = FontWeight.ExtraBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.subtitle, color = ApixGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CategoryPill(title: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) ApixGold else ApixSurface2
    val fg = if (selected) Color.Black else Color.White
    Surface(color = bg, shape = RoundedCornerShape(999.dp), modifier = Modifier.clickable(onClick = onClick)) {
        Text(title, color = fg, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SettingsRow(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ApixSurface).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = ApixGray, fontSize = 12.sp)
        }
        trailing()
    }
}

private val NAV_TABS = listOf(
    NavSpec(ApixTab.HOME, "الرئيسية", Icons.Default.Home),
    NavSpec(ApixTab.MOVIES, "أفلام", Icons.Default.Movie),
    NavSpec(ApixTab.SERIES, "مسلسلات", Icons.Default.Tv),
    NavSpec(ApixTab.ANIME, "أنمي", Icons.Default.PlayCircle),
    NavSpec(ApixTab.SETTINGS, "إعدادات", Icons.Default.Settings),
)

@Composable
fun BottomNav(current: ApixTab, onTab: (ApixTab) -> Unit, isTv: Boolean = false, modifier: Modifier = Modifier) {
    NavigationBar(modifier = modifier.fillMaxWidth().height(64.dp), containerColor = Color(0xFF0B0B0B)) {
        NAV_TABS.forEach { spec ->
            val selected = spec.tab == current
            NavigationBarItem(
                selected = selected,
                onClick = { onTab(spec.tab) },
                icon = { Icon(spec.icon, null) },
                label = { Text(spec.label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ApixGold, selectedTextColor = ApixGold,
                    unselectedIconColor = Color.Gray, unselectedTextColor = Color.Gray,
                    indicatorColor = Color(0xFF1A1A1A),
                ),
            )
        }
    }
}

/** Vertical navigation rail for landscape phones and TVs. Pinned to the right. */
@Composable
fun SideNav(current: ApixTab, onTab: (ApixTab) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.background(Color(0xFF0B0B0B)).padding(vertical = 16.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NAV_TABS.forEach { spec ->
            val selected = spec.tab == current
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()
            val bg = when { selected -> ApixGold; focused -> Color(0xFF2A2A2A); else -> ApixSurface }
            val fg = if (selected) Color.Black else Color.White
            Surface(
                color = bg,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
                    .clickable(interactionSource = interaction, indication = null) { onTab(spec.tab) }
                    .focusable(interactionSource = interaction),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(spec.icon, null, tint = fg)
                    Spacer(Modifier.width(12.dp))
                    Text(spec.label, color = fg, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}


/** "المزيد" button used at the end of a paged grid. */
@Composable
fun LoadMoreButton(loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
        if (loading) {
            CircularProgressIndicator(color = ApixGold, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
        } else {
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = ApixGold, contentColor = Color.Black),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Default.ExpandMore, null)
                Spacer(Modifier.width(8.dp))
                Text("المزيد", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A single episode row with a thumbnail and a play button. */
@Composable
fun EpisodeRow(episode: ApixEpisode, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(if (focused) ApixGold else Color.Transparent, label = "ep_border")
    val borderWidth = if (focused) 2.dp else 1.dp
    Row(
        modifier = modifier.fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(borderWidth, border, RoundedCornerShape(16.dp))
            .background(ApixSurface2)
            .clickable(interactionSource = interaction, indication = null, onClick = onPlay)
            .focusable(interactionSource = interaction)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(width = 108.dp, height = 62.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center,
        ) {
            if (episode.stillUrl.isNotBlank()) {
                AsyncImage(
                    model = episode.stillUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Icon(Icons.Default.PlayArrow, null, tint = ApixGold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${episode.number}. ${episode.title}",
                color = Color.White, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (episode.overview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(episode.overview, color = ApixGray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (episode.duration.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(episode.duration, color = ApixGray, fontSize = 12.sp)
        }
    }
}
