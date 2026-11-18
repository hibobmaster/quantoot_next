@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.quanquan.quantoot.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import com.quanquan.quantoot.data.safeWebUrl
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import kotlinx.io.buffered
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
                val ext = address.pathExtension.orEmpty().lowercase()
                check(ext != "m3u8")
                val suffix = ext.takeIf { it.length in 1..8 && it.all(Char::isLetterOrDigit) } ?: "mp4"
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

private suspend fun downloadForPlayback(url: String, file: Path) = withContext(Dispatchers.Default) {
    HttpClient(Darwin).use { client ->
        client.prepareGet(url).execute { response ->
            check(response.status.isSuccess())
            val channel = response.bodyAsChannel()
            SystemFileSystem.sink(file).buffered().use { output ->
                val bytes = ByteArray(64 * 1024)
                while (true) {
                    val count = channel.readAvailable(bytes)
                    if (count < 0) break
                    output.write(bytes, 0, count)
                }
            }
        }
    }
}
