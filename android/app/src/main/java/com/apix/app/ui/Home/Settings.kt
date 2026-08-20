package com.apix.app.ui.Home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF050505)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.86f).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("الإعدادات", color = Color.White, fontSize = 30.sp)
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color(0xFFFFC94A))
                    }
                }
                SettingsItem("الحساب", "إعدادات الحساب والتفعيل")
                SettingsItem("المظهر", "إعدادات واجهة التطبيق")
                SettingsItem("عن APiX", "معلومات التطبيق والإصدار")
                Text("هذا القسم جاهز للتوسعة من لوحة الإعدادات لاحقًا.", color = Color(0xFF8E877A), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun SettingsItem(title: String, subtitle: String) {
    val source = remember { MutableInteractionSource() }
    val focused = source.collectIsFocusedAsState().value
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF121212), RoundedCornerShape(18.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFFFFC94A) else Color(0x332A2114), RoundedCornerShape(18.dp))
            .focusable(interactionSource = source)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 18.sp)
            Text(subtitle, color = Color(0xFF918C83), fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text("›", color = Color(0xFFFFC94A), fontSize = 30.sp)
    }
}
