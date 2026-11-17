package com.quanquan.quantoot.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyLayoutScrollScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates

internal enum class ProfileCategory(val titleKey: String) {
    Posts("mastodon_posts"),
    Media("mastodon_media"),
    Featured("mastodon_featured"),
    Activity("mastodon_activity"),
    About("settings_about"),
}

internal data class ProfileSelection(
    val category: ProfileCategory = ProfileCategory.Posts,
    val showReplies: Boolean = false,
    val showBoosts: Boolean = true,
)

@Composable
internal fun rememberProfileScrollState(): ProfileScrollState = remember { ProfileScrollState() }

@Stable
internal class ProfileScrollState : ScrollableState {
    private val motion = MutatorMutex()
    private var attachment by mutableStateOf(BodyAttachment(ProfileSelection(), LazyListState()))
    var geometry by mutableStateOf(ProfileScrollGeometry())
        private set
    var coordinates: LayoutCoordinates? = null
        internal set
    val selection: ProfileSelection get() = attachment.selection
    val bodyState: LazyListState get() = attachment.list

    override var isScrollInProgress by mutableStateOf(false)
        private set
    override val canScrollForward: Boolean
        get() = geometry.collapsed < geometry.headerHeight || bodyState.canScrollForward
    override val canScrollBackward: Boolean
        get() = geometry.collapsed > 0f || bodyState.canScrollBackward

    val accessibilityOffset: Float
        get() = geometry.collapsed + bodyState.firstVisibleItemIndex * 500f + bodyState.firstVisibleItemScrollOffset

    internal fun measured(header: Int, category: Int, viewport: Int) {
        geometry = geometry.measured(header, category, viewport)
    }

    suspend fun select(next: ProfileSelection) {
        if (next == selection) return
        motion.mutate(MutatePriority.PreventUserInput) {
            attachment = BodyAttachment(next, LazyListState())
        }
    }

    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
        withMotion(scrollPriority) { block() }
    }

    // Nested children cannot bypass the controller's mutation and body-scroll ownership.
    override fun dispatchRawDelta(delta: Float): Float = 0f

    internal suspend fun reveal(region: ProfileRegion, bounds: () -> Rect?) {
        withMotion(MutatePriority.Default) {
            val rect = bounds() ?: return@withMotion
            val top = if (region == ProfileRegion.Body) geometry.categoryHeight.toFloat() else 0f
            val distance = profileRevealDistance(rect.top, rect.bottom, top, geometry.viewportHeight.toFloat())
            if (distance == 0f) return@withMotion
            if (region == ProfileRegion.Header) body.snapToItem(0)
            var previous = 0f
            animate(0f, distance) { value, _ ->
                scrollBy(value - previous)
                previous = value
            }
        }
    }

    private suspend fun withMotion(priority: MutatePriority, block: suspend MotionScope.() -> Unit) {
        motion.mutate(priority) {
            val list = bodyState
            isScrollInProgress = true
            try {
                list.scroll(priority) {
                    MotionScope(LazyLayoutScrollScope(list, this)).block()
                }
            } finally {
                isScrollInProgress = false
            }
        }
    }

    private inner class MotionScope(val body: androidx.compose.foundation.lazy.layout.LazyLayoutScrollScope) : ScrollScope {
        override fun scrollBy(pixels: Float): Float {
            val result = geometry.scroll(pixels, body::scrollBy)
            geometry = result.geometry
            return result.consumed
        }
    }

    private data class BodyAttachment(val selection: ProfileSelection, val list: LazyListState)
}

internal enum class ProfileRegion { Header, Categories, Body }
