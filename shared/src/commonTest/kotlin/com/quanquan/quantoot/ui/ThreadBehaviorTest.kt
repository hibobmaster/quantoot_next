package com.quanquan.quantoot.ui

import com.quanquan.quantoot.model.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadBehaviorTest {
    private val root = Status(id = "root", repliesCount = 7)
    private val reply = Status(id = "reply", inReplyToId = "root")
    private val nested = Status(id = "nested", inReplyToId = "reply")

    @Test
    fun repliesDuringContextRequestSurviveAndDoNotInventCounts() = runTest {
        val projection = StatusProjection()
        val response = CompletableDeferred<ThreadResult>()
        var calls = 0
        val thread = ThreadState(root.id, backgroundScope, projection::project) {
            calls++
            response.await()
        }
        thread.refresh()
        runCurrent()
        val event = StreamEvent.Created(StreamSource.User, reply)
        projection.apply(event)
        thread.apply(event)
        thread.apply(event)
        response.complete(ThreadResult(root, StatusContext()))
        runCurrent()
        assertEquals(listOf("root", "reply"), thread.rows.map { it.id })
        assertEquals(7, thread.root?.repliesCount)
        assertEquals(2, calls)
        assertEquals(1, thread.descendants.size)
    }

    @Test
    fun contextOwnsOrderWhileEditsNeverCreateMembership() = runTest {
        val projection = StatusProjection()
        val thread = ThreadState(root.id, backgroundScope, projection::project) {
            ThreadResult(root, StatusContext(descendants = listOf(reply, nested)))
        }
        thread.refresh()
        runCurrent()
        val edit = StreamEvent.Edited(StreamSource.User, nested.copy(content = "edited"))
        projection.apply(edit)
        thread.apply(edit)
        val absent = StreamEvent.Edited(StreamSource.Local, Status(id = "absent", inReplyToId = root.id))
        projection.apply(absent)
        thread.apply(absent)
        assertEquals(listOf("root", "reply", "nested"), thread.rows.map { it.id })
        assertEquals("edited", thread.descendants.last().content)
        assertEquals(1, thread.depth(reply.id))
        assertEquals(2, thread.depth(nested.id))
    }

    @Test
    fun unrelatedPostsAreIgnoredAndUnknownParentsWaitForContext() = runTest {
        var next = ThreadResult(root, StatusContext())
        var calls = 0
        val thread = ThreadState(root.id, backgroundScope) { calls++; next }
        thread.refresh()
        runCurrent()
        thread.apply(StreamEvent.Created(StreamSource.User, Status(id = "unrelated")))
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, calls)
        thread.apply(StreamEvent.Created(StreamSource.User, nested))
        assertTrue(thread.descendants.isEmpty())
        next = ThreadResult(root, StatusContext(descendants = listOf(reply, nested)))
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("reply", "nested"), thread.descendants.map { it.id })
        assertEquals(2, calls)
    }

    @Test
    fun mentionNotificationAddsRelevantReplyAndReconnectsCoalesce() = runTest {
        var calls = 0
        val thread = ThreadState(root.id, backgroundScope) {
            calls++
            ThreadResult(root, StatusContext(descendants = listOf(reply)))
        }
        thread.refresh()
        runCurrent()
        thread.apply(StreamEvent.Notify(StreamSource.User, Notification(type = "mention", status = nested)))
        repeat(5) { thread.apply(StreamEvent.Reconnected(StreamSource.User)) }
        assertEquals(listOf("reply", "nested"), thread.descendants.map { it.id })
        assertEquals(1, calls)
        advanceTimeBy(300)
        runCurrent()
        assertEquals(2, calls)
        assertEquals(7, thread.root?.repliesCount)
    }

    @Test
    fun contextFailureAndRoot404KeepLoadedReplies() = runTest {
        val ancestor = Status(id = "ancestor")
        var next = ThreadResult(root, StatusContext(listOf(ancestor), listOf(reply, nested)))
        val thread = ThreadState(root.id, backgroundScope) { next }
        thread.refresh()
        runCurrent()
        next = ThreadResult(root.copy(repliesCount = 8), null, "Context unavailable")
        thread.refresh()
        runCurrent()
        assertEquals(listOf("ancestor", "root", "reply", "nested"), thread.rows.map { it.id })
        assertEquals(8, thread.root?.repliesCount)
        assertEquals("Context unavailable", thread.error)
        next = ThreadResult(null, null)
        thread.refresh()
        runCurrent()
        assertTrue(thread.rootUnavailable)
        assertNull(thread.root)
        assertEquals(listOf("ancestor", "reply", "nested"), thread.rows.map { it.id })
    }

    @Test
    fun deletionBeatsContextResponseAndPreservesKnownReplyAncestry() = runTest {
        val projection = StatusProjection()
        val response = CompletableDeferred<ThreadResult>()
        var calls = 0
        val thread = ThreadState(root.id, backgroundScope, projection::project) {
            if (++calls == 1) ThreadResult(root, StatusContext(descendants = listOf(reply)))
            else response.await()
        }
        thread.refresh()
        runCurrent()
        thread.refresh()
        runCurrent()
        for (id in listOf(root.id, reply.id)) {
            val deleted = StreamEvent.Deleted(StreamSource.User, id)
            projection.apply(deleted)
            thread.apply(deleted)
        }
        val created = StreamEvent.Created(StreamSource.User, nested)
        projection.apply(created)
        thread.apply(created)
        response.complete(ThreadResult(root, StatusContext(descendants = listOf(reply))))
        runCurrent()
        assertTrue(thread.rootUnavailable)
        assertEquals(listOf("nested"), thread.rows.map { it.id })
        assertEquals(2, thread.depth(nested.id))
    }

    @Test
    fun refreshErrorsKeepRowsAndBackgroundCancellationCannotReplaceThem() = runTest {
        val stale = CompletableDeferred<ThreadResult>()
        var calls = 0
        val thread = ThreadState(root.id, backgroundScope) {
            when (++calls) {
                1 -> ThreadResult(root, StatusContext(descendants = listOf(reply)))
                2 -> error("Offline")
                3 -> withContext(NonCancellable) { stale.await() }
                else -> ThreadResult(root, StatusContext(descendants = listOf(reply, nested)))
            }
        }
        thread.refresh()
        runCurrent()
        thread.refresh()
        runCurrent()
        assertEquals("Offline", thread.error)
        assertEquals(listOf("root", "reply"), thread.rows.map { it.id })
        thread.refresh()
        runCurrent()
        thread.setActive(false)
        thread.apply(StreamEvent.Reconnected(StreamSource.User))
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(3, calls)
        thread.setActive(true)
        thread.refresh()
        runCurrent()
        stale.complete(ThreadResult(null, StatusContext()))
        runCurrent()
        assertFalse(thread.rootUnavailable)
        assertEquals(listOf("root", "reply", "nested"), thread.rows.map { it.id })
    }

    @Test
    fun publishedReplyRemainsVisibleBeforeTheServerContextIncludesIt() = runTest {
        val thread = ThreadState(root.id, backgroundScope) { ThreadResult(root, StatusContext()) }
        thread.includePublished(reply)
        thread.refresh()
        runCurrent()
        assertEquals(listOf("reply"), thread.descendants.map { it.id })
        assertEquals(7, thread.root?.repliesCount)
    }

    @Test
    fun publishingReturnsToExistingThreadAndFocusesConfirmedReply() {
        val compose = Screen.Compose(reply = reply)
        val routes = listOf(Screen.Profile("author"), Screen.Thread(root.id), compose)
        assertEquals(
            listOf(Screen.Profile("author"), Screen.Thread(root.id, focusReplyId = nested.id)),
            routesAfterPublish(routes, compose, nested),
        )
        assertEquals(
            listOf(Screen.Thread(reply.id, focusReplyId = nested.id)),
            routesAfterPublish(listOf(compose), compose, nested),
        )
        assertEquals(
            listOf(Screen.Profile("author"), Screen.Thread(root.id)),
            navigateTo(routes, Screen.Thread(root.id)),
        )
        val focused = listOf(Screen.Thread(root.id, focusReplyId = nested.id))
        assertEquals(focused, navigateTo(focused, Screen.Thread(root.id)))
        val edit = Screen.Compose(edit = reply)
        assertEquals(listOf(Screen.Thread(root.id)), routesAfterPublish(listOf(Screen.Thread(root.id), edit), edit, reply))
    }
}
