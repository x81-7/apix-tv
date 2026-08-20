package com.apix.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.apix.app.ui.Home.HomeRoot
import com.apix.app.ui.theme.APiXTheme

class HomeComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            APiXTheme(darkTheme = true) {
                HomeRoot(
                    onOpenLive = {
                        startActivity(Intent(this, ComposeActivity::class.java))
                    }
                )
            }
        }
    }
}
