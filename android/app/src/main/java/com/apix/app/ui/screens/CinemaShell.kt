package com.apix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Text
import com.apix.app.data.AppSettings
import com.apix.app.data.CinemaRepository
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem
import com.apix.app.ui.theme.Gold

/** A custom tab injected by the host (e.g. Live TV or Settings). */
data class CinemaTab(val label: String, val content: @Composable () -> Unit)

/**
 * CinemaShell — the root container shown when app_mode is CINEMA_ONLY or HYBRID.
 *
 * It loads the unified [HomeData] ONCE (from the Cloudflare cinema worker) and
 * powers four built-in tabs (الرئيسية / أفلام / مسلسلات / أنمي). The host can
 * inject [leadingTabs] (e.g. "مباشر" Live in HYBRID mode) and [trailingTabs]
 * (e.g. "الإعدادات"). The bottom bar uses APiX's gold-on-black styling.
 */
@Composable
fun CinemaShell(
    appMode: String,
    externalSourceUrl: String,
    onMediaClick: (MediaItem) -> Unit,
    leadingTabs: List<CinemaTab> = emptyList(),
    trailingTabs: List<CinemaTab> = emptyList(),
    modifier: Modifier = Modifier
) {
    var data by remember { mutableStateOf<HomeData?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(appMode, externalSourceUrl) {
        loading = true
        data = CinemaRepository.loadHome(
            AppSettings(appMode = appMode, externalSourceUrl = externalSourceUrl)
        )
        loading = false
    }

    // Built-in cinema tabs.
    val cinemaTabs = remember(data) {
        listOf(
            CinemaTab("الرئيسية") { HomeScreen(data = data, onItemClick = onMediaClick) },
            CinemaTab("أفلام") { MoviesScreen(data = data, onItemClick = onMediaClick) },
            CinemaTab("مسلسلات") { SeriesScreen(data = data, onItemClick = onMediaClick) },
            CinemaTab("أنمي") { AnimeScreen(data = data, onItemClick = onMediaClick) }
        )
    }

    val tabs = remember(data, leadingTabs, trailingTabs) {
        leadingTabs + cinemaTabs + trailingTabs
    }

    var selected by remember { mutableIntStateOf(leadingTabs.size) } // default to "الرئيسية"

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = modifier.fillMaxSize().background(CinemaBlack)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (loading && tabs.getOrNull(selected)?.label in listOf("الرئيسية", "أفلام", "مسلسلات", "أنمي")) {
                    CircularProgressIndicator(color = Gold, modifier = Modifier.align(Alignment.Center))
                } else {
                    tabs.getOrNull(selected)?.content?.invoke()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0E0E0E))
                    .padding(vertical = 6.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isSel = index == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) Gold.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable { selected = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            color = if (isSel) Gold else Color(0xFFBBBBBB),
                            fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
