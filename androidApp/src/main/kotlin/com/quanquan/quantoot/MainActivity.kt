package com.quanquan.quantoot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.quanquan.quantoot.platform.AndroidPlatformServices

class MainActivity : ComponentActivity() {
    private lateinit var platform: AndroidPlatformServices

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        platform = AndroidPlatformServices(this)
        platform.handleRedirect(intent?.dataString)
        setContent { QuanTootApp(platform) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        platform.handleRedirect(intent.dataString)
    }

    override fun onStart() {
        super.onStart()
        if (::platform.isInitialized) platform.foreground.value = true
    }

    override fun onStop() {
        if (::platform.isInitialized) platform.foreground.value = false
        super.onStop()
    }
}
