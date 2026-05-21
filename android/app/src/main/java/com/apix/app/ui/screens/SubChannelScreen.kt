package com.apix.app.ui.screens

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.apix.app.data.Channel
import com.apix.app.ui.theme.CharcoalCard
import com.apix.app.ui.theme.Gold
import kotlinx.coroutines.delay

@Composable
fun SubChannelScreen(
    menuName: String,
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit,
    onBack: () -> Unit,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // حالة التحميل عند الضغط على قناة
    var loadingChannel by remember { mutableStateOf<Channel?>(null) }

    // عند الضغط: أظهر دائرة لثانية ثم افتح القناة
    LaunchedEffect(loadingChannel) {
        val ch = loadingChannel ?: return@LaunchedEffect
        delay(800L)
        loadingChannel = null
        onChannelClick(ch)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val config = LocalConfiguration.current
        val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val cols = if (isLandscape) {
            if (config.screenWidthDp > 900) 4 else 3
        } else 2

        Box(modifier = modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // شريط علوي
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val backInteraction = remember { MutableInteractionSource() }
                    val backFocused by backInteraction.collectIsFocusedAsState()
                    val backPressed by backInteraction.collectIsPressedAsState()

                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .focusable(interactionSource = backInteraction)
                            .then(
                                if (backFocused || backPressed)
                                    Modifier.border(2.dp, Gold, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) {
                                append("AP")
                            }
                            withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.ExtraBold)) {
                                append("iX ")
                            }
                            withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.Bold)) {
                                append(menuName)
                            }
                        },
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.width(48.dp))
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(channels, key = { it.id }) { channel ->
                        SubChannelCard(
                            channel = channel,
                            isLoading = loadingChannel?.id == channel.id,
                            onClick = {
                                if (loadingChannel == null) {
                                    loadingChannel = channel
                                }
                            }
                        )
                    }
                }
            }

            // دائرة تحميل مركزية عند الضغط
            if (loadingChannel != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Gold,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SubChannelCard(
    channel: Channel,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHighlighted = isFocused || isPressed || isLoading

    val scale by animateFloatAsState(
        targetValue = if (isHighlighted) 1.05f else 1f,
        label = "subScale"
    )

    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isHighlighted) 3.dp else 1.dp,
                color = if (isHighlighted) Gold else Color(0xFF444444),
                shape = RoundedCornerShape(12.dp)
            )
            .background(CharcoalCard, RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
    ) {
        if (channel.imageUrl.isNotEmpty()) {
            AsyncImage(
                model = channel.imageUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // مؤشر تحميل صغير على البطاقة المضغوطة
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Gold,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}