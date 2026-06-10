package com.apix.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apix.app.data.*
import com.apix.app.ui.screens.*
import com.apix.app.ui.theme.APiXTheme
import com.apix.app.viewmodel.MainViewModel
import com.apix.app.viewmodel.CinemaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SupabaseRepository.init(application)
        WindowCompat.setDecorFitsSystemWindows(window, true)

        setContent {
            APiXTheme(darkTheme = true) {
                AppNavigation(initialStreamConfigJson = intent.getStringExtra("streamConfig"))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

@Composable
fun AppNavigation(initialStreamConfigJson: String? = null) {
    val viewModel: MainViewModel = viewModel()
    val cinemaViewModel: CinemaViewModel = viewModel()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val cinemaState by cinemaViewModel.homeState.collectAsState()
    val cinemaLoading by cinemaViewModel.isLoading.collectAsState()

    // حقن الإضافات
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val secureStore = com.apix.app.vod.plugin.SecureRepositoryStore(context)
            val repos = secureStore.getRepositories()
            val urls = listOf(BuildConfig.PLUGIN_REPO_URL_1, BuildConfig.PLUGIN_REPO_URL_2)
            urls.forEach { url ->
                if (url.isNotEmpty() && !repos.any { it.manifestUrl == url }) {
                    secureStore.addRepository("سيرفر ${url.hashCode()}", url)
                }
            }
        }
        cinemaViewModel.loadCinemaData(uiState.appMode, uiState.externalSourceUrl)
    }

    var currentScreen by remember { mutableStateOf<com.apix.app.Screen>(com.apix.app.Screen.Main) }
    
    // واجهة السينما
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cinemaLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            CinemaShell(
                data = cinemaState,
                isLoading = false,
                onItemClick = { /* استدعاء منطق التشغيل */ },
                onLiveChannelClick = { /* استدعاء منطق القنوات */ }
            )
        }
    }
}
