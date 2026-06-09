package com.apix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.app.vod.extractors.SubtitleSource
import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.CharcoalCard

@Composable
fun SubtitleSelectionScreen(
    subtitles: List<SubtitleSource>,
    onSelect: (SubtitleSource?) -> Unit,
    onSettingsClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val arSubs = subtitles.filter { it.language.contains("ar", true) }
    val enSubs = subtitles.filter { it.language.contains("en", true) }
    val otherSubs = subtitles.filter { !it.language.contains("ar", true) && !it.language.contains("en", true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121212))
                .clickable(enabled = false, onClick = {})
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Subtitle Sources",
                    color = Gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Row {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val interactionOff = remember { MutableInteractionSource() }
                    val isOffFocused by interactionOff.collectIsFocusedAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CharcoalCard)
                            .border(
                                width = if (isOffFocused) 2.dp else 0.dp,
                                color = if (isOffFocused) Gold else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(interactionSource = interactionOff, indication = null) { onSelect(null) }
                            .focusable(interactionSource = interactionOff)
                            .padding(16.dp)
                    ) {
                        Text(text = "Disable Subtitles", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (arSubs.isNotEmpty()) {
                    item { SubtitleGroupHeader("Arabic") }
                    itemsIndexed(arSubs) { index, sub ->
                        SubtitleItemRow("Arabic ${index + 1}", sub, onSelect)
                    }
                }

                if (enSubs.isNotEmpty()) {
                    item { SubtitleGroupHeader("English") }
                    itemsIndexed(enSubs) { index, sub ->
                        SubtitleItemRow("English ${index + 1}", sub, onSelect)
                    }
                }

                if (otherSubs.isNotEmpty()) {
                    item { SubtitleGroupHeader("Other") }
                    itemsIndexed(otherSubs) { index, sub ->
                        SubtitleItemRow("${sub.language} ${index + 1}", sub, onSelect)
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleGroupHeader(title: String) {
    Text(
        text = title,
        color = Gold,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 8.dp)
    )
}

@Composable
private fun SubtitleItemRow(
    label: String,
    sub: SubtitleSource,
    onSelect: (SubtitleSource) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CharcoalCard)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Gold else Color.Transparent,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onSelect(sub) }
            .focusable(interactionSource = interactionSource)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Subtitles, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = if (isFocused) Gold else Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
