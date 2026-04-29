package com.apix.pc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.pc.data.Category
import com.apix.pc.data.Channel
import com.apix.pc.ui.AppViewModel
import com.apix.pc.ui.components.ApixLogo
import com.apix.pc.ui.components.ChannelCard
import com.apix.pc.ui.components.SidebarCategoryItem
import com.apix.pc.ui.theme.Gold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Windows home — pixel-faithful port of
 * `android/app/src/main/java/com/apix/app/ui/screens/MainScreen.kt` (landscape variant):
 *   • Right-side category rail with gold accent bar + APiX wordmark
 *   • Main grid filtered by selected category
 *   • Top bar with category name + clock
 */
@Composable
fun HomeScreen(vm: AppViewModel, onChannelClick: (Channel) -> Unit) {
    val selectedId = vm.selectedCategoryId.value
    val visibleCats = vm.categories.filter { !it.hidden }
    val selectedCat = visibleCats.firstOrNull { it.id == selectedId } ?: visibleCats.firstOrNull()
    val channels = vm.channelsFor(selectedCat?.id).filter { !it.hidden }

    if (vm.loading.value && vm.channels.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Gold)
        }
        return
    }

    Row(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // ─── Main content area ───────────────────────────────────────────
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            TopBar(title = selectedCat?.name ?: "")
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cols = if (maxWidth >= 1200.dp) 5
                           else if (maxWidth >= 900.dp) 4
                           else if (maxWidth >= 640.dp) 3
                           else 2
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cols),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(channels, key = { it.id }) { ch ->
                        ChannelCard(channel = ch, onClick = { onChannelClick(ch) })
                    }
                }
            }
        }

        // ─── Right sidebar with categories ───────────────────────────────
        Column(
            modifier = Modifier
                .width(240.dp)
                .fillMaxHeight()
                .background(Color(0xFF111111))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Gold accent bar
            Box(
                modifier = Modifier
                    .width(4.dp).height(40.dp)
                    .background(Gold, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.height(4.dp))
            ApixLogo(fontSize = 24)

            Spacer(Modifier.height(24.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(visibleCats, key = { it.id }) { cat ->
                    SidebarCategoryItem(
                        category = cat,
                        isSelected = cat.id == selectedCat?.id,
                        onClick = { vm.selectedCategoryId.value = cat.id }
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String) {
    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(30_000)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = time,
                color = Gold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .border(1.dp, Gold, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
        }
    }
}
