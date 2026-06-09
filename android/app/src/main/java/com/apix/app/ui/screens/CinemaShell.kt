package com.apix.app.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem
import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.DarkBackground
import com.apix.app.ui.theme.CharcoalCard

/**
 * البيانات المهيكلة للأقسام (Tabs)
 */
data class NavItem(val title: String, val icon: ImageVector)

val hybridNavItems = listOf(
    NavItem("الرئيسية", Icons.Default.Home),
    NavItem("أفلام", Icons.Default.Movie),
    NavItem("مسلسلات", Icons.Default.Tv),
    NavItem("أنمي", Icons.Default.Animation),
    NavItem("مباشر", Icons.Default.LiveTv)
)

/**
 * الإطار الحاوي للتطبيق (Shell) المتوافق مع الجوال والتلفاز
 */
@Composable
fun CinemaShell(
    data: HomeData?,
    isLoading: Boolean,
    onItemClick: (MediaItem) -> Unit,
    onLiveChannelClick: (Any) -> Unit, // Any مؤقتاً حتى نربط كلاس القنوات
    modifier: Modifier = Modifier
) {
    // تحديد ما إذا كان الجهاز تلفاز أو في وضع أفقي (Landscape)
    val configuration = LocalConfiguration.current
    val isLandscapeOrTv = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
            (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

    // قراءة وضع التطبيق (رياضة فقط أم هجين)
    val appMode = data?.appMode?.uppercase() ?: "HYBRID"
    val isLiveOnly = appMode == "LIVE_ONLY" || appMode == "SPORT_ONLY"

    // إذا كان الوضع "مباشر فقط"، نعرض واجهة البث المباشر الأصلية مع إخفاء أي أشرطة تنقل VOD
    if (isLiveOnly) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
                // سيتم استدعاء تصميم MainScreen (البث المباشر) هنا لاحقاً
                Text("شاشة البث المباشر الكلاسيكية", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }
        return
    }

    // --- وضع الهجين (HYBRID) ---
    var selectedTab by remember { mutableIntStateOf(0) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = modifier.fillMaxSize().background(DarkBackground)) {
            
            if (isLandscapeOrTv) {
                // 📺 واجهة التلفاز (شريط جانبي - Navigation Rail)
                Row(modifier = Modifier.fillMaxSize()) {
                    // الشريط الجانبي
                    TvNavigationRail(
                        items = hybridNavItems,
                        selectedIndex = selectedTab,
                        onItemSelected = { selectedTab = it },
                        modifier = Modifier.width(90.dp).fillMaxHeight()
                    )
                    
                    // المحتوى
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        ShellContent(
                            selectedTab = selectedTab,
                            data = data,
                            isLoading = isLoading,
                            onItemClick = onItemClick,
                            onLiveChannelClick = onLiveChannelClick
                        )
                    }
                }
            } else {
                // 📱 واجهة الجوال (شريط سفلي - Bottom Navigation)
                Column(modifier = Modifier.fillMaxSize()) {
                    // المحتوى
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ShellContent(
                            selectedTab = selectedTab,
                            data = data,
                            isLoading = isLoading,
                            onItemClick = onItemClick,
                            onLiveChannelClick = onLiveChannelClick
                        )
                    }
                    
                    // الشريط السفلي
                    MobileBottomBar(
                        items = hybridNavItems,
                        selectedIndex = selectedTab,
                        onItemSelected = { selectedTab = it }
                    )
                }
            }
        }
    }
}

/**
 * المحتوى المتغير بناءً على القسم المختار
 */
@Composable
private fun ShellContent(
    selectedTab: Int,
    data: HomeData?,
    isLoading: Boolean,
    onItemClick: (MediaItem) -> Unit,
    onLiveChannelClick: (Any) -> Unit
) {
    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold)
        }
        return
    }

    when (selectedTab) {
        0 -> HomeScreen(data = data, onItemClick = onItemClick)
        1 -> MoviesScreen(data = data, onItemClick = onItemClick)
        2 -> SeriesScreen(data = data, onItemClick = onItemClick)
        3 -> AnimeScreen(data = data, onItemClick = onItemClick)
        4 -> {
            // واجهة البث المباشر المعزولة داخل الهجين
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("شاشة البث المباشر المخصصة للوضع الهجين", color = Gold)
            }
        }
    }
}

/**
 * 📺 شريط التنقل الجانبي للتلفاز (مخصص بدقة لريموت التلفاز)
 */
@Composable
private fun TvNavigationRail(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black) // أسود نقي كما طلبت
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()

            // التأثير: إطار ذهبي فقط عند التركيز بدون تكبير الحجم
            val borderColor = if (isFocused) Gold else Color.Transparent
            val iconTint = if (isSelected || isFocused) Gold else Color.Gray

            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) CharcoalCard else Color.Transparent)
                    .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { onItemSelected(index) }
                    )
                    .focusable(interactionSource = interactionSource),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * 📱 شريط التنقل السفلي للهاتف (بخلفية شفافة/ضبابية)
 */
@Composable
private fun MobileBottomBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xD9000000)) // أسود شفاف قليلاً
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            val iconTint = if (isSelected) Gold else Color.Gray

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onItemSelected(index) }
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
