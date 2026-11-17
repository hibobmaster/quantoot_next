@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.quanquan.quantoot.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import com.quanquan.quantoot.data.safeWebUrl
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

@Composable
actual fun NativeVideoPlayer(url: String, modifier: Modifier) {
    val safe = safeWebUrl(url) ?: return
    val player = remember(safe) { AVPlayer(uRL = NSURL.URLWithString(safe)!!) }
    DisposableEffect(player) { onDispose { player.pause() } }
    UIKitViewController(
        factory = {
            AVPlayerViewController().apply {
                this.player = player
                showsPlaybackControls = true
            }
        },
        modifier = modifier,
    )
}
