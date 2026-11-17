package com.quanquan.quantoot.ui

import com.quanquan.quantoot.model.Page
import com.quanquan.quantoot.model.Status
import com.quanquan.quantoot.model.StreamEvent
import com.quanquan.quantoot.model.StreamSource
import com.quanquan.quantoot.platform.KeyValueStore
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Encoder

@OptIn(ExperimentalCoroutinesApi::class)
class FeedCacheSessionTest {
    private class Preferences : KeyValueStore {
        val values = mutableMapOf<String, String?>()
        val reads = mutableListOf<String>()
        val writes = mutableListOf<Pair<String, String?>>()
        var beforeRead: suspend (String) -> Unit = {}
        var beforeWrite: suspend (String) -> Unit = {}
        var writing = 0
        var maximumWriters = 0

        override suspend fun read(key: String): String? {
            reads += key
            val saved = values[key]
            beforeRead(key)
            return saved
        }

        override suspend fun write(key: String, value: String?) {
            writes += key to value
            writing++
            maximumWriters = maxOf(maximumWriters, writing)
            try {
                beforeWrite(key)
                values[key] = value
            } finally {
                writing--
            }
        }
    }

    private fun snapshot(id: String, offset: Int = 0) = FeedSnapshot(
        listOf(Status(id = id)),
        offset = offset,
        nextCursor = "tail:$id",
        continuationKnown = true,
    )

    @Test
    fun rapidSourceReturnUsesFinalPositionAndCursorBeforeDiskDebounce() = runTest {
        val preferences = Preferences()
        val session = FeedCacheSession(preferences, Status.serializer(), backgroundScope)
        val a = session.attach("a")
        a.checkpoint(snapshot("a", 12))
        a.close(snapshot("a", 87))
        val b = session.attach("b")
        b.checkpoint(snapshot("b", 53))
        b.close()

        val returned = session.attach("a")
        assertEquals(snapshot("a", 87), returned.read())
        assertTrue(preferences.reads.isEmpty())
        assertTrue(preferences.writes.isEmpty())
        returned.close(snapshot("a", 99))
        runCurrent()
        advanceTimeBy(399)
        runCurrent()
        assertTrue(preferences.writes.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(snapshot("a", 99), FeedCache(preferences, "a", Status.serializer()).read())
        assertEquals(snapshot("b", 53), FeedCache(preferences, "b", Status.serializer()).read())
        session.close()
    }

    @Test
    fun newerMemoryWinsWhenAnOlderDiskReadCompletesLate() = runTest {
        val preferences = Preferences()
        FeedCache(preferences, "a", Status.serializer()).write(snapshot("disk", 11))
        val readGate = CompletableDeferred<Unit>()
        preferences.beforeRead = { readGate.await() }
        val session = FeedCacheSession(preferences, Status.serializer(), backgroundScope)
        val attachment = session.attach("a")
        val read = async { attachment.read() }
        runCurrent()
        attachment.checkpoint(snapshot("memory", 92))
        readGate.complete(Unit)

        assertEquals(snapshot("memory", 92), read.await())
        assertEquals(snapshot("memory", 92), attachment.read())
        attachment.close()
        session.close()
    }

    @Test
    fun supersededReadAndCloseCannotPublishThroughANewerAttachment() = runTest {
        val preferences = Preferences()
        FeedCache(preferences, "a", Status.serializer()).write(snapshot("disk"))
        val readGate = CompletableDeferred<Unit>()
        preferences.beforeRead = { readGate.await() }
        val session = FeedCacheSession(preferences, Status.serializer(), backgroundScope)
        val outgoing = session.attach("a")
        val read = async { outgoing.read() }
        runCurrent()
        val incoming = session.attach("a")
        incoming.checkpoint(snapshot("current", 71))
        outgoing.close(snapshot("stale", 500))
        outgoing.checkpoint(snapshot("even later", 600))
        outgoing.close(snapshot("again", 700))
        readGate.complete(Unit)

        assertNull(read.await())
        assertEquals(snapshot("current", 71), incoming.read())
        incoming.close()
        session.close()
    }

    @Test
    fun writesStayOrderedAcrossDetachAndReattachWhileStorageIsBlocked() = runTest {
        val preferences = Preferences()
        val writeGate = CompletableDeferred<Unit>()
        preferences.beforeWrite = { if (preferences.writes.size == 1) writeGate.await() }
        val session = FeedCacheSession(preferences, Status.serializer(), backgroundScope)
        val first = session.attach("a")
        first.checkpoint(snapshot("old", 10))
        runCurrent()
        advanceTimeBy(400)
        runCurrent()
        assertEquals(1, preferences.writes.size)
        first.close(snapshot("departing", 20))
        val second = session.attach("a")
        assertEquals(snapshot("departing", 20), second.read())
        second.close(snapshot("newest", 30))
        advanceTimeBy(800)
        runCurrent()
        assertEquals(1, preferences.writes.size)

        writeGate.complete(Unit)
        runCurrent()
        advanceTimeBy(400)
        runCurrent()
        assertEquals(2, preferences.writes.size)
        assertEquals(1, preferences.maximumWriters)
        assertEquals(snapshot("newest", 30), FeedCache(preferences, "a", Status.serializer()).read())
        session.close()
    }

    @Test
    fun checkpointRestartsTheDiskDebounceFromTheLatestChange() = runTest {
        val preferences = Preferences()
        val session = FeedCacheSession(preferences, Status.serializer(), backgroundScope)
        val attachment = session.attach("a")
        attachment.checkpoint(snapshot("a", 10))
        runCurrent()
        advanceTimeBy(350)
        attachment.checkpoint(snapshot("a", 20))
        runCurrent()
        advanceTimeBy(399)
        runCurrent()
        assertTrue(preferences.writes.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, preferences.writes.size)
        assertEquals(snapshot("a", 20), attachment.read())
        attachment.close()
        session.close()
    }

    @Test
    fun inactiveEntriesAreBoundedBeforeWritingAndReleasedOnceClean() = runTest {
        val preferences = Preferences()
        val session = FeedCacheSession(preferences, Status.serializer(), backgroundScope)
        val rows = (0..119).map { Status(id = "$it") }
        repeat(8) { index ->
            session.attach("$index").close(
                FeedSnapshot(rows, index = 110, offset = 900, nextCursor = "tail", continuationKnown = true),
            )
        }
        repeat(8) { index ->
            val attachment = session.attach("$index")
            val saved = assertNotNull(attachment.read())
            assertEquals(100, saved.items.size)
            assertEquals(99, saved.index)
            assertEquals(0, saved.offset)
            assertFalse(saved.continuationKnown)
            attachment.close()
        }
        assertTrue(preferences.reads.isEmpty())
        runCurrent()
        advanceTimeBy(400)
        runCurrent()
        assertEquals(8, preferences.values.size)
        repeat(8) { index ->
            val attachment = session.attach("$index")
            assertEquals(100, attachment.read()?.items?.size)
            attachment.close()
        }
        assertEquals(8, preferences.reads.size)
        session.close()
    }

    @Test
    fun scrollingReusesTheBoundedPayloadAndSerializesOnlyAfterDebounce() = runTest {
        val preferences = Preferences()
        var serializedItems = 0
        val serializer = object : KSerializer<String> by String.serializer() {
            override fun serialize(encoder: Encoder, value: String) {
                serializedItems++
                encoder.encodeString(value)
            }
        }
        val session = FeedCacheSession(preferences, serializer, backgroundScope)
        val attachment = session.attach("a")
        val content = FeedSnapshot(List(120) { "$it:${"x".repeat(10_000)}" }, continuationKnown = true)
        attachment.checkpoint(content)
        val bounded = assertNotNull(attachment.read())
        assertTrue(bounded.items.size in 1..99)
        assertFalse(bounded.continuationKnown)
        val afterContent = serializedItems
        repeat(200) { offset -> attachment.checkpoint(content.copy(offset = offset)) }
        attachment.checkpoint(content.atPosition(FeedPosition.Leading(Int.MAX_VALUE)))
        assertEquals(afterContent, serializedItems)
        assertEquals(Int.MAX_VALUE, attachment.read()?.leadingOffset)
        assertTrue(preferences.writes.isEmpty())
        runCurrent()
        advanceTimeBy(400)
        runCurrent()
        assertEquals(afterContent + bounded.items.size, serializedItems)
        assertTrue(assertNotNull(preferences.values["a"]).encodeToByteArray().size <= 512 * 1024)
        attachment.close()
        session.close()
    }

    @Test
    fun failedWritesKeepTheLatestSnapshotUntilItCanBePersisted() = runTest {
        val preferences = Preferences()
        preferences.beforeWrite = { error("Storage unavailable") }
        val session = FeedCacheSession(preferences, Status.serializer(), backgroundScope)
        session.attach("a").close(snapshot("saved", 48))
        runCurrent()
        advanceTimeBy(400)
        runCurrent()
        val retry = session.attach("a")
        assertEquals(snapshot("saved", 48), retry.read())
        assertTrue(preferences.reads.isEmpty())
        preferences.beforeWrite = {}
        retry.close(snapshot("saved", 48))
        runCurrent()
        advanceTimeBy(400)
        runCurrent()
        assertEquals(snapshot("saved", 48), FeedCache(preferences, "a", Status.serializer()).read())
        session.close()
    }

    @Test
    fun sessionCloseCancelsPendingPersistenceAndInvalidatesAttachments() = runTest {
        val preferences = Preferences()
        val session = FeedCacheSession(preferences, Status.serializer(), backgroundScope)
        val attachment = session.attach("a")
        attachment.checkpoint(snapshot("a"))
        runCurrent()
        session.close()
        session.close()
        attachment.close(snapshot("late"))
        advanceTimeBy(800)
        runCurrent()
        assertNull(attachment.read())
        assertTrue(preferences.writes.isEmpty())
        assertFailsWith<IllegalStateException> { session.attach("a") }
    }

    @Test
    fun pagerCloseCapturesOnceAndRejectsLateResponsesAndEvents() = runTest {
        val response = CompletableDeferred<Page<Status>>()
        val checkpoints = mutableListOf<FeedSnapshot<Status>>()
        var calls = 0
        val pager = FeedPager(backgroundScope, { status: Status -> status.id }, onClose = {
            checkpoints += it.snapshot(FeedPosition.Status(0, 82), FeedPosition.Leading(0)) { status -> status.id }
        }) {
            calls++
            withContext(NonCancellable) { response.await() }
        }
        pager.restore(snapshot("current"))
        pager.load()
        runCurrent()
        pager.close()
        pager.close()
        pager.created(Status(id = "event"), defer = false)
        pager.restore(snapshot("restore"))
        pager.remove { true }
        pager.load()
        response.complete(Page(listOf(Status(id = "response")), "wrong cursor"))
        runCurrent()

        assertEquals(listOf(snapshot("current", 82)), checkpoints)
        assertEquals(listOf("current"), pager.rows.map { it.id })
        assertEquals("tail:current", pager.snapshot().nextCursor)
        assertEquals(1, calls)
        assertFalse(pager.busy)
    }

    @Test
    fun checkpointsFollowProjectedIdentityAndNeverSaveFooterPixels() = runTest {
        val projection = StatusProjection()
        val pager = FeedPager(backgroundScope, { status: Status -> status.id }, projection::project) {
            Page(emptyList())
        }
        pager.restore(FeedSnapshot(listOf(Status(id = "before"), Status(id = "anchor"), Status(id = "after"))))
        projection.apply(StreamEvent.Deleted(StreamSource.User, "anchor"))
        val moved = pager.snapshot(FeedPosition.Status(1, 94), FeedPosition.Leading(0)) { it.id }
        assertEquals(listOf("before", "after"), moved.items.map { it.id })
        assertEquals(1, moved.index)
        assertEquals(0, moved.offset)
        val footer = pager.snapshot(FeedPosition.Status(3, 400), FeedPosition.Leading(0)) { it.id }
        assertEquals(1, footer.index)
        assertEquals(0, footer.offset)
        val header = pager.snapshot(FeedPosition.Leading(320), FeedPosition.Leading(0)) { it.id }
        assertEquals(320, header.leadingOffset)
        assertEquals(0, header.index)
        assertEquals(0, header.offset)
    }
}
