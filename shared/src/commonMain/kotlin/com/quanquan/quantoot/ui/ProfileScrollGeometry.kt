package com.quanquan.quantoot.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class ProfileScrollGeometry(
    val headerHeight: Int = 0,
    val categoryHeight: Int = 0,
    val viewportHeight: Int = 0,
    val collapsed: Float = 0f,
) {
    val categoryTop: Float get() = headerHeight - collapsed
    val bodyTop: Float get() = categoryTop + categoryHeight
    val bodyHeight: Int get() = (viewportHeight - categoryHeight).coerceAtLeast(0)

    fun measured(header: Int, category: Int, viewport: Int): ProfileScrollGeometry {
        val nextCollapse = when {
            headerHeight == 0 -> 0f
            collapsed == headerHeight.toFloat() -> header.toFloat()
            else -> (header - categoryTop).coerceIn(0f, header.toFloat())
        }
        return ProfileScrollGeometry(header, category, viewport, nextCollapse)
    }

    fun bodyItemIsVisible(offset: Int, size: Int): Boolean {
        val top = max(bodyTop, bodyTop + offset)
        val bottom = min(viewportHeight.toFloat(), bodyTop + offset + size)
        return bottom > top
    }

    fun scroll(delta: Float, bodyScroll: (Float) -> Float): ProfileScrollResult {
        if (!delta.isFinite()) return ProfileScrollResult(this, 0f)
        if (delta >= 0f) {
            val headerDelta = min(delta, headerHeight - collapsed)
            val bodyDelta = bodyScroll(delta - headerDelta)
            return ProfileScrollResult(copy(collapsed = collapsed + headerDelta), headerDelta + bodyDelta)
        }
        val bodyDelta = bodyScroll(delta)
        val headerDelta = max(delta - bodyDelta, -collapsed)
        return ProfileScrollResult(copy(collapsed = collapsed + headerDelta), bodyDelta + headerDelta)
    }
}

internal data class ProfileScrollResult(val geometry: ProfileScrollGeometry, val consumed: Float)

internal fun profileRevealDistance(top: Float, bottom: Float, visibleTop: Float, visibleBottom: Float): Float = when {
    top >= visibleTop && bottom <= visibleBottom -> 0f
    top < visibleTop && bottom > visibleBottom -> 0f
    abs(top - visibleTop) < abs(bottom - visibleBottom) -> top - visibleTop
    else -> bottom - visibleBottom
}
