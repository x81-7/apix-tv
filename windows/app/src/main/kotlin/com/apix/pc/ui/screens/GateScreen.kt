package com.apix.pc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apix.pc.data.StreamConfig
import com.apix.pc.ui.AppViewModel
import com.apix.pc.ui.components.ApixLogo
import com.apix.pc.ui.theme.Gold

/**
 * Manual stream gate — equivalent of `GateActivity` on Android.
 * Two modes:
 *   1. Bypass: type the panel `bypassCode` (default 2026) → unlocks the app and
 *      jumps straight to the Home screen.
 *   2. Direct play: paste a stream URL + (optional) DRM ClearKey to test a
 *      single channel without browsing the catalogue.
 */
@Composable
fun GateScreen(
    vm: AppViewModel,
    onUnlock: () -> Unit,
    onDirectPlay: (StreamConfig) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var clearKey by remember { mutableStateOf("") }
    var userAgent by remember { mutableStateOf("") }
    var referer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val expected = vm.bypassCode.value

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 460.dp).padding(24.dp)
        ) {
            ApixLogo(fontSize = 44)
            Spacer(Modifier.height(8.dp))
            Text(
                vm.gateTitle.value.ifBlank { "تشغيل يدوي" },
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Text(
                vm.gateSubtitle.value.ifBlank { "أدخل كود الدخول أو رابط بث للتشغيل المباشر" },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // ─── Bypass code ─────────────────────────────────────────────
            GateField(
                value = code, onValueChange = { code = it.filter { c -> c.isDigit() }.take(8) },
                placeholder = "كود الدخول (مثال 2026)",
                isPassword = true, keyboardType = KeyboardType.NumberPassword,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (expected.isNotBlank() && code.trim() == expected.trim()) onUnlock()
                    else error = "كود غير صحيح"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("فتح التطبيق", fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 22.dp),
                color = Color.White.copy(alpha = 0.12f)
            )

            // ─── Direct stream ───────────────────────────────────────────
            Text("تشغيل رابط مباشر", color = Color.White, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
            Spacer(Modifier.height(8.dp))
            GateField(value = url, onValueChange = { url = it }, placeholder = "رابط البث (m3u8 / mpd / mp4)")
            Spacer(Modifier.height(8.dp))
            GateField(value = clearKey, onValueChange = { clearKey = it }, placeholder = "ClearKey  (KID:KEY)  — اختياري")
            Spacer(Modifier.height(8.dp))
            GateField(value = userAgent, onValueChange = { userAgent = it }, placeholder = "User-Agent — اختياري")
            Spacer(Modifier.height(8.dp))
            GateField(value = referer, onValueChange = { referer = it }, placeholder = "Referer — اختياري")

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    if (url.isBlank()) { error = "أدخل رابط البث"; return@OutlinedButton }
                    val parts = clearKey.split(":").map { it.trim() }
                    val (kid, key) = if (parts.size == 2) parts[0] to parts[1] else null to null
                    val needShaka = !key.isNullOrBlank() || url.endsWith(".mpd", true)
                    onDirectPlay(StreamConfig(
                        url = url.trim(),
                        title = "تشغيل يدوي",
                        playerType = if (needShaka) "shaka_web" else "native",
                        drmScheme = if (!key.isNullOrBlank()) "clearkey" else null,
                        drmKeyId = kid,
                        drmKey = key,
                        drmLicenseUrl = null,
                        userAgent = userAgent.takeIf { it.isNotBlank() },
                        referer = referer.takeIf { it.isNotBlank() },
                        customHeaders = null,
                    ))
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Gold),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تشغيل البث", color = Gold, fontWeight = FontWeight.Bold)
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFFF5252), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun GateField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            cursorBrush = SolidColor(Gold),
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}