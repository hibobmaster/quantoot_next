package com.quanquan.quantoot.ui

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
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
import kotlinx.coroutines.launch

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
        scale = 1f
        offset = Offset.Zero
    }

    fun zoomTo(value: Float, focal: Offset = Offset(viewport.width / 2f, viewport.height / 2f)) {
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
    var saving by remember { mutableStateOf(false) }
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
                            saving = true
                            ui.run {
                                try {
                                    ui.platform.saveMedia(url, "quantoot-${media.id}")
                                    ui.message = strings.text("mastodon_image_saved")
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    enabled = media.type == "image" && url != null && !saving,
                    colors = whiteText,
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(tr("mastodon_save_to_gallery"))
                }
            }
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                userScrollEnabled = image.scale <= 1f,
                key = { it },
            ) { index ->
                GalleryPage(ui, items[index], images[index], index == pager.currentPage)
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
private fun GalleryPage(ui: UiContext, media: MediaAttachment, image: GalleryImageState, active: Boolean) {
    val url = mediaOriginalUrl(media)
    Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        when {
            media.type == "image" && url != null -> GalleryImage(media, url, image)
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
private fun GalleryImage(media: MediaAttachment, url: String, image: GalleryImageState) {
    var loading by remember(url) { mutableStateOf(true) }
    var failed by remember(url) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().onSizeChanged(image::resize)
            .pointerInput(image) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.pressed && it.previousPressed } &&
                            (event.changes.count { it.pressed } > 1 || image.scale > 1f)) {
                            image.transform(event.calculateCentroid(), event.calculatePan(), event.calculateZoom())
                            event.changes.filter { it.positionChanged() }.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(image) {
                detectTapGestures(onDoubleTap = { focal ->
                    if (image.scale > 1f) image.reset() else image.zoomTo(3f, focal)
                })
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
                translationY = image.offset.y
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
