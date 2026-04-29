package com.apix.pc.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.apix.pc.ui.theme.Gold

/**
 * Track / quality / audio selection. Mirrors the Android
 * `dialog_track_selection.xml` look (dark card, gold accents, RTL text).
 */
@Composable
fun TrackSelectionDialog(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Color(0xFF111418),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(min = 260.dp, max = 360.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    title,
                    color = Gold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (items.isEmpty()) {
                    Text("لا توجد خيارات متاحة", color = Color.White.copy(alpha = 0.6f),
                         fontSize = 14.sp,
                         modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    items.forEachIndexed { i, label ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { onSelect(i); onDismiss() }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(18.dp)
                                    .background(
                                        if (i == selectedIndex) Gold else Color.Transparent,
                                        RoundedCornerShape(50)
                                    )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = Color.White, fontSize = 15.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("إغلاق", color = Gold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
