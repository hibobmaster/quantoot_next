package com.quanquan.quantoot.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.quanquan.quantoot.model.*
import kotlin.test.*
import kotlinx.serialization.json.Json

class MediaBehaviorTest {
    @Test
    fun singlePreviewUsesOriginalDimensionsAndBoundsExtremeShapes() {
        fun media(width: Int, height: Int) = MediaAttachment(
            meta = MediaMetadata(original = MediaDimensions(width, height))
        )
        assertEquals(1.5f, mediaPreviewAspectRatio(media(1200, 800)))
        assertEquals(0.75f, mediaPreviewAspectRatio(media(100, 2000)))
        assertEquals(1.91f, mediaPreviewAspectRatio(media(2000, 100)))
        val invalidOriginal = MediaAttachment(
            meta = MediaMetadata(MediaDimensions(100, 0), MediaDimensions(800, 800))
        )
        assertEquals(1f, mediaPreviewAspectRatio(invalidOriginal))
        assertEquals(4f / 3f, mediaPreviewAspectRatio(media(-1, 500)))
        assertEquals(4f / 3f, mediaPreviewAspectRatio(MediaAttachment()))
    }

    @Test
    fun galleryKeepsUnsupportedAttachmentsAndItsOpeningSnapshot() {
        val raw = mutableListOf(
            MediaAttachment(id = "first", url = "https://media.example/first.jpg"),
            MediaAttachment(id = "unknown", type = "unknown", url = "https://media.example/file"),
            MediaAttachment(id = "last", url = "https://media.example/last.jpg"),
        )
        val gallery = MediaGalleryItems(raw, 1)
        raw.clear()
        assertEquals(3, gallery.size)
        assertEquals("unknown", gallery[gallery.initialIndex].id)
        assertEquals("first", gallery[gallery.adjacent(gallery.initialIndex, -1)].id)
        assertEquals("last", gallery[gallery.adjacent(gallery.initialIndex, 1)].id)
        assertEquals(0, gallery.adjacent(0, -1))
        assertEquals(2, gallery.adjacent(2, 1))
        assertEquals(2, MediaGalleryItems(gallery.attachments, 99).initialIndex)
        assertEquals(0, MediaGalleryItems(gallery.attachments, -1).initialIndex)
    }

    @Test
    fun originalAndThumbnailUrlsRejectExecutableSchemesAndCredentials() {
        val media = MediaAttachment(
            url = "javascript:alert(1)",
            previewUrl = "https://user:password@media.example/preview.jpg",
            remoteUrl = "https://remote.example/original.jpg",
        )
        assertEquals("https://remote.example/original.jpg", mediaOriginalUrl(media))
        assertEquals("https://remote.example/original.jpg", mediaPreviewUrl(media))
        assertNull(mediaOriginalUrl(media.copy(remoteUrl = null)))
        val thumbnailOnly = MediaAttachment(previewUrl = "https://media.example/thumbnail.jpg")
        assertNull(mediaOriginalUrl(thumbnailOnly))
        assertEquals("https://media.example/thumbnail.jpg", mediaPreviewUrl(thumbnailOnly))
    }

    @Test
    fun letterboxedImagesCannotPanIntoBlankSpace() {
        val viewport = IntSize(400, 400)
        val landscape = IntSize(800, 400)
        assertEquals(Offset.Zero, boundedGalleryOffset(Offset(500f, 500f), 1f, viewport, landscape))
        assertEquals(Offset(200f, 0f), boundedGalleryOffset(Offset(500f, 500f), 2f, viewport, landscape))
        assertEquals(Offset(-400f, -100f), boundedGalleryOffset(Offset(-500f, -500f), 3f, viewport, landscape))
        assertEquals(Offset(0f, 200f), boundedGalleryOffset(Offset(500f, 500f), 2f, viewport, IntSize(400, 800)))
        assertEquals(Offset.Zero, boundedGalleryOffset(Offset(500f, 500f), 2f, IntSize.Zero, landscape))
    }

    @Test
    fun optionalApiMetadataDecodesWithoutChangingExistingDefaults() {
        val json = Json { ignoreUnknownKeys = true }
        val status = json.decodeFromString<Status>(
            """{"id":"s","media_attachments":[{"id":"m","meta":{"original":{"width":1600,"height":900},"small":{"width":400,"height":225}}}],"tags":[{"name":"tea","url":"https://social.example/tags/tea"}],"card":{"type":"link","provider_name":"Provider","provider_url":"https://provider.example","width":1200,"height":630,"image_description":"A preview","blurhash":"hash"}}"""
        )
        assertEquals(MediaDimensions(1600, 900), status.mediaAttachments.single().meta?.original)
        assertEquals(MediaDimensions(400, 225), status.mediaAttachments.single().meta?.small)
        assertEquals("tea", status.tags.single().name)
        assertEquals("Provider", status.card?.providerName)
        assertEquals("A preview", status.card?.imageDescription)
        assertEquals("link", status.card?.type)
        val oldStatus = json.decodeFromString<Status>("""{"id":"old","media_attachments":[{"id":"m"}]}""")
        assertNull(oldStatus.mediaAttachments.single().meta)
        assertEquals(emptyList(), oldStatus.tags)
    }
}
