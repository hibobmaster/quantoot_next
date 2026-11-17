package com.quanquan.quantoot

import androidx.compose.ui.window.ComposeUIViewController
import com.quanquan.quantoot.platform.IosPlatformServices

fun MainViewController(platform: IosPlatformServices) = ComposeUIViewController {
    QuanTootApp(platform)
}
