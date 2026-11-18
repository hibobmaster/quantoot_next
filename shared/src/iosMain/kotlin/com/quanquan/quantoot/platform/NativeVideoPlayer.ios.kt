@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.quanquan.quantoot.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import com.quanquan.quantoot.data.safeWebUrl
import kotlinx.coroutines.*
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.AVFoundation.*
import platform.AVKit.AVPlayerViewController
import platform.Foundation.*

@Composable
actual fun NativeVideoPlayer(url: String, modifier: Modifier, onError: () -> Unit) {
    val address = remember(url) { safeWebUrl(url)?.let { NSURL.URLWithString(it) } } ?: return
    var player by remember(address) { mutableStateOf<AVPlayer?>(null) }
    val failed by rememberUpdatedState(onError)
    LaunchedEffect(address) {
        var temporary: Path? = null
        try {
            player = AVPlayer(uRL = address)
            if (!playWhenReady(requireNotNull(player))) {
                player?.pause()
                player = null
                // Some media hosts return HTTP 200 to AVFoundation's byte-range requests.
                val suffix = checkNotNull(playbackFileExtension(url))
                val file = Path(NSTemporaryDirectory(), "quantoot-${NSUUID().UUIDString}.$suffix")
                temporary = file
                downloadForPlayback(url, file)
                player = AVPlayer(uRL = NSURL.fileURLWithPath(file.toString()))
                check(playWhenReady(requireNotNull(player)))
            }
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed()
        } finally {
            player?.pause()
            player?.replaceCurrentItemWithPlayerItem(null)
            player = null
            temporary?.let { SystemFileSystem.delete(it, mustExist = false) }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (player == null) CircularProgressIndicator()
        else UIKitViewController(
            factory = { AVPlayerViewController().apply { showsPlaybackControls = true } },
            update = { controller -> if (controller.player != player) controller.player = player },
            modifier = Modifier.matchParentSize(),
        )
    }
}

private suspend fun playWhenReady(player: AVPlayer): Boolean {
    val item = player.currentItem ?: return false
    player.play()
    while (item.status == AVPlayerItemStatusUnknown) delay(150)
    return item.status == AVPlayerItemStatusReadyToPlay
}
