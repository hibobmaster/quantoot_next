package com.quanquan.quantoot.platform

import android.content.Context
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.quanquan.quantoot.data.safeWebUrl
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.io.files.Path

@Composable
actual fun NativeVideoPlayer(url: String, modifier: Modifier, onError: () -> Unit) {
    val safe = safeWebUrl(url) ?: return
    val cacheDir = LocalContext.current.cacheDir
    val failed by rememberUpdatedState(onError)
    var resume by remember(safe) { mutableStateOf<PlaybackPosition?>(null) }
    var localFile by remember(safe) { mutableStateOf<File?>(null) }
    val fileExtension = remember(safe) { playbackFileExtension(safe) }

    fun recover(position: PlaybackPosition) {
        if (fileExtension == null) failed()
        else if (resume == null) resume = position
    }

    LaunchedEffect(safe, resume) {
        if (resume == null) return@LaunchedEffect
        var temporary: File? = null
        try {
            val file = File.createTempFile("quantoot-playback-", ".$fileExtension", cacheDir)
            temporary = file
            downloadForPlayback(safe, Path(file.absolutePath))
            localFile = file
            awaitCancellation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed()
        } finally {
            temporary?.delete()
        }
    }

    val local = localFile
    if (resume != null && local == null) {
        Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    key(safe, local) {
        AndroidView(
            factory = { context ->
                PlaybackVideoView(context, ::recover.takeIf { local == null && fileExtension != null }).apply {
                    setMediaController(MediaController(context).also { it.setAnchorView(this) })
                    setOnErrorListener { _, _, _ ->
                        if (local != null) failed()
                        else recover(playbackPosition)
                        true
                    }
                    setVideoURI(local?.let(Uri::fromFile) ?: Uri.parse(safe))
                    val position = resume
                    if (position != null) seekTo(position.millis)
                    if (position?.playing != false) start()
                }
            },
            modifier = modifier,
            onRelease = { it.release() },
        )
    }
}

private data class PlaybackPosition(val millis: Int, val playing: Boolean)

private class PlaybackVideoView(
    context: Context,
    private val onStalledSeek: ((PlaybackPosition) -> Unit)?,
) : VideoView(context) {
    private var seekMillis = 0
    private var playWhenReady = false
    val playbackPosition: PlaybackPosition get() = PlaybackPosition(seekMillis, playWhenReady)
    private val seekTimeout = Runnable { onStalledSeek?.invoke(playbackPosition) }

    init {
        setOnPreparedListener { media ->
            media.setOnSeekCompleteListener { removeCallbacks(seekTimeout) }
        }
    }

    override fun seekTo(msec: Int) {
        seekMillis = msec
        removeCallbacks(seekTimeout)
        val videoDuration = duration
        if (onStalledSeek != null && videoDuration > 0 && msec > videoDuration.toLong() * bufferPercentage / 100) {
            // The platform can retry a rejected range for a long time without notifying the view.
            postDelayed(seekTimeout, 5_000)
        }
        super.seekTo(msec)
    }

    fun release() {
        removeCallbacks(seekTimeout)
        stopPlayback()
    }

    override fun start() {
        playWhenReady = true
        super.start()
    }

    override fun pause() {
        playWhenReady = false
        super.pause()
    }
}
