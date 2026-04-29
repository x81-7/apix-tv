package com.apix.pc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import com.apix.pc.ui.theme.Gold

/**
 * PIN unlock screen — mirrors Android's `PinLockScreen` and iOS's `PinPromptView`.
 * Used for both side-menu PINs (`side_menus.pin_code`) and per-channel PINs
 * (`channels.pin_code` / `sub_channels.pin_code`).
 */
@Composable
fun PinLockScreen(
    title: String,
    expectedPin: String,
    onUnlocked: () -> Unit,
    onCancel: () -> Unit,
) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 360.dp).padding(24.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Gold,
                modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, color = Color.White, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("هذا القسم محمي برمز PIN", color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                if (entered.isEmpty()) {
                    Text("أدخل رمز PIN", color = Color.White.copy(alpha = 0.35f), fontSize = 14.sp)
                }
                BasicTextField(
                    value = entered,
                    onValueChange = { v ->
                        entered = v.filter { it.isDigit() }.take(8)
                        error = null
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    cursorBrush = SolidColor(Gold),
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp,
                        textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = Color(0xFFFF5252), fontSize = 13.sp)
            }

            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(46.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                ) { Text("إلغاء", color = Color.White) }
                Button(
                    onClick = {
                        if (entered.trim() == expectedPin.trim()) onUnlocked()
                        else error = "رمز غير صحيح"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f).height(46.dp),
                ) { Text("فتح", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
