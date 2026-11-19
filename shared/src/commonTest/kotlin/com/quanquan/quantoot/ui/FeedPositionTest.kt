package com.quanquan.quantoot.ui

import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.KeyValueStore
import kotlin.test.*
import kotlinx.coroutines.test.*

class FeedPositionTest {
    private class Preferences : KeyValueStore {
        var value: String? = null
        override suspend fun read(key: String) = value
        override suspend fun write(key: String, value: String?) { this.value = value }
    }

    @Test
    fun headerAndStatusPositionsRoundTripWithoutSharingPixelOffsets() = runTest {
        val cache = FeedCache(Preferences(), "profile", Status.serializer())
        val rows = listOf(Status(id = "first"), Status(id = "second"))
        val base = FeedSnapshot(rows, nextCursor = "opaque-tail", continuationKnown = true)
        cache.write(base.atPosition(feedPosition(0, 340, hasLeading = true)))
        val header = cache.read()!!
        assertEquals(0, header.index)
        assertEquals(0, header.offset)
        assertEquals(FeedPosition.Leading(340), header.restoredPosition(rows, { it.id }, hasLeading = true))
        assertEquals("opaque-tail", header.nextCursor)
        assertTrue(header.continuationKnown)

        cache.write(header.atPosition(feedPosition(2, 83, hasLeading = true)))
        val status = cache.read()!!
        assertNull(status.leadingOffset)
        assertEquals(1, status.index)
        assertEquals(83, status.offset)
        val restored = status.restoredPosition(rows, { it.id }, hasLeading = true)
        assertEquals(2, restored.listIndex(hasLeading = true))
        assertEquals(83, restored.offset)
    }

    @Test
    fun emptyProfileRetainsHeaderAndClampsAfterHeaderHeightChanges() = runTest {
        val cache = FeedCache(Preferences(), "empty-profile", Status.serializer())
        cache.write(FeedSnapshot<Status>().atPosition(FeedPosition.Leading(900)))
        val restored = cache.read()!!.restoredPosition(emptyList(), { it.id }, hasLeading = true)
        assertEquals(FeedPosition.Leading(900), restored)
        val header = assertIs<FeedPosition.Leading>(restored)
        assertEquals(399, header.measuredOffset(400))
        assertEquals(900, header.measuredOffset(1200))
        assertEquals(0, header.measuredOffset(0))
    }

    @Test
    fun legacySnapshotStillRestoresToItsStatusAfterTheHeader() = runTest {
        val preferences = Preferences().apply {
            value = """{"items":[{"id":"first"},{"id":"second"}],"index":1,"offset":72}"""
        }
        val saved = FeedCache(preferences, "legacy", Status.serializer()).read()!!
        val position = saved.restoredPosition(saved.items, { it.id }, hasLeading = true)
        assertEquals(FeedPosition.Status(1, 72), position)
        assertEquals(2, position.listIndex(hasLeading = true))
        assertEquals(1, position.listIndex(hasLeading = false))
    }

    @Test
    fun invalidHeaderOffsetsAreRejectedAtTheCacheBoundary() = runTest {
        val preferences = Preferences().apply { value = """{"leadingOffset":-1}""" }
        assertNull(FeedCache(preferences, "invalid", Status.serializer()).read())
    }

    @Test
    fun restoredAnchorFollowsIdentityThroughInsertionsAndProjection() = runTest {
        val projection = StatusProjection()
        val rows = listOf(Status(id = "before"), Status(id = "anchor"), Status(id = "after"))
        val saved = FeedSnapshot(rows, index = 1, offset = 95)
        val pager = FeedPager(backgroundScope, { status: Status -> status.id }, projection::project) { Page(emptyList()) }
        pager.created(Status(id = "arrival"), defer = false)
        pager.restore(saved)
        assertEquals(FeedPosition.Status(2, 95), saved.restoredPosition(pager.rows, { it.id }, true))

        projection.apply(StreamEvent.Deleted(StreamSource.User, "anchor"))
        pager.prune()
        assertEquals(FeedPosition.Status(2, 0), saved.restoredPosition(pager.rows, { it.id }, true))
        projection.apply(StreamEvent.Deleted(StreamSource.User, "after"))
        pager.prune()
        assertEquals(FeedPosition.Status(1, 0), saved.restoredPosition(pager.rows, { it.id }, true))
        projection.apply(StreamEvent.Deleted(StreamSource.User, "before"))
        pager.prune()
        assertEquals(FeedPosition.Leading(0), saved.restoredPosition(pager.rows, { it.id }, true))
        assertEquals(FeedPosition.Status(0, 0), saved.restoredPosition(pager.rows, { it.id }, false))
    }

    @Test
    fun boundingPreservesHeaderMetadataAndResetsTruncatedStatusPixels() = runTest {
        val cache = FeedCache(Preferences(), "bounded", Status.serializer())
        val rows = (0..119).map { Status(id = "$it", content = "x".repeat(10_000)) }
        val original = FeedSnapshot(rows, index = 110, offset = 94, nextCursor = "tail", continuationKnown = true)
        cache.write(original)
        val truncated = cache.read()!!
        assertTrue(truncated.items.size < 100)
        assertEquals(truncated.items.lastIndex, truncated.index)
        assertEquals(0, truncated.offset)
        assertFalse(truncated.continuationKnown)

        cache.write(original.atPosition(FeedPosition.Leading(390)))
        val header = cache.read()!!
        assertTrue(header.items.size < 100)
        assertEquals(390, header.leadingOffset)
        assertEquals(0, header.index)
        assertEquals(0, header.offset)
    }

    @Test
    fun firstStatusBoundaryDeterminesDeferredArrivalsAndRevealTarget() = runTest {
        val pager = FeedPager(backgroundScope, { status: Status -> status.id }) { Page(emptyList()) }
        pager.restore(FeedSnapshot(listOf(Status(id = "old"))))
        val header = feedPosition(0, 700, hasLeading = true)
        assertFalse(header.readingOlder())
        pager.created(Status(id = "visible-arrival"), defer = header.readingOlder())
        assertEquals("visible-arrival", pager.rows.first().id)
        assertFalse(feedPosition(1, 160, true).readingOlder())
        val scrolled = feedPosition(1, 161, true)
        assertTrue(scrolled.readingOlder())
        assertTrue(feedPosition(2, 0, true).readingOlder())
        pager.created(Status(id = "pending"), defer = scrolled.readingOlder())
        assertEquals("visible-arrival", pager.rows.first().id)
        pager.reveal()
        assertEquals("pending", pager.rows.first().id)
        assertEquals(1, FeedPosition.Status(0, 0).listIndex(hasLeading = true))
        assertEquals(0, FeedPosition.Status(0, 0).listIndex(hasLeading = false))
        assertEquals(FeedPosition.Status(0, 161), feedPosition(0, 161, hasLeading = false))
    }

    @Test
    fun paginationNeedsAVisibleStatusNearTheEndOrTheFooter() {
        assertFalse(feedNeedsMore(listOf(0), rowCount = 20, hasLeading = true))
        assertFalse(feedNeedsMore(listOf(0), rowCount = 0, hasLeading = true))
        assertFalse(feedNeedsMore(listOf(1, 2, 3), rowCount = 20, hasLeading = true))
        assertTrue(feedNeedsMore(listOf(17, 18), rowCount = 20, hasLeading = true))
        assertTrue(feedNeedsMore(listOf(1), rowCount = 0, hasLeading = true))
        assertTrue(feedNeedsMore(listOf(3), rowCount = 2, hasLeading = true))
        assertTrue(feedNeedsMore(listOf(16, 17), rowCount = 20, hasLeading = false))
        assertTrue(feedNeedsMore(listOf(0), rowCount = 0, hasLeading = false))
        assertFalse(feedNeedsMore(emptyList(), rowCount = 0, hasLeading = false))
    }

    @Test
    fun homeChoicesRetainSourcesAndGuestDefault() {
        assertEquals(HomeTimeline.Local, HomeTimeline.initial(signedIn = false))
        assertEquals(HomeTimeline.Home, HomeTimeline.initial(signedIn = true))
        assertEquals(Timeline.Home, HomeTimeline.Home.source)
        assertEquals(Timeline.Public(local = true), HomeTimeline.Local.source)
        assertEquals(Timeline.Public(local = false), HomeTimeline.Federated.source)
    }
}
