package com.quanquan.quantoot.ui

import com.quanquan.quantoot.model.*
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class StatusProjectionTest {
    private val original = Status(id = "post", content = "original", createdAt = "2026-11-17T00:00:00Z")
    private val edited = original.copy(content = "edited", editedAt = "2026-11-17T00:01:00Z")

    @Test
    fun eventAfterReadStartsWinsEvenWhenReadCompletesLast() = runTest {
        val statuses = StatusProjection()
        val response = CompletableDeferred<Status>()
        val read = async { statuses.read({ response.await() }) { listOf(it) } }
        runCurrent()
        statuses.apply(StreamEvent.Edited(StreamSource.User, edited))
        response.complete(original)
        read.await()
        assertEquals("edited", statuses.project(original)?.content)
    }

    @Test
    fun laterReadAndParsedEditTimeRejectOlderValues() = runTest {
        val statuses = StatusProjection()
        val first = CompletableDeferred<Status>()
        val read = async { statuses.read({ first.await() }) { listOf(it) } }
        runCurrent()
        statuses.read({ edited }) { listOf(it) }
        first.complete(original)
        read.await()
        statuses.read({ original }) { listOf(it) }
        assertEquals(edited.content, statuses.project(original)?.content)
        val equivalentTime = edited.copy(content = "same instant", editedAt = "2026-11-17T08:01:00+08:00")
        statuses.read({ equivalentTime }) { listOf(it) }
        assertEquals("same instant", statuses.project(original)?.content)
    }

    @Test
    fun staleActionPreservesEditsAndOnlyChangesItsOwnFields() = runTest {
        val statuses = StatusProjection()
        statuses.read({ original.copy(bookmarked = true, reblogged = true, reblogsCount = 8) }) { listOf(it) }
        val response = CompletableDeferred<Status>()
        val action = async { statuses.action(original.id, StatusAction.Favourite) { response.await() } }
        runCurrent()
        statuses.apply(StreamEvent.Edited(StreamSource.User, edited.copy(bookmarked = true, reblogged = true, reblogsCount = 8)))
        response.complete(original.copy(favourited = true, favouritesCount = 3))
        action.await()
        val actual = statuses.project(original)!!
        assertEquals(edited.content, actual.content)
        assertTrue(actual.bookmarked)
        assertTrue(actual.reblogged)
        assertEquals(8, actual.reblogsCount)
        assertTrue(actual.favourited)
        assertEquals(3, actual.favouritesCount)
    }

    @Test
    fun latestActionInvocationWinsAndOtherActionsCompleteIndependently() = runTest {
        val statuses = StatusProjection()
        statuses.read({ original }) { listOf(it) }
        val first = CompletableDeferred<Status>()
        val favourite = async { statuses.action(original.id, StatusAction.Favourite) { first.await() } }
        runCurrent()
        statuses.action(original.id, StatusAction.Favourite) { original.copy(favouritesCount = 2) }
        statuses.action(original.id, StatusAction.Bookmark) { original.copy(bookmarked = true) }
        first.complete(original.copy(favourited = true, favouritesCount = 3))
        favourite.await()
        val actual = statuses.project(original)!!
        assertFalse(actual.favourited)
        assertEquals(2, actual.favouritesCount)
        assertTrue(actual.bookmarked)
    }

    @Test
    fun confirmedActionSurvivesOldReadsAndStreamDefaultsUntilFreshReadSettlesIt() = runTest {
        val statuses = StatusProjection()
        statuses.read({ original }) { listOf(it) }
        val response = CompletableDeferred<Status>()
        val read = async { statuses.read({ response.await() }) { listOf(it) } }
        runCurrent()
        statuses.action(original.id, StatusAction.Favourite) { original.copy(favourited = true, favouritesCount = 4) }
        statuses.apply(StreamEvent.Edited(StreamSource.User, edited))
        response.complete(original)
        read.await()
        assertTrue(statuses.project(original)!!.favourited)
        assertEquals(4, statuses.project(original)!!.favouritesCount)
        statuses.read({ edited.copy(favouritesCount = 3) }) { listOf(it) }
        assertFalse(statuses.project(original)!!.favourited)
        assertEquals(3, statuses.project(original)!!.favouritesCount)
    }

    @Test
    fun streamDefaultsCannotClearViewerFlagsLearnedFromRest() = runTest {
        val statuses = StatusProjection()
        statuses.read({ original.copy(favourited = true, bookmarked = true, reblogged = true) }) { listOf(it) }
        statuses.apply(StreamEvent.Edited(StreamSource.Local, edited.copy(favouritesCount = 8, reblogsCount = 4)))
        val actual = statuses.project(original)!!
        assertTrue(actual.favourited)
        assertTrue(actual.bookmarked)
        assertTrue(actual.reblogged)
        assertEquals(8, actual.favouritesCount)
        assertEquals(4, actual.reblogsCount)
        statuses.read({ edited }) { listOf(it) }
        assertFalse(statuses.project(original)!!.favourited)
        assertFalse(statuses.project(original)!!.bookmarked)
        assertFalse(statuses.project(original)!!.reblogged)
    }

    @Test
    fun deletionBeatsReadActionBoostAndQuoteButOnlyDeletesItsOwnWrapper() = runTest {
        val statuses = StatusProjection()
        val boost = Status(id = "boost", reblog = original)
        val quote = Status(id = "quote", quote = Quote(quotedStatus = original, quotedStatusId = original.id))
        statuses.read({ listOf(boost, quote) }) { it }
        val response = CompletableDeferred<Status>()
        val action = async { statuses.action(original.id, StatusAction.Favourite) { response.await() } }
        runCurrent()
        statuses.apply(StreamEvent.Deleted(StreamSource.User, "post"))
        response.complete(original.copy(favourited = true))
        action.await()
        statuses.read({ original }) { listOf(it) }
        statuses.apply(StreamEvent.Created(StreamSource.User, original))
        assertNull(statuses.project(original))
        assertNull(statuses.project(boost))
        assertEquals("deleted", statuses.project(quote)?.quote?.state)
        assertNull(statuses.project(quote)?.quote?.quotedStatus)
        val another = StatusProjection()
        another.apply(StreamEvent.Deleted(StreamSource.Local, boost.id))
        assertNull(another.project(boost))
        assertEquals(original, another.project(original))
    }

    @Test
    fun staleNestedCopiesCannotOverwriteCanonicalOriginalOrQuote() = runTest {
        val statuses = StatusProjection()
        statuses.apply(StreamEvent.Edited(StreamSource.User, edited))
        val nested = Status(id = "boost", reblog = Status(id = "quoted", quote = Quote(quotedStatus = original)))
        statuses.read({ nested }) { listOf(it) }
        assertEquals(edited.content, statuses.project(nested)?.original?.quote?.quotedStatus?.content)
        assertEquals(original, StatusProjection().project(original))
    }
}
