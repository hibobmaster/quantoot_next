package com.quanquan.quantoot.ui

import kotlin.test.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GallerySaveStateTest {
    private val first = GallerySaveTarget("https://media.example/first.gif", "quantoot-first")
    private val second = GallerySaveTarget("https://media.example/second.png", "quantoot-second")

    @Test
    fun pendingSaveKeepsItsTargetAndRejectsDuplicateSaves() = runTest {
        val state = GallerySaveState()
        val committed = CompletableDeferred<Unit>()
        val writes = mutableListOf<GallerySaveTarget>()
        var selection = first
        val target = selection
        val job = launch { state.save(target) { writes += it; committed.await() } }
        runCurrent()
        assertEquals(GallerySaveResult.Saving(first), state.result)
        assertTrue(state.saving)
        selection = second
        state.save(selection) { writes += it }
        assertEquals(listOf(first), writes)
        state.dismissFeedback()
        assertEquals(GallerySaveResult.Saving(first), state.result)
        committed.complete(Unit)
        job.join()
        assertEquals(GallerySaveResult.Saved(first), state.result)
        assertFalse(state.saving)
    }

    @Test
    fun failureRetriesTheCapturedTargetAndDismissesLocally() = runTest {
        val state = GallerySaveState()
        state.save(first) { error("Photos rejected the resource") }
        val failure = assertIs<GallerySaveResult.Failed>(state.result)
        assertEquals(first, failure.target)
        state.save(failure.target) {
            assertEquals(GallerySaveResult.Saving(first), state.result)
            assertEquals(first, it)
        }
        assertEquals(GallerySaveResult.Saved(first), state.result)
        state.dismissFeedback()
        assertEquals(GallerySaveResult.Idle, state.result)
    }

    @Test
    fun cancellationPropagatesAndDoesNotBecomeFailureFeedback() = runTest {
        val state = GallerySaveState()
        var propagated = false
        val job = launch {
            try {
                state.save(first) { awaitCancellation() }
            } catch (e: CancellationException) {
                propagated = true
                throw e
            }
        }
        runCurrent()
        assertTrue(state.saving)
        job.cancel()
        job.join()
        assertTrue(propagated)
        assertEquals(GallerySaveResult.Idle, state.result)
    }
}
