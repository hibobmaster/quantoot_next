package com.quanquan.quantoot.ui

import androidx.compose.ui.geometry.Offset
import kotlin.test.*

class GalleryGestureStateTest {
    @Test
    fun upwardAndDownwardDragsFollowTheFingerAndDismissOnce() {
        for (direction in listOf(-1f, 1f)) {
            val gesture = GalleryGestureState()
            gesture.begin()
            assertEquals(GalleryGestureOwner.Dismiss, gesture.move(Offset(2f, 80f * direction), 1, false, 10f))
            gesture.move(Offset(-2f, 90f * direction), 1, false, 10f)
            assertEquals(170f * direction, gesture.dismissOffset)
            val animation = assertNotNull(gesture.release(1000f, 160f))
            assertEquals(170f * direction, animation.from)
            assertEquals(1000f * direction, animation.target)
            assertTrue(gesture.complete(animation))
            assertFalse(gesture.complete(animation))
        }
    }

    @Test
    fun shortDragsReboundInEitherDirection() {
        for (distance in listOf(-119f, 119f)) {
            val gesture = GalleryGestureState()
            gesture.begin()
            gesture.move(Offset(0f, distance), 1, false, 10f)
            val rebound = assertNotNull(gesture.release(600f, 160f))
            assertEquals(distance, rebound.from)
            assertEquals(0f, rebound.target)
            assertFalse(gesture.complete(rebound))
            assertEquals(0f, gesture.dismissOffset)
        }
    }

    @Test
    fun thresholdUsesTheSmallerOfViewportFractionAndDensityAdjustedCap() {
        for ((height, cap, threshold) in listOf(Triple(600f, 160f, 120f), Triple(2000f, 320f, 320f))) {
            for (direction in listOf(-1f, 1f)) {
                val gesture = GalleryGestureState()
                gesture.begin()
                gesture.move(Offset(0f, (threshold - 1f) * direction), 1, false, 10f)
                assertEquals(0f, assertNotNull(gesture.release(height, cap)).target)
                gesture.begin()
                gesture.move(Offset(0f, threshold * direction), 1, false, 10f)
                assertTrue(gesture.complete(assertNotNull(gesture.release(height, cap))))
            }
        }
    }

    @Test
    fun smallMovementWaitsForSlopAndHorizontalMovementStaysWithPager() {
        val gesture = GalleryGestureState()
        gesture.begin()
        assertEquals(GalleryGestureOwner.Undecided, gesture.move(Offset(4f, 3f), 1, false, 10f))
        assertEquals(0f, gesture.dismissOffset)
        assertEquals(GalleryGestureOwner.Pager, gesture.move(Offset(10f, 2f), 1, false, 10f))
        assertEquals(GalleryGestureOwner.Pager, gesture.move(Offset(0f, 300f), 1, false, 10f))
        assertEquals(0f, gesture.dismissOffset)
        assertNull(gesture.release(600f, 160f))
    }

    @Test
    fun zoomedPanNeverBecomesDismissalWhenScaleReturnsToOne() {
        val gesture = GalleryGestureState()
        gesture.begin()
        assertEquals(GalleryGestureOwner.Undecided, gesture.move(Offset(0f, 2f), 1, true, 10f))
        assertEquals(GalleryGestureOwner.Transform, gesture.move(Offset(0f, 100f), 1, true, 10f))
        assertEquals(GalleryGestureOwner.Transform, gesture.move(Offset(0f, 300f), 1, false, 10f))
        assertEquals(0f, gesture.dismissOffset)
        assertNull(gesture.release(600f, 160f))
        gesture.begin()
        assertEquals(GalleryGestureOwner.Dismiss, gesture.move(Offset(0f, 150f), 1, false, 10f))
    }

    @Test
    fun secondPointerCancelsDismissalAndOwnsTheRemainingGesture() {
        val gesture = GalleryGestureState()
        gesture.begin()
        gesture.move(Offset(0f, -200f), 1, false, 10f)
        assertEquals(-200f, gesture.dismissOffset)
        assertEquals(GalleryGestureOwner.Transform, gesture.move(Offset.Zero, 2, false, 10f))
        assertEquals(0f, gesture.dismissOffset)
        assertEquals(GalleryGestureOwner.Transform, gesture.move(Offset(0f, -400f), 1, false, 10f))
        assertNull(gesture.release(600f, 160f))
    }

    @Test
    fun twoPointersTakeOverHorizontalPagingEvenBeforeZoomChanges() {
        val gesture = GalleryGestureState()
        gesture.begin()
        assertEquals(GalleryGestureOwner.Pager, gesture.move(Offset(100f, 0f), 1, false, 10f))
        assertEquals(GalleryGestureOwner.Transform, gesture.move(Offset.Zero, 2, false, 10f))
        assertEquals(GalleryGestureOwner.Transform, gesture.move(Offset(0f, 250f), 1, false, 10f))
        assertNull(gesture.release(600f, 160f))
    }

    @Test
    fun cancelledLongDragReboundsWithoutDismissal() {
        val gesture = GalleryGestureState()
        gesture.begin()
        gesture.move(Offset(0f, 400f), 1, false, 10f)
        val rebound = assertNotNull(gesture.release(600f, 160f, cancelled = true))
        assertEquals(0f, rebound.target)
        assertFalse(gesture.complete(rebound))
        assertEquals(0f, gesture.dismissOffset)
    }

    @Test
    fun pageOrZoomCancellationRejectsStaleAnimationUpdatesAndCompletion() {
        val gesture = GalleryGestureState()
        val generation = gesture.begin()
        gesture.move(Offset(0f, 200f), 1, false, 10f)
        val animation = assertNotNull(gesture.release(600f, 160f))
        gesture.cancel()
        assertFalse(gesture.isCurrent(generation))
        gesture.animate(animation, 450f)
        assertEquals(0f, gesture.dismissOffset)
        assertFalse(gesture.complete(animation))
        gesture.begin()
        gesture.move(Offset(0f, -20f), 1, false, 10f)
        gesture.animate(animation, 600f)
        assertEquals(-20f, gesture.dismissOffset)
        assertFalse(gesture.complete(animation))
    }
}
