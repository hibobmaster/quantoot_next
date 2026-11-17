package com.quanquan.quantoot.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.quanquan.quantoot.model.MediaAttachment
import com.quanquan.quantoot.platform.NativeVideoPlayer
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class GallerySaveTarget(val url: String, val name: String)

internal sealed interface GallerySaveResult {
    data object Idle : GallerySaveResult
    data class Saving(val target: GallerySaveTarget) : GallerySaveResult
    data class Saved(val target: GallerySaveTarget) : GallerySaveResult
    data class Failed(val target: GallerySaveTarget) : GallerySaveResult
}

internal class GallerySaveState {
    var result by mutableStateOf<GallerySaveResult>(GallerySaveResult.Idle)
        private set
    val saving get() = result is GallerySaveResult.Saving

    suspend fun save(target: GallerySaveTarget, write: suspend (GallerySaveTarget) -> Unit) {
        if (saving) return
        result = GallerySaveResult.Saving(target)
        try {
            write(target)
            result = GallerySaveResult.Saved(target)
        } catch (e: CancellationException) {
            result = GallerySaveResult.Idle
            throw e
        } catch (_: Exception) {
            result = GallerySaveResult.Failed(target)
        }
    }

    fun dismissFeedback() {
        if (!saving) result = GallerySaveResult.Idle
    }
}

internal fun boundedGalleryOffset(
    position: Offset,
    zoom: Float,
    viewport: IntSize,
    image: IntSize,
): Offset {
    if (zoom <= 1f || viewport.width <= 0 || viewport.height <= 0) return Offset.Zero
    val validImage = image.width > 0 && image.height > 0
    val fit =
        if (validImage)
            min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
        else 1f
    val width = if (validImage) image.width * fit else viewport.width.toFloat()
    val height = if (validImage) image.height * fit else viewport.height.toFloat()
    val maxX = max(0f, (width * zoom - viewport.width) / 2f)
    val maxY = max(0f, (height * zoom - viewport.height) / 2f)
    return Offset(position.x.coerceIn(-maxX, maxX), position.y.coerceIn(-maxY, maxY))
}

private class GalleryImageState(media: MediaAttachment) {
    val gesture = GalleryGestureState()
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    var viewport by mutableStateOf(IntSize.Zero)
        private set
    var image by mutableStateOf(
        IntSize(media.meta?.original?.width ?: 0, media.meta?.original?.height ?: 0)
    )
        private set

    fun resize(viewport: IntSize) {
        this.viewport = viewport
        offset = boundedGalleryOffset(offset, scale, viewport, image)
    }

    fun loaded(image: IntSize) {
        this.image = image
        offset = boundedGalleryOffset(offset, scale, viewport, image)
    }

    fun reset() {
        gesture.cancel()
        scale = 1f
        offset = Offset.Zero
    }

    fun zoomTo(value: Float, focal: Offset = Offset(viewport.width / 2f, viewport.height / 2f)) {
        gesture.cancel()
        transform(focal, Offset.Zero, value.coerceIn(1f, 6f) / scale)
    }

    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val next = (scale * zoom).coerceIn(1f, 6f)
        val focal = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
        offset = boundedGalleryOffset(
            (offset - focal) * (next / scale) + focal + pan, next, viewport, image
        )
        scale = next
    }
}

@Composable
fun MediaGallery(
    ui: UiContext,
    attachments: List<MediaAttachment>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    if (attachments.isEmpty()) return
    val items = remember(attachments, initialIndex) { MediaGalleryItems(attachments, initialIndex) }
    val pager = rememberPagerState(initialPage = items.initialIndex) { items.size }
    val images = remember(items) { items.attachments.map(::GalleryImageState) }
    val media = items[pager.currentPage]
    val image = images[pager.currentPage]
    val url = mediaOriginalUrl(media)
    val scope = rememberCoroutineScope()
    val strings = LocalStrings.current
    var showAlt by remember(pager.currentPage) { mutableStateOf(false) }
    val saveState = remember(items) { GallerySaveState() }
    fun save(target: GallerySaveTarget) {
        scope.launch {
            saveState.save(target) { ui.platform.saveMedia(it.url, it.name) }
        }
    }
    LaunchedEffect(pager.currentPage) { images.forEach { it.reset() } }
    val whiteControls = IconButtonDefaults.iconButtonColors(
        contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.3f)
    )
    val whiteText = ButtonDefaults.textButtonColors(
        contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.3f)
    )
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDismiss, colors = whiteControls) {
                    Icon(Icons.Default.Close, tr("mastodon_close"))
                }
                Text(
                    tr("mastodon_media_position", "index" to "${pager.currentPage + 1}", "total" to "${items.size}"),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { url?.let(ui.platform::share) },
                    enabled = url != null,
                    colors = whiteControls,
                ) { Icon(Icons.Default.Share, tr("mastodon_button_share")) }
                TextButton(
                    onClick = {
                        if (url != null) {
                            save(GallerySaveTarget(url, "quantoot-${media.id}"))
                        }
                    },
                    enabled = media.type == "image" && url != null && !saveState.saving,
                    colors = whiteText,
                ) {
                    if (saveState.saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(tr("mastodon_save_to_gallery"))
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                userScrollEnabled = image.scale <= 1f,
                key = { it },
            ) { index ->
                GalleryPage(ui, items[index], images[index], index == pager.currentPage) {
                    if (index == pager.currentPage && images[index].scale <= 1f) onDismiss()
                }
            }
            val feedback = saveState.result
            if (feedback is GallerySaveResult.Saved || feedback is GallerySaveResult.Failed) {
                Snackbar(
                    modifier = Modifier.padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    action = {
                        if (feedback is GallerySaveResult.Failed) TextButton(onClick = { save(feedback.target) }) {
                            Text(tr("mastodon_retry"), color = MaterialTheme.colorScheme.inversePrimary)
                        }
                    },
                    dismissAction = {
                        IconButton(onClick = saveState::dismissFeedback) {
                            Icon(Icons.Default.Close, tr("mastodon_close"))
                        }
                    },
                ) {
                    Text(tr(if (feedback is GallerySaveResult.Saved) "mastodon_image_saved" else "mastodon_image_save_failed"))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { scope.launch { pager.animateScrollToPage(items.adjacent(pager.currentPage, -1)) } },
                    enabled = pager.currentPage > 0,
                    colors = whiteControls,
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, tr("mastodon_previous_media")) }
                if (media.type == "image") {
                    TextButton(
                        onClick = { image.zoomTo(image.scale / 1.5f) },
                        enabled = image.scale > 1f,
                        colors = whiteText,
                        modifier = Modifier.width(40.dp).semantics { contentDescription = strings.text("mastodon_zoom_out") },
                    ) { Text("−") }
                    TextButton(
                        onClick = image::reset,
                        colors = whiteText,
                        modifier = Modifier.width(64.dp).semantics { contentDescription = strings.text("mastodon_reset_zoom") },
                    ) { Text("${(image.scale * 100).toInt()}%") }
                    TextButton(
                        onClick = { image.zoomTo(image.scale * 1.5f) },
                        enabled = image.scale < 6f,
                        colors = whiteText,
                        modifier = Modifier.width(40.dp).semantics { contentDescription = strings.text("mastodon_zoom_in") },
                    ) { Text("+") }
                } else Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { scope.launch { pager.animateScrollToPage(items.adjacent(pager.currentPage, 1)) } },
                    enabled = pager.currentPage < items.size - 1,
                    colors = whiteControls,
                ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr("mastodon_next_media")) }
                if (!media.description.isNullOrBlank()) {
                    TextButton(onClick = { showAlt = !showAlt }, colors = whiteText, modifier = Modifier.width(48.dp)) { Text("ALT") }
                }
            }
            if (showAlt) {
                Text(
                    media.description.orEmpty(),
                    Modifier.fillMaxWidth().heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()).padding(16.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun GalleryPage(
    ui: UiContext,
    media: MediaAttachment,
    image: GalleryImageState,
    active: Boolean,
    onDismiss: () -> Unit,
) {
    val url = mediaOriginalUrl(media)
    Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        when {
            media.type == "image" && url != null -> GalleryImage(media, url, image, active, onDismiss)
            media.type in listOf("video", "gifv", "audio") && url != null -> {
                if (active) NativeVideoPlayer(
                    url,
                    if (media.type == "audio") Modifier.fillMaxWidth().height(120.dp) else Modifier.fillMaxSize(),
                )
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("mastodon_media_unavailable"), color = Color.White)
                TextButton(onClick = { ui.open(url) }, enabled = url != null) {
                    Text(tr("mastodon_open_in_browser"), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun GalleryImage(
    media: MediaAttachment,
    url: String,
    image: GalleryImageState,
    active: Boolean,
    onDismiss: () -> Unit,
) {
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    val dismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val gesture = image.gesture
    Box(
        Modifier.fillMaxSize().onSizeChanged(image::resize)
            .pointerInput(image, active) {
                if (!active) return@pointerInput
                var lastTapUp: Long? = null
                fun settle(cancelled: Boolean = false) {
                    val animation = gesture.release(size.height.toFloat(), 160.dp.toPx(), cancelled) ?: return
                    scope.launch {
                        animate(animation.from, animation.target, animationSpec = tween(180)) { value, _ ->
                            gesture.animate(animation, value)
                        }
                        if (gesture.complete(animation)) dismiss()
                    }
                }
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val generation = gesture.begin()
                        val doubleTap = lastTapUp?.let {
                            down.uptimeMillis - it in viewConfiguration.doubleTapMinTimeMillis..viewConfiguration.doubleTapTimeoutMillis
                        } == true
                        lastTapUp = null
                        gesture.move(Offset.Zero, currentEvent.changes.count { it.pressed }, image.scale > 1f, viewConfiguration.touchSlop)
                        do {
                            val event = awaitPointerEvent()
                            if (!gesture.isCurrent(generation)) return@awaitEachGesture
                            val owner = gesture.move(
                                event.calculatePan(), event.changes.count { it.pressed }, image.scale > 1f,
                                viewConfiguration.touchSlop,
                            )
                            if (event.changes.any { it.isConsumed } &&
                                owner != GalleryGestureOwner.Pager && owner != GalleryGestureOwner.Transform) {
                                settle(cancelled = true)
                                return@awaitEachGesture
                            }
                            when (owner) {
                                GalleryGestureOwner.Transform -> {
                                    if (event.changes.any { it.pressed && it.previousPressed }) {
                                        image.transform(event.calculateCentroid(), event.calculatePan(), event.calculateZoom())
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                                GalleryGestureOwner.Dismiss -> event.changes.forEach { it.consume() }
                                else -> Unit
                            }
                            if (event.changes.none { it.pressed }) {
                                val up = event.changes.firstOrNull { it.id == down.id }
                                if (owner == GalleryGestureOwner.Undecided && up != null &&
                                    up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                                    up.consume()
                                    if (doubleTap) {
                                        if (image.scale > 1f) image.reset() else image.zoomTo(3f, up.position)
                                    } else lastTapUp = up.uptimeMillis
                                }
                                settle()
                            } else if (owner == GalleryGestureOwner.Undecided &&
                                awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) {
                                settle(cancelled = true)
                                return@awaitEachGesture
                            }
                        } while (event.changes.any { it.pressed })
                    }
                } finally {
                    gesture.cancel()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            url,
            media.description?.takeIf { it.isNotBlank() } ?: mediaTypeLabel(media.type),
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = image.scale
                scaleY = image.scale
                translationX = image.offset.x
                translationY = image.offset.y + gesture.dismissOffset
            },
            contentScale = ContentScale.Fit,
            onLoading = {
                loading = true
                failed = false
            },
            onSuccess = {
                loading = false
                failed = false
                image.loaded(IntSize(it.result.image.width, it.result.image.height))
            },
            onError = {
                loading = false
                failed = true
            },
        )
        if (loading) CircularProgressIndicator(color = Color.White)
        if (failed) Text(tr("mastodon_media_unavailable"), color = Color.White)
    }
}
