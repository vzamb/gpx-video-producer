package com.gpxvideo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.gpxvideo.core.ui.theme.GpxVideoTheme
import com.gpxvideo.lib.strava.StravaAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var stravaAuth: StravaAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleStravaCallback(intent)
        setContent {
            GpxVideoTheme {
                AppNavigation()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStravaCallback(intent)
    }

    private fun handleStravaCallback(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "gpxvideo" && uri.host == "strava" && uri.path == "/callback") {
            val code = uri.getQueryParameter("code") ?: return
            lifecycleScope.launch {
                stravaAuth.exchangeCode(code)
            }
        }
    }
}
