package com.apix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
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
import com.apix.app.ui.theme.Gold

@Composable
fun FullScreenLoader(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // APiX Logo
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) {
                        append("AP")
                    }
                    withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.ExtraBold)) {
                        append("iX")
                    }
                },
                fontSize = 48.sp
            )
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(
                color = Gold,
                trackColor = Color(0xFF333333),
                strokeWidth = 4.dp,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun ApixLogo(fontSize: Int = 28, modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.ExtraBold)) {
                append("AP")
            }
            withStyle(SpanStyle(color = Gold, fontWeight = FontWeight.ExtraBold)) {
                append("iX")
            }
        },
        fontSize = fontSize.sp,
        modifier = modifier
    )
}
