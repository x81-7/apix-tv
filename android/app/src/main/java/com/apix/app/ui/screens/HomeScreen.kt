package com.apix.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.apix.app.data.HomeData
import com.apix.app.data.MediaItem
import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.DarkBackground
import com.apix.app.ui.theme.DarkSurface
import com.apix.app.ui.theme.CharcoalCard

/**
 * بيانات استوديو الإنتاج لشريط الشركات
 */
data class StudioItem(val name: String, val logoUrl: String)

val studiosList = listOf(
    StudioItem("Netflix", "https://upload.wikimedia.org/wikipedia/commons/0/08/Netflix_2015_logo.svg"),
    StudioItem("Marvel", "https://upload.wikimedia.org/wikipedia/commons/7/71/Marvel-Comics-Logo.svg"),
    StudioItem("HBO", "https://upload.wikimedia.org/wikipedia/commons/d/de/HBO_logo.svg"),
    StudioItem("Disney+", "https://upload.wikimedia.org/wikipedia/commons/3/3e/Disney%2B_logo.svg"),
    StudioItem("Paramount+", "https://upload.wikimedia.org/wikipedia/commons/a/a5/Paramount%2B_logo.svg")
)

/**
 * الشاشة الرئيسية للسينما والمحتوى الرقمي
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    data: HomeData?,
    onItemClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (data == null || data.isEmpty) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لا يوجد محتوى متاح حالياً",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            return
        }

        // استخدام LazyColumn لضمان كفاءة التمرير والتحميل للأجهزة الضعيفة وشاشات التلفاز
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. السلايدر الرئيسي (Hero Carousel)
            if (data.hero.isNotEmpty()) {
                item {
                    CinemaHeroCarousel(heroItems = data.hero, onItemClick = onItemClick)
                }
            }

            // 2. شريط الشركات (Studios Row)
            item {
                StudiosHorizontalRow(onStudioClick = { studioName ->
                    // سيتم ربط فتح شاشة الشبكة الخاصة بالشركة هنا لاحقاً
                })
            }

            // 3. صفوف المحتوى الديناميكي القادم من السيرفر (أحدث الأفلام، أحدث المسلسلات...)
            items(data.rows) { row ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    // عنوان القسم مع الخط العمودي الذهبي على اليمين
                    SectionHeader(title = row.title)
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    // الصف الأفقي للبوسترات
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(row.items) { item ->
                            MediaPosterCard(item = item, onClick = { onItemClick(item) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * السلايدر الرئيسي العالي الدقة (Hero Carousel)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CinemaHeroCarousel(
    heroItems: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit
) {
    // نأخذ أشهر 6 عناصر كحد أقصى (2 أفلام، 2 مسلسلات، 2 أنمي) كما طلبت
    val limitedHero = heroItems.take(6)
    val pagerState = rememberPagerState(pageCount = { limitedHero.size })

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val item = limitedHero[page]
            
            Box(modifier = Modifier.fillMaxSize()) {
                // صورة الخلفية الكبيرة للعمل (Backdrop)
                AsyncImage(
                    model = item.backdrop.ifBlank { item.poster },
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // التدرج اللوني الأسود لدمج الصورة مع خلفية التطبيق النقية
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black
                                )
                            )
                        )
                )

                // معلومات العمل والأزرار المتموضعة في الأسفل
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    if (item.rating.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = item.rating, color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = item.year, color = Color.LightGray, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // أزرار التحكم المتوافقة مع التلفاز والجوال
                    val interactionSource = remember { MutableInteractionSource() }
                    val isFocused by interactionSource.collectIsFocusedAsState()
                    val buttonBg = if (isFocused) Gold else Color.White.copy(alpha = 0.15f)
                    val buttonContentColor = if (isFocused) Color.Black else Color.White

                    Button(
                        onClick = { onItemClick(item) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonBg,
                            contentColor = buttonContentColor
                        ),
                        shape = RoundedCornerShape(10.dp),
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .border(
                                width = if (isFocused) 0.dp else 1.dp,
                                color = Color.White.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .focusable(interactionSource = interactionSource)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "شاهد الآن", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // مؤشرات الصفحات (Dots) في السلايدر
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            repeat(limitedHero.size) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 18.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Gold else Color.Gray.copy(alpha = 0.5f))
                )
            }
        }
    }
}

/**
 * شريط شركات الإنتاج (Studios Row)
 */
@Composable
private fun StudiosHorizontalRow(
    onStudioClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "الشركات العالمية")
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(studiosList) { studio ->
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                val borderColor = if (isFocused) Gold else Color.Transparent

                Box(
                    modifier = Modifier
                        .size(width = 130.dp, height = 70.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurface)
                        .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onStudioClick(studio.name) }
                        )
                        .focusable(interactionSource = interactionSource),
                    contentAlignment = Alignment.Center
                ) {
                    // عرض الشعار النصي أو الأيقونة بدقة داخل الحاوية الداكنة
                    Text(
                        text = studio.name,
                        color = if (isFocused) Gold else Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * عنوان القسم مع الخط العمودي الذهبي المصمم خصيصاً لليمين (RTL)
 */
@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // الخط العمودي الذهبي بسمك 4dp على اليمين تماماً
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 20.dp)
                .background(Gold, RoundedCornerShape(2.dp))
        )
        
        Spacer(modifier = Modifier.width(10.dp))
        
        Text(
            text = title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * كارت البوستر الذكي للمحتوى (Media Card) المقاوم للثغرات الرسومية
 */
@Composable
private fun MediaPosterCard(
    item: MediaItem,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) Gold else Color.Transparent

    Column(
        modifier = Modifier
            .width(125.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .size(width = 125.dp, height = 185.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CharcoalCard)
                .border(2.dp, borderColor, RoundedCornerShape(12.dp))
                .focusable(interactionSource = interactionSource)
        ) {
            // صورة البوستر الأساسية للفيلم/المسلسل
            if (item.poster.isNotBlank()) {
                AsyncImage(
                    model = item.poster,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // شارة التقييم (Badge) في أعلى اليسار
            if (item.rating.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                        .border(0.5.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .align(Alignment.TopStart)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Gold, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = item.rating, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // اسم العمل تحت البوستر سطر واحد مع ثلاثة نقاط عند الزيادة
        Text(
            text = item.title,
            color = if (isFocused) Gold else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
    }
}
