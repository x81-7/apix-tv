package com.apix.pc.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.apix.pc.data.Category
import com.apix.pc.data.Channel
import com.apix.pc.ui.theme.CharcoalCard
import com.apix.pc.ui.theme.Gold

/** APiX wordmark (white "AP" + gold "iX") — same look as Android. */
@Composable
fun ApixLogo(fontSize: Int = 28, modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) { append("AP") }
            withStyle(SpanStyle(color = Gold,       fontWeight = FontWeight.ExtraBold)) { append("iX") }
        },
        fontSize = fontSize.sp,
        modifier = modifier
    )
}

fun categoryIcon(name: String): ImageVector {
    val l = name.lowercase()
    return when {
        l.contains("sport") -> Icons.Default.SportsSoccer
        l.contains("movie") || l.contains("film") || l.contains("أفلام") -> Icons.Default.Movie
        l.contains("net") || l.contains("شبك") -> Icons.Default.Language
        l.contains("relig") || l.contains("دين") || l.contains("إسلام") -> Icons.Default.Mosque
        l.contains("setting") || l.contains("إعدادات") -> Icons.Default.Settings
        else -> Icons.Default.LiveTv
    }
}

/** Channel card — 16:9 image, gradient label, gold hover border (Android parity). */
@Composable
fun ChannelCard(channel: Channel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val highlighted = hovered || pressed

    val scale by animateFloatAsState(if (highlighted) 1.05f else 1f, label = "cardScale")
    val border = if (highlighted) Gold else Color(0xFF333333)
    val borderW = if (highlighted) 3.dp else 1.dp

    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .border(borderW, border, RoundedCornerShape(12.dp))
            .shadow(if (highlighted) 12.dp else 0.dp, RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        if (!channel.imageUrl.isNullOrBlank()) {
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
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
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
    }
}

/** Sidebar item used in landscape layouts — Android parity. */
@Composable
fun SidebarCategoryItem(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        when {
            isSelected -> Gold
            hovered    -> Color(0xFF333333)
            else       -> Color.Transparent
        }, label = "sbBg"
    )
    val fg by animateColorAsState(
        when {
            isSelected -> Color.Black
            hovered    -> Gold
            else       -> Color(0xFFCCCCCC)
        }, label = "sbFg"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg, RoundedCornerShape(12.dp))
            .border(
                width = if (hovered && !isSelected) 2.dp else 0.dp,
                color = if (hovered && !isSelected) Gold else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(categoryIcon(category.name), null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            category.name.uppercase(),
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}