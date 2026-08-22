package com.lagradost.cloudstream3.apix.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.apix.data.*
import com.lagradost.cloudstream3.apix.ui.components.*
import com.lagradost.cloudstream3.apix.ui.theme.*

@Composable
fun SettingsScreen(
    state: ApixUiState,
    onBack: () -> Unit,
    onToggleLanguage: (String) -> Unit,
    onToggleDownloads: (Boolean) -> Unit,
    onToggleBypass: (Boolean) -> Unit,
    onReload: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())) {
        AppTopBar(title = "الإعدادات", onBack = onBack)
        Spacer(Modifier.height(6.dp))
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("عام") {}
            SettingsRow("اللغة", "العربية / English") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onToggleLanguage("ar") }, label = { Text("AR") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (state.language == "ar") ApixGold else ApixSurface, labelColor = if (state.language == "ar") Color.Black else Color.White))
                    AssistChip(onClick = { onToggleLanguage("en") }, label = { Text("EN") }, colors = AssistChipDefaults.assistChipColors(containerColor = if (state.language == "en") ApixGold else ApixSurface, labelColor = if (state.language == "en") Color.Black else Color.White))
                }
            }
            SettingsRow("التنزيلات", "إظهار/إخفاء قسم التنزيلات") {
                Switch(checked = state.downloadsEnabled, onCheckedChange = onToggleDownloads, colors = SwitchDefaults.colors(checkedThumbColor = ApixGold, checkedTrackColor = ApixGold.copy(alpha = 0.45f)))
            }
            SettingsRow("تجاوز مزود الخدمة", "تشغيل خاصية التجاوز فقط عند الحاجة") {
                Switch(checked = state.bypassIsp, onCheckedChange = onToggleBypass, colors = SwitchDefaults.colors(checkedThumbColor = ApixGold, checkedTrackColor = ApixGold.copy(alpha = 0.45f)))
            }
            SectionHeader("المزامنة") {}
            SettingsRow("الوركر", if (state.workerStatus.isBlank()) "لم يتم الربط بعد" else state.workerStatus) { TextButton(onClick = onReload) { Text("إعادة تحميل", color = ApixGold) } }
            Text("يتم جلب)
        }
        Spacer(Modifier.height(32.dp))
    }
}
