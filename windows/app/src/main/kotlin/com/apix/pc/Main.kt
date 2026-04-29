package com.apix.pc

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.apix.pc.security.HardwareId
import com.apix.pc.ui.AppRoot
import com.apix.pc.ui.theme.ApixTheme
import com.apix.pc.util.DeepLinkArgs

/**
 * Entry point for APiX TV — Windows Desktop.
 * Mirrors the Android ComposeActivity boot flow but runs on Compose for Desktop (JVM).
 */
fun main(args: Array<String>) {
    // Initialise hardware-bound device id (anti-tamper) early — same role as MediaDrm UUID on Android.
    HardwareId.ensureInitialized()

    // Parse incoming deep-link args: apix://<payload>  or  https://apix-panal.vercel.app/watch.html?id=<payload>
    val deepLink = DeepLinkArgs.parse(args)

    application {
        val windowState = rememberWindowState()
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "APiX TV"
        ) {
            ApixTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(initialDeepLink = deepLink)
                }
            }
        }
    }
}