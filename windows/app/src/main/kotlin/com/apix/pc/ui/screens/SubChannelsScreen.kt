package com.apix.pc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.pc.data.Channel
import com.apix.pc.ui.components.ChannelCard
import com.apix.pc.ui.theme.Gold

/**
 * Sub-channels grid for an open_submenu channel.
 * Mirrors `android/.../ui/screens/SubChannelScreen.kt`.
 */
@Composable
fun SubChannelsScreen(
    menuName: String,
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) { append("AP") }
                    withStyle(SpanStyle(color = Gold,       fontWeight = FontWeight.ExtraBold)) { append("iX ") }
                    withStyle(SpanStyle(color = Gold,       fontWeight = FontWeight.Bold))      { append(menuName) }
                },
                fontSize = 22.sp
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cols = if (maxWidth >= 1200.dp) 5
                       else if (maxWidth >= 900.dp) 4
                       else if (maxWidth >= 640.dp) 3
                       else 2
            LazyVerticalGrid(
                columns = GridCells.Fixed(cols),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(channels, key = { it.id }) { ch ->
                    ChannelCard(channel = ch, onClick = { onChannelClick(ch) })
                }
            }
        }
    }
}