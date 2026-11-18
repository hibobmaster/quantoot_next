package com.quanquan.quantoot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.safeWebUrl
import com.quanquan.quantoot.model.MediaAttachment
import com.quanquan.quantoot.model.MediaDimensions

internal fun mediaOriginalUrl(media: MediaAttachment): String? =
    safeWebUrl(media.url.orEmpty()) ?: safeWebUrl(media.remoteUrl.orEmpty())

internal fun mediaPreviewUrl(media: MediaAttachment): String? =
    safeWebUrl(media.previewUrl.orEmpty()) ?: mediaOriginalUrl(media)

internal fun mediaPreviewAspectRatio(media: MediaAttachment): Float =
    (media.meta?.original?.aspectRatio() ?: media.meta?.small?.aspectRatio() ?: (4f / 3f))
        .coerceIn(0.75f, 1.91f)

private fun MediaDimensions.aspectRatio(): Float? {
    val width = width ?: return null
    val height = height ?: return null
    return if (width > 0 && height > 0) width.toFloat() / height else null
}

internal class MediaGalleryItems(attachments: List<MediaAttachment>, initialIndex: Int) {
    val attachments = attachments.toList()
    val size: Int get() = attachments.size
    val initialIndex: Int

    init {
        require(attachments.isNotEmpty())
        this.initialIndex = initialIndex.coerceIn(0, attachments.lastIndex)
    }

    operator fun get(index: Int): MediaAttachment = attachments[index]

    fun adjacent(index: Int, direction: Int): Int = (index + direction).coerceIn(0, size - 1)
}

private data class GalleryRequest(val attachments: List<MediaAttachment>, val index: Int)

@Composable
fun MediaAttachments(ui: UiContext, attachments: List<MediaAttachment>, sensitive: Boolean) {
    if (attachments.isEmpty()) return
    var revealed by remember(attachments.map { it.id }, sensitive) { mutableStateOf(!sensitive) }
    var gallery by remember { mutableStateOf<GalleryRequest?>(null) }
    val spacing = 6.dp
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))) {
        val width = maxWidth
        val columns = if (width >= 600.dp && attachments.size > 4) 3 else 2
        val cellHeight = ((width - spacing * (columns - 1)) / columns).coerceAtMost(240.dp)
        val open: (Int) -> Unit = { index ->
            gallery = GalleryRequest(attachments.toList(), index)
        }
        when (attachments.size) {
            1 -> MediaPreview(
                attachments.first(),
                revealed,
                Modifier.fillMaxWidth().height(
                    (width / mediaPreviewAspectRatio(attachments.first())).coerceIn(160.dp, 420.dp)
                ),
                onOpen = { open(0) },
            )
            3 -> Row(
                Modifier.fillMaxWidth().height((width * 0.75f).coerceAtMost(420.dp)),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                MediaPreview(
                    attachments[0], revealed, Modifier.weight(1f).fillMaxHeight(),
                    onOpen = { open(0) },
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing)) {
                    for (index in 1..2) {
                        MediaPreview(
                            attachments[index], revealed, Modifier.weight(1f).fillMaxWidth(),
                            onOpen = { open(index) },
                        )
                    }
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                attachments.indices.chunked(columns).forEach { indices ->
                    Row(
                        Modifier.fillMaxWidth().height(cellHeight),
                        horizontalArrangement = Arrangement.spacedBy(spacing),
                    ) {
                        indices.forEach { index ->
                            MediaPreview(
                                attachments[index], revealed, Modifier.weight(1f).fillMaxHeight(),
                                onOpen = { open(index) },
                            )
                        }
                        repeat(columns - indices.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (!revealed) {
            Column(
                Modifier.matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(role = Role.Button) { revealed = true }
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Text(tr("mastodon_sensitive_content"), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { revealed = true }) { Text(tr("mastodon_show")) }
            }
        } else if (sensitive) {
            FilledTonalButton(
                onClick = { revealed = false },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) { Text(tr("mastodon_hide")) }
        }
    }
    gallery?.let { request ->
        MediaGallery(ui, request.attachments, request.index) { gallery = null }
    }
}

@Composable
private fun MediaPreview(
    media: MediaAttachment,
    revealed: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
) {
    val preview = mediaPreviewUrl(media)
    var loading by remember(preview) { mutableStateOf(preview != null) }
    var failed by remember(preview) { mutableStateOf(preview == null) }
    Box(
        modifier.testTag("media-preview-${media.id}")
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = revealed, role = Role.Button, onClick = onOpen),
        contentAlignment = Alignment.Center,
    ) {
        if (revealed) {
            AsyncImage(
                preview,
                media.description?.takeIf { it.isNotBlank() } ?: mediaTypeLabel(media.type),
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onLoading = {
                    loading = true
                    failed = false
                },
                onSuccess = {
                    loading = false
                    failed = false
                },
                onError = {
                    loading = false
                    failed = true
                },
            )
            if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            if (failed) {
                Text(
                    mediaTypeLabel(media.type),
                    Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (media.type == "video" || media.type == "gifv") {
                Surface(color = Color.Black.copy(alpha = 0.65f), shape = RoundedCornerShape(24.dp)) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = mediaTypeLabel(media.type),
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp).size(32.dp),
                    )
                }
            }
            if (media.type != "image") {
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        mediaTypeLabel(media.type),
                        Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
internal fun mediaTypeLabel(type: String): String = tr(
    when (type) {
        "image" -> "mastodon_media_type_image"
        "video" -> "mastodon_media_type_video"
        "gifv" -> "mastodon_media_type_gif"
        else -> "mastodon_media_type_unknown"
    }
)
