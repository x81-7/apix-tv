package com.apix.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * PIN unlock screen for protected side menus.
 * Two buttons: cancel (back) and unlock. Remote/D-Pad friendly.
 */
@Composable
fun PinLockScreen(
    menuName: String,
    expectedPin: String,
    onCancel: () -> Unit,
    onUnlocked: () -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0B0B0F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.widthIn(max = 380.dp).padding(24.dp)
        ) {
            Icon(Icons.Default.Lock, null, tint = Color(0xFFFFD700), modifier = Modifier.size(48.dp))
            Text("القسم محمي", color = Color.White, fontSize = 22.sp)
            Text(menuName, color = Color(0xFFBBBBBB), fontSize = 14.sp)

            OutlinedTextField(
                value = entered,
                onValueChange = { v ->
                    entered = v.filter { it.isDigit() }.take(8)
                    error = null
                },
                label = { Text("أدخل رمز الفتح") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error != null,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                shape = RoundedCornerShape(12.dp)
            )
            if (error != null) Text(error!!, color = Color(0xFFFF6B6B), fontSize = 13.sp)

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("إلغاء") }

                Button(
                    onClick = {
                        if (entered == expectedPin) onUnlocked()
                        else error = "الرمز غير صحيح"
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700), contentColor = Color.Black)
                ) { Text("فتح") }
            }
        }
    }
}
