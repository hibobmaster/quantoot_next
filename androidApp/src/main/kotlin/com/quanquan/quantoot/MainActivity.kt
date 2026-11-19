package com.quanquan.quantoot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.quanquan.quantoot.platform.AndroidPlatformServices
import com.quanquan.quantoot.ui.AdaptiveWindow
import com.quanquan.quantoot.ui.WindowRegion
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    private lateinit var platform: AndroidPlatformServices

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        platform = AndroidPlatformServices(this)
        platform.handleRedirect(intent?.dataString)
        setContent {
            val layouts = remember {
                WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this).map { layout ->
                    AdaptiveWindow(layout.displayFeatures.filterIsInstance<FoldingFeature>()
                        .filter { it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL }
                        .map { feature ->
                            val bounds = feature.bounds
                            WindowRegion(bounds.left.toFloat(), bounds.top.toFloat(),
                                bounds.right.toFloat(), bounds.bottom.toFloat(), separates = true)
                        })
                }
            }
            val window by layouts.collectAsState(AdaptiveWindow())
            QuanTootApp(platform, window)
        }
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
