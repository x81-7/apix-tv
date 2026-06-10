package com.apix.app

import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import com.apix.app.data.*
import com.apix.app.ui.screens.*
import com.apix.app.viewmodel.*

class ComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseRepository.init(application)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            val isDarkMode = remember { mutableStateOf(true) }
            val isInPlayer = remember { mutableStateOf(false) }

            // حقن الإضافات
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val secureStore = com.apix.app.vod.plugin.SecureRepositoryStore(applicationContext)
                    val urls = listOf(BuildConfig.PLUGIN_REPO_URL_1, BuildConfig.PLUGIN_REPO_URL_2)
                    urls.forEachIndexed { i, url ->
                        if (url.isNotEmpty() && !secureStore.getRepositories().any { it.manifestUrl == url }) {
                            secureStore.addRepository("سيرفر ${i + 1}", url)
                        }
                    }
                }
            }

            APiXTheme(darkTheme = isDarkMode.value) {
                AppNavigation(
                    isDarkMode = isDarkMode.value,
                    onToggleDarkMode = { isDarkMode.value = it },
                    onPlayerStateChanged = { isInPlayer.value = it }
                )
            }
        }
    }
}

@Composable
fun AppNavigation(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    onPlayerStateChanged: (Boolean) -> Unit,
    initialStreamConfigJson: String? = null
) {
    val viewModel: MainViewModel = viewModel()
    val cinemaViewModel: CinemaViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val cinemaState by cinemaViewModel.homeState.collectAsState()
    val cinemaLoading by cinemaViewModel.isLoading.collectAsState()

    // تحميل بيانات السينما
    LaunchedEffect(uiState.appMode) {
        cinemaViewModel.loadCinemaData(uiState.appMode, uiState.externalSourceUrl)
    }

    // هنا يتم استدعاء واجهة السينما مع تمرير كل المتغيرات التي يتوقعها مشروعك
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cinemaLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFFFD700))
        } else {
            CinemaShell(
                data = cinemaState,
                isLoading = false,
                onItemClick = { /* استدعاء منطق الـ VodPlayerBridge الذي يربط بين المشغل والإضافات */ },
                onLiveChannelClick = { channel -> /* ربط مع MainViewModel */ }
            )
        }
    }
}
