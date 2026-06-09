package com.apix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.apix.app.ui.theme.Gold
import com.apix.app.ui.theme.CharcoalCard

data class SubtitleConfig(
    val fontSize: Float = 18f,
    val textColor: Color = Color.White,
    val backgroundColor: Color = Color.Transparent,
    val bottomOffset: Int = 30
)

@Composable
fun SubtitleSettingsScreen(
    currentConfig: SubtitleConfig,
    onConfigChanged: (SubtitleConfig) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sizeOptions = listOf(14f, 18f, 24f, 32f)
    val sizeLabels = listOf("Small", "Medium", "Large", "Huge")
    
    val colorOptions = listOf(Color.White, Gold, Color(0xFF00FF00), Color(0xFF00FFFF))
    
    val bgOptions = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f), Color.Black)
    val bgLabels = listOf("None", "Transparent", "Dark")

    val posOptions = listOf(10, 30, 60, 100)
    val posLabels = listOf("Low", "Default", "High", "Top")

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
                Text("Subtitle Settings", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingRow("Font Size", sizeOptions, sizeLabels, currentConfig.fontSize) { 
                onConfigChanged(currentConfig.copy(fontSize = it as Float)) 
            }
            Spacer(modifier = Modifier.height(20.dp))

            ColorSettingRow("Font Color", colorOptions, currentConfig.textColor) { 
                onConfigChanged(currentConfig.copy(textColor = it)) 
            }
            Spacer(modifier = Modifier.height(20.dp))

            SettingRow("Background", bgOptions, bgLabels, currentConfig.backgroundColor) { 
                onConfigChanged(currentConfig.copy(backgroundColor = it as Color)) 
            }
            Spacer(modifier = Modifier.height(20.dp))

            SettingRow("Position", posOptions, posLabels, currentConfig.bottomOffset) { 
                onConfigChanged(currentConfig.copy(bottomOffset = it as Int)) 
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    options: List<Any>,
    labels: List<String>,
    currentValue: Any,
    onSelect: (Any) -> Unit
) {
    Column {
        Text(title, color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(options.size) { index ->
                val opt = options[index]
                val label = labels[index]
                val isSelected = opt == currentValue
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Gold else CharcoalCard)
                        .border(
                            width = if (isFocused) 2.dp else 0.dp,
                            color = if (isFocused) Color.White else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable(interactionSource = interactionSource, indication = null) { onSelect(opt) }
                        .focusable(interactionSource = interactionSource)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSettingRow(
    title: String,
    colors: List<Color>,
    currentColor: Color,
    onSelect: (Color) -> Unit
) {
    Column {
        Text(title, color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(colors) { color ->
                val isSelected = color == currentColor
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(color)
                        .border(
                            width = if (isSelected || isFocused) 3.dp else 0.dp,
                            color = if (isFocused) Color.White else if (isSelected) Gold else Color.Transparent,
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable(interactionSource = interactionSource, indication = null) { onSelect(color) }
                        .focusable(interactionSource = interactionSource)
                )
            }
        }
    }
}
