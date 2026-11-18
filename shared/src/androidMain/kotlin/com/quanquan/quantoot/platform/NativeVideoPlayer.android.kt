package com.quanquan.quantoot.platform

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.quanquan.quantoot.data.safeWebUrl

@Composable
actual fun NativeVideoPlayer(url: String, modifier: Modifier, onError: () -> Unit) {
    val safe = safeWebUrl(url) ?: return
    key(safe) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    setOnErrorListener { _, _, _ -> onError(); true }
                    setVideoURI(Uri.parse(safe))
                    start()
                }
            },
            modifier = modifier,
            onRelease = { it.stopPlayback() },
        )
    }
}
