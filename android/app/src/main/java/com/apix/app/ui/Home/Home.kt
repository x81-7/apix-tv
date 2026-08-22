package com.apix.app.ui.Home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val HomeGold = Color(0xFFFFC94A)
private val HomeGoldDark = Color(0xFF6E4D13)

@Composable
fun HomeRoot(onOpenLive: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(HomePage.Home) }
    val backToHome: () -> Unit = { page = HomePage.Home }

    BackHandler(enabled = page != HomePage.Home) {
        backToHome()
    }

    AnimatedContent(
        targetState = page,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "home-pages"
    ) { target ->
        when (target) {
            HomePage.Home -> HomeScreen(
                onLive = onOpenLive,
                onMovies = {
                    runCatching {
                        context.startActivity(
                            android.content.Intent(
                                context,
                                com.lagradost.cloudstream3.apix.ApixLauncherActivity::class.java
                            )
                        )
                    }
                },
                onSettings = { page = HomePage.Settings }
            )

            HomePage.Movies -> HomeScreen(
                onLive = onOpenLive,
                onMovies = { page = HomePage.Home },
                onSettings = { page = HomePage.Settings }
            )

            HomePage.Settings -> SettingsScreen(
                onBack = backToHome
            )
        }
    }
}

private enum class HomePage {
    Home,
    Movies,
    Settings
}

@Composable
private fun HomeScreen(
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSettings: () -> Unit
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    val liveFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        liveFocus.requestFocus()

        while (true) {
            now = Date()
            delay(1000)
        }
    }

    val dateText = remember(now) {
        SimpleDateFormat(
            "dd/MM/yyyy",
            Locale.getDefault()
        ).format(now)
    }

    val timeText = remember(now) {
        SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        ).format(now)
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF050505),
                            Color(0xFF0A0805),
                            Color(0xFF020202)
                        )
                    )
                )
                .padding(
                    horizontal = 14.dp,
                    vertical = 12.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            val panelWidth = maxWidth.coerceAtMost(1280.dp)

            Column(
                modifier = Modifier
                    .widthIn(max = panelWidth)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF15100B),
                                Color(0xFF050505)
                            ),
                            radius = 1100f
                        )
                    )
                    .border(
                        1.dp,
                        Color(0xFF2A2114),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(
                        horizontal = 38.dp,
                        vertical = 22.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        date = dateText,
                        time = timeText
                    )
                }

                Spacer(
                    Modifier.weight(0.08f)
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "APiX",
                        color = Color.White,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-2).sp
                    )

                    Text(
                        text = "كل ما تحب .. في مكان واحد",
                        color = Color(0xFFF2D9A1),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(
                    Modifier.weight(0.06f)
                )

                CompositionLocalProvider(
                    LocalLayoutDirection provides LayoutDirection.Ltr
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.73f),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HomeTile(
                            title = "بث مباشر",
                            subtitle = "جميع القنوات المباشرة وأقوى البطولات",
                            icon = Icons.Filled.LiveTv,
                            imageRes = com.apix.app.R.drawable.home_live_thumb,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(liveFocus),
                            onClick = onLive
                        )

                        HomeTile(
                            title = "أفلام",
                            subtitle = "أحدث الأفلام العالمية والمسلسلات",
                            icon = Icons.Filled.Movie,
                            imageRes = com.apix.app.R.drawable.home_movies_thumb,
                            modifier = Modifier.weight(1f),
                            onClick = onMovies
                        )

                        HomeTile(
                            title = "إعدادات",
                            subtitle = "إعدادات التطبيق والحساب والمزيد",
                            icon = Icons.Filled.Settings,
                            imageRes = com.apix.app.R.drawable.home_settings_thumb,
                            modifier = Modifier.weight(1f),
                            onClick = onSettings
                        )
                    }
                }

                Spacer(
                    Modifier.weight(0.04f)
                )

                Text(
                    text = "APiX TV",
                    color = Color(0xFF7C6B50),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun StatusPill(
    date: String,
    time: String
) {
    Row(
        modifier = Modifier
            .clip(
                RoundedCornerShape(18.dp)
            )
            .background(
                Color(0x660F0F0F)
            )
            .border(
                BorderStroke(
                    1.dp,
                    Color(0x332A2114)
                ),
                RoundedCornerShape(18.dp)
            )
            .padding(
                horizontal = 18.dp,
                vertical = 8.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "▣  $date",
            color = Color(0xFFE6D6AD),
            fontSize = 14.sp
        )

        Text(
            text = "|",
            color = Color(0xFF756144),
            fontSize = 14.sp
        )

        Text(
            text = "◷  $time",
            color = Color(0xFFE6D6AD),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun HomeTile(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    imageRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource =
        remember {
            MutableInteractionSource()
        }

    val focused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(26.dp)
            )
            .shadow(
                if (focused) 18.dp else 8.dp,
                RoundedCornerShape(26.dp)
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF14100B),
                        Color(0xFF090909)
                    )
                )
            )
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = if (focused) {
                    HomeGold
                } else {
                    HomeGoldDark
                },
                shape = RoundedCornerShape(26.dp)
            )
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    (
                        event.key == Key.Enter ||
                        event.key == Key.DirectionCenter
                    )
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .focusable(
                interactionSource = interactionSource
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(
                    RoundedCornerShape(20.dp)
                )
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color(0x22000000)
                    )
            )

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HomeGold,
                modifier = Modifier.size(70.dp)
            )
        }

        Spacer(
            Modifier.size(10.dp)
        )

        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = subtitle,
                color = Color(0xFFB6B0A5),
                fontSize = 11.sp,
                modifier = Modifier.padding(
                    horizontal = 6.dp
                ),
                maxLines = 2
            )
        }

        Spacer(
            Modifier.size(12.dp)
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(
                    RoundedCornerShape(50)
                )
                .background(
                    Color(0x14000000)
                )
                .border(
                    1.dp,
                    HomeGold,
                    RoundedCornerShape(50)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "→",
                color = HomeGold,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
