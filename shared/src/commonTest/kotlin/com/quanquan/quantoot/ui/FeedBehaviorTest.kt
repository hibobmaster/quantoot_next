package com.quanquan.quantoot.ui

import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.KeyValueStore
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FeedBehaviorTest {
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
        pager.receive(Notification(id = "40"), defer = false)
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
    fun streamArrivalSurvivesRefreshResponse() = runTest {
        val response = CompletableDeferred<Page<Status>>()
        val pager = FeedPager(backgroundScope, { s: Status -> s.id }) { response.await() }
        pager.restore(FeedSnapshot(listOf(Status(id = "1"))))
        pager.load()
        runCurrent()
        pager.receive(Status(id = "3"), defer = false)
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
        pager.receive(Status(id = "1", content = "fresh"), defer = false)
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
        assertEquals("exact-tail", restored.nextCursor)
        assertFalse(restored.continuationKnown)
        persisted = "{invalid"
        assertNull(cache.read())
    }
}
