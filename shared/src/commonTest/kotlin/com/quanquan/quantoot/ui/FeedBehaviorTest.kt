package com.quanquan.quantoot.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.KeyValueStore
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FeedBehaviorTest {
    @Test
    fun refreshIndicatorOnlyAppearsWhenUpdatingExistingRows() = runTest {
        val pager = FeedPager(backgroundScope, { status: Status -> status.id }) {
            Page(listOf(Status(id = "a")), "next")
        }
        pager.restore(null)
        pager.load()
        assertTrue(pager.busy)
        assertFalse(pager.refreshing)
        runCurrent()
        pager.load()
        assertTrue(pager.refreshing)
        runCurrent()
        pager.load(more = true)
        assertTrue(pager.paging)
        assertFalse(pager.refreshing)
        runCurrent()
        assertFalse(pager.busy)
    }

    @Test
    fun interactionsGroupByTypeAndStatusWithUniqueAuthors() {
        val status = Status(id = "post")
        fun notification(id: String, actor: String, type: String = "favourite") =
            Notification(id, type, "2026-11-17T00:00:00Z", Account(id = actor), status)
        val raw =
            listOf(
                notification("90000000000000000001", "a"),
                notification("90000000000000000003", "b"),
                notification("90000000000000000002", "a"),
                notification("90000000000000000004", "a", "reblog"),
            )
        val groups = groupFeedNotifications(raw)
        assertEquals(listOf("reblog", "favourite"), groups.map { it.first().type })
        assertEquals(listOf("b", "a"), groups[1].map { it.account.id })
        assertEquals(4, raw.size)
    }

    @Test
    fun groupedDisplayDoesNotChangeRawPagingCursor() = runTest {
        var requested: String? = null
        val pager =
            FeedPager(backgroundScope, { n: Notification -> n.id }) { maxId ->
                requested = maxId
                Page(emptyList())
            }
        pager.restore(
            FeedSnapshot(
                listOf(Notification(id = "30"), Notification(id = "20"), Notification(id = "10")),
                nextCursor = "10",
                continuationKnown = true,
            )
        )
        pager.created(Notification(id = "40"), defer = false)
        pager.load(more = true)
        runCurrent()
        assertEquals("10", requested)
        assertTrue(pager.exhausted)
    }

    @Test
    fun refreshDefersArrivalsAndKeepsReadPositionRows() = runTest {
        val pager =
            FeedPager(backgroundScope, { s: Status -> s.id }) {
                Page(listOf(Status(id = "3"), Status(id = "2", content = "updated")), "next")
            }
        pager.restore(
            FeedSnapshot(
                listOf(Status(id = "2"), Status(id = "1")),
                nextCursor = "tail",
                continuationKnown = true,
            )
        )
        pager.load(defer = true)
        runCurrent()
        assertEquals(listOf("2", "1"), pager.rows.map { it.id })
        assertEquals("updated", pager.rows.first().content)
        assertEquals(listOf("3"), pager.pending.map { it.id })
        pager.reveal()
        assertEquals(listOf("3", "2", "1"), pager.rows.map { it.id })
    }

    @Test
    fun returningToTopRevealsPendingPostsEvenWhileARefreshIsInFlight() = runTest {
        val response = CompletableDeferred<Page<Status>>()
        val atTop = mutableStateOf(false)
        var requested = 0
        var topRequests = 0
        val pager = FeedPager(backgroundScope, { status: Status -> status.id },
            beforeReplace = { topRequests++ },
        ) {
            requested++
            response.await()
        }
        pager.restore(FeedSnapshot(listOf(Status(id = "2"), Status(id = "1")), nextCursor = "tail", continuationKnown = true))
        backgroundScope.launch { pager.revealWhenAtTop { atTop.value } }
        runCurrent()

        pager.load(defer = true)
        pager.created(Status(id = "3"), defer = true)
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(listOf("2", "1"), pager.rows.map { it.id })
        assertEquals(listOf("3"), pager.pending.map { it.id })

        Snapshot.withMutableSnapshot { atTop.value = true }
        runCurrent()
        assertTrue(pager.busy)
        assertEquals(listOf("3", "2", "1"), pager.rows.map { it.id })
        assertTrue(pager.pending.isEmpty())
        assertEquals(1, topRequests)

        response.complete(Page(listOf(Status(id = "4"), Status(id = "3"), Status(id = "2")), "head"))
        runCurrent()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(listOf("4", "3", "2", "1"), pager.rows.map { it.id })
        assertTrue(pager.pending.isEmpty())
        assertEquals("tail", pager.snapshot().nextCursor)
        assertEquals(2, topRequests)
        assertEquals(1, requested)
    }

    @Test
    fun streamArrivalSurvivesRefreshResponse() = runTest {
        val response = CompletableDeferred<Page<Status>>()
        val pager = FeedPager(backgroundScope, { s: Status -> s.id }) { response.await() }
        pager.restore(FeedSnapshot(listOf(Status(id = "1"))))
        pager.load()
        runCurrent()
        pager.created(Status(id = "3"), defer = false)
        response.complete(Page(listOf(Status(id = "2"), Status(id = "1")), "older"))
        runCurrent()
        assertEquals(listOf("3", "2", "1"), pager.rows.map { it.id })
    }

    @Test
    fun existingArrivalDuringRefreshDoesNotMoveItsRow() = runTest {
        val response = CompletableDeferred<Page<Status>>()
        val pager = FeedPager(backgroundScope, { s: Status -> s.id }) { response.await() }
        pager.restore(FeedSnapshot(listOf(Status(id = "2"), Status(id = "1"))))
        pager.load()
        runCurrent()
        pager.created(Status(id = "1", content = "fresh"), defer = false)
        response.complete(Page(listOf(Status(id = "2"), Status(id = "1"))))
        runCurrent()
        assertEquals(listOf("2", "1"), pager.rows.map { it.id })
    }

    @Test
    fun deferredRefreshKeepsOpaqueTailAfterThreePages() = runTest {
        val requested = mutableListOf<String?>()
        var fresh = false
        val pager =
            FeedPager(backgroundScope, { s: Status -> s.id }) { cursor ->
                requested += cursor
                when (cursor) {
                    null ->
                        if (fresh)
                            Page(listOf(Status(id = "new"), Status(id = "a")), "refresh-tail")
                        else Page(listOf(Status(id = "a")), "opaque-1")
                    "opaque-1" -> Page(listOf(Status(id = "b")), "opaque-2")
                    "opaque-2" -> Page(listOf(Status(id = "c")), "opaque-3")
                    "opaque-3" -> Page(listOf(Status(id = "d")))
                    else -> error("Unexpected cursor $cursor")
                }
            }
        pager.restore()
        pager.load()
        runCurrent()
        repeat(2) {
            pager.load(more = true)
            runCurrent()
        }
        fresh = true
        pager.load(defer = true)
        runCurrent()
        pager.reveal()
        pager.load(more = true)
        runCurrent()
        assertEquals(listOf(null, "opaque-1", "opaque-2", null, "opaque-3"), requested)
        assertEquals(listOf("new", "a", "b", "c", "d"), pager.rows.map { it.id })
        assertTrue(pager.exhausted)
    }

    @Test
    fun diskRestoreResumesExactOpaqueCursorAndLegacyRowsRequireReset() = runTest {
        var persisted: String? = null
        val preferences =
            object : KeyValueStore {
                override suspend fun read(key: String) = persisted

                override suspend fun write(key: String, value: String?) {
                    persisted = value
                }
            }
        val cache = FeedCache(preferences, "paging", Status.serializer())
        val original =
            FeedPager(backgroundScope, { s: Status -> s.id }) {
                Page(listOf(Status(id = "public-id")), "opaque + &")
            }
        original.restore()
        original.load()
        runCurrent()
        cache.write(original.snapshot())
        val requests = mutableListOf<String?>()
        val restored =
            FeedPager(backgroundScope, { s: Status -> s.id }) { cursor ->
                requests += cursor
                Page(listOf(Status(id = "older")))
            }
        restored.restore(cache.read())
        restored.load(more = true)
        runCurrent()
        assertEquals(listOf<String?>("opaque + &"), requests)
        assertEquals(listOf("public-id", "older"), restored.rows.map { it.id })
        persisted = """{"items":[{"id":"legacy-public-id"}],"index":0,"offset":0}"""
        restored.restore(cache.read())
        restored.load(more = true)
        runCurrent()
        assertEquals(1, requests.size)
        restored.load(defer = true)
        runCurrent()
        assertEquals(listOf("opaque + &", null), requests)
        assertEquals(listOf("older"), restored.rows.map { it.id })
    }

    @Test
    fun cacheIsBoundedAndCorruptDataFallsBack() = runTest {
        var persisted: String? = null
        val preferences =
            object : KeyValueStore {
                override suspend fun read(key: String): String? = persisted

                override suspend fun write(key: String, value: String?) {
                    persisted = value
                }
            }
        val cache = FeedCache(preferences, "test", Status.serializer())
        cache.write(
            FeedSnapshot(
                (1..120).map { Status(id = "$it", content = "x".repeat(10000)) },
                110,
                20,
                "exact-tail",
                true,
            )
        )
        assertTrue(persisted!!.encodeToByteArray().size <= 512 * 1024)
        val restored = cache.read()!!
        assertTrue(restored.items.size <= 100)
        assertTrue(restored.index < restored.items.size)
        assertEquals(0, restored.offset)
        assertEquals("exact-tail", restored.nextCursor)
        assertFalse(restored.continuationKnown)
        persisted = "{invalid"
        assertNull(cache.read())
    }
    @Test
    fun projectedEditAndDeletionWinOverRefreshPageAndCache() = runTest {
        val statuses = StatusProjection()
        val original = Status(id = "original", content = "old")
        val edited = original.copy(content = "new", editedAt = "2026-11-17T00:01:00Z")
        val boost = Status(id = "boost", reblog = original)
        val response = CompletableDeferred<Page<Status>>()
        val pager = FeedPager(backgroundScope, { s: Status -> s.id }, statuses::project) {
            statuses.read({ response.await() }) { it.items }
        }
        pager.restore(FeedSnapshot(listOf(original), nextCursor = "opaque", continuationKnown = true))
        pager.load(more = true)
        runCurrent()
        statuses.apply(StreamEvent.Edited(StreamSource.User, edited))
        pager.prune()
        assertEquals("new", pager.rows.single().content)
        statuses.apply(StreamEvent.Deleted(StreamSource.Local, original.id))
        pager.prune()
        response.complete(Page(listOf(original, boost), "next-opaque"))
        runCurrent()
        assertTrue(pager.rows.isEmpty())
        assertEquals("next-opaque", pager.snapshot().nextCursor)
        pager.restore(FeedSnapshot(listOf(original, boost)))
        assertTrue(pager.rows.isEmpty())
        assertTrue(pager.snapshot().items.isEmpty())
    }

    @Test
    fun pendingEditAndDeleteDoNotInsertOrChangeTheReadingOrder() = runTest {
        val statuses = StatusProjection()
        val pager = FeedPager(backgroundScope, { s: Status -> s.id }, statuses::project) { Page(emptyList()) }
        pager.restore(FeedSnapshot(listOf(Status(id = "known"))))
        val created = Status(id = "new")
        statuses.apply(StreamEvent.Created(StreamSource.User, created))
        pager.created(created, defer = true)
        statuses.apply(StreamEvent.Edited(StreamSource.User, created.copy(content = "edited")))
        statuses.apply(StreamEvent.Edited(StreamSource.User, Status(id = "absent")))
        pager.prune()
        assertEquals(listOf("known"), pager.rows.map { it.id })
        assertEquals("edited", pager.pending.single().content)
        statuses.apply(StreamEvent.Deleted(StreamSource.User, "new"))
        pager.prune()
        pager.reveal()
        assertEquals(listOf("known"), pager.rows.map { it.id })
        assertTrue(pager.pending.isEmpty())
    }

    @Test
    fun arrivalsSurviveCacheRestoreAndSupersededRequestsExactlyOnce() = runTest {
        val first = CompletableDeferred<Page<Status>>()
        var requests = 0
        val pager = FeedPager(backgroundScope, { s: Status -> s.id }) {
            if (++requests == 1) withContext(NonCancellable) { first.await() }
            else Page(listOf(Status(id = "fresh"), Status(id = "old")), "fresh-cursor")
        }
        pager.created(Status(id = "arrival"), defer = false)
        pager.restore(FeedSnapshot(listOf(Status(id = "old"))))
        assertEquals(listOf("arrival", "old"), pager.rows.map { it.id })
        pager.load()
        runCurrent()
        pager.load()
        runCurrent()
        first.complete(Page(listOf(Status(id = "stale")), "stale-cursor"))
        runCurrent()
        assertEquals(listOf("arrival", "fresh", "old"), pager.rows.map { it.id })
        assertEquals("fresh-cursor", pager.snapshot().nextCursor)
        pager.created(Status(id = "arrival"), defer = false)
        assertEquals(1, pager.rows.count { it.id == "arrival" })
    }

    @Test
    fun restoreInvalidatesInFlightRequestsAndEmptyProjectedPageCanContinue() = runTest {
        val response = CompletableDeferred<Page<Status>>()
        val requests = mutableListOf<String?>()
        val pager = FeedPager(backgroundScope, { s: Status -> s.id }, { it.takeUnless { it.id == "deleted" } }) { cursor ->
            requests += cursor
            if (requests.size == 1) withContext(NonCancellable) { response.await() }
            else Page(listOf(Status(id = "visible")))
        }
        pager.restore()
        pager.load()
        runCurrent()
        pager.restore(FeedSnapshot(listOf(Status(id = "deleted")), nextCursor = "next", continuationKnown = true))
        response.complete(Page(listOf(Status(id = "wrong"))))
        runCurrent()
        assertTrue(pager.rows.isEmpty())
        pager.load(more = true)
        runCurrent()
        assertEquals(listOf(null, "next"), requests)
        assertEquals(listOf("visible"), pager.rows.map { it.id })
    }

    @Test
    fun sourcesCannotInsertIntoOtherFeedsAndLocalWritesReconcileMembership() = runTest {
        var calls = 0
        val pager = FeedPager(backgroundScope, { s: Status -> s.id }) {
            calls++
            Page(listOf(Status(id = "server")), "next")
        }
        pager.restore()
        val local = StreamEvent.Created(StreamSource.Local, Status(id = "local"))
        pager.apply(local, StreamSource.User, defer = false, active = true)
        assertTrue(pager.rows.isEmpty())
        pager.apply(local, StreamSource.Local, defer = false, active = true)
        assertEquals(listOf("local"), pager.rows.map { it.id })
        pager.apply(StreamEvent.Created(StreamSource.LocalWrite, Status(id = "published")), StreamSource.Local, false, true)
        runCurrent()
        assertFalse(pager.rows.any { it.id == "published" })
        assertTrue(pager.rows.any { it.id == "server" })
        assertEquals(1, calls)
        pager.apply(StreamEvent.Reconnected(StreamSource.User), StreamSource.Local, true, true)
        runCurrent()
        assertEquals(1, calls)
        pager.apply(StreamEvent.Reconnected(StreamSource.Local), StreamSource.Local, true, true)
        runCurrent()
        assertEquals(2, calls)
        pager.apply(StreamEvent.Reconnected(StreamSource.Local), StreamSource.Local, true, false)
        runCurrent()
        assertEquals(2, calls)
    }

}
