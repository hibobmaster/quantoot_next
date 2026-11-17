package com.quanquan.quantoot.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileScrollGeometryTest {
    @Test
    fun emptyBodyAllowsTheWholeUncappedHeaderToScroll() {
        val start = ProfileScrollGeometry(1400, 56, 700)
        val partial = start.scroll(300f) { 0f }
        assertEquals(300f, partial.consumed)
        assertEquals(1100f, partial.geometry.categoryTop)
        val pinned = partial.geometry.scroll(2000f) { 0f }
        assertEquals(1100f, pinned.consumed)
        assertEquals(0f, pinned.geometry.categoryTop)
        assertEquals(644, pinned.geometry.bodyHeight)
    }

    @Test
    fun forwardMotionCrossesTheHeaderBodyBoundaryInOneDelta() {
        var bodyReceived = 0f
        val result = ProfileScrollGeometry(300, 48, 700, 250f).scroll(180f) {
            bodyReceived = it
            it
        }
        assertEquals(130f, bodyReceived)
        assertEquals(180f, result.consumed)
        assertEquals(300f, result.geometry.collapsed)
    }

    @Test
    fun reverseMotionReturnsTheBodyBeforeExpandingTheHeader() {
        val start = ProfileScrollGeometry(300, 48, 700, 300f)
        val stillInBody = start.scroll(-100f) { it }
        assertEquals(300f, stillInBody.geometry.collapsed)
        val crossing = stillInBody.geometry.scroll(-200f) { -70f }
        assertEquals(-200f, crossing.consumed)
        assertEquals(170f, crossing.geometry.collapsed)
        val atTop = crossing.geometry.scroll(-400f) { 0f }
        assertEquals(-170f, atTop.consumed)
        assertEquals(0f, atTop.geometry.collapsed)
    }

    @Test
    fun replacingBodyExtentDoesNotAffectVisibleHeaderGeometry() {
        val geometry = ProfileScrollGeometry(430, 52, 750, 180f)
        for (bodyRange in listOf(0f, 40f, 9000f)) {
            val switched = geometry.measured(430, 52, 750)
            assertEquals(250f, switched.categoryTop)
            assertEquals(302f, switched.bodyTop)
            assertEquals(0f, switched.scroll(0f) { it.coerceAtMost(bodyRange) }.consumed)
        }
    }

    @Test
    fun headerRemeasurePreservesTheCategoryPositionUnlessItIsImpossible() {
        val partial = ProfileScrollGeometry(400, 48, 700, 120f)
        assertEquals(280f, partial.measured(900, 68, 700).categoryTop)
        assertEquals(100f, partial.measured(100, 48, 700).categoryTop)
        val collapsed = partial.copy(collapsed = 400f)
        assertEquals(0f, collapsed.measured(900, 68, 700).categoryTop)
    }

    @Test
    fun bodyVisibilityUsesTheOuterClipAndTheListsOwnTop() {
        val partial = ProfileScrollGeometry(400, 48, 700, 100f)
        assertTrue(partial.bodyItemIsVisible(300, 100))
        assertFalse(partial.bodyItemIsVisible(352, 100))
        assertFalse(partial.bodyItemIsVisible(-100, 100))
        assertTrue(partial.bodyItemIsVisible(-20, 100))
        assertFalse(ProfileScrollGeometry(1000, 48, 700).bodyItemIsVisible(0, 100))
    }

    @Test
    fun revealUsesTheBodyAreaBelowThePinnedRow() {
        assertEquals(-28f, profileRevealDistance(20f, 100f, 48f, 700f))
        assertEquals(80f, profileRevealDistance(650f, 780f, 48f, 700f))
        assertEquals(0f, profileRevealDistance(60f, 100f, 48f, 700f))
        assertEquals(0f, profileRevealDistance(0f, 900f, 48f, 700f))
    }
}
