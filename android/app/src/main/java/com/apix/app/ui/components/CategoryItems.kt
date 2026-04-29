package com.apix.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.app.data.Category
import com.apix.app.ui.theme.Gold

fun getCategoryIcon(name: String): ImageVector {
    val lower = name.lowercase()
    return when {
        lower.contains("sport") -> Icons.Default.SportsSoccer
        lower.contains("movie") || lower.contains("film") || lower.contains("أفلام") -> Icons.Default.Movie
        lower.contains("net") || lower.contains("شبك") -> Icons.Default.Language
        lower.contains("relig") || lower.contains("دين") || lower.contains("إسلام") -> Icons.Default.Mosque
        lower.contains("setting") || lower.contains("إعدادات") -> Icons.Default.Settings
        else -> Icons.Default.LiveTv
    }
}

/**
 * Bottom nav category item (portrait/mobile)
 */
@Composable
fun BottomNavCategoryItem(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected -> Gold
            isFocused -> Color.White
            else -> Color(0xFF888888)
        },
        label = "iconColor"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isSelected -> Gold
            isFocused -> Color.White
            else -> Color(0xFF888888)
        },
        label = "textColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = getCategoryIcon(category.name),
            contentDescription = category.name,
            tint = iconColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = category.name,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Sidebar category item (landscape/TV)
 */
@Composable
fun SidebarCategoryItem(
    category: Category,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> Gold
            isFocused -> Color(0xFF333333)
            else -> Color.Transparent
        },
        label = "sidebarBg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.Black
            isFocused -> Gold
            else -> Color(0xFFCCCCCC)
        },
        label = "sidebarContent"
    )
    val borderColor = if (isFocused && !isSelected) Gold else Color.Transparent

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor, RoundedCornerShape(12.dp))
            .border(
                width = if (isFocused && !isSelected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            imageVector = getCategoryIcon(category.name),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.name.uppercase(),
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
