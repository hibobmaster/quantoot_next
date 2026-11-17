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
import androidx.compose.runtime.mutableLongStateOf
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

internal sealed interface ProfileBodyOpening {
    data class Initial(val motionRevision: Long) : ProfileBodyOpening
    data object Selection : ProfileBodyOpening
}

internal class ProfileBodyAttachment(
    val selection: ProfileSelection,
    val opening: ProfileBodyOpening,
    val list: LazyListState = LazyListState(),
)

internal data class ProfileBodyLocation(val collapsed: Float, val index: Int = 0, val offset: Int = 0)

@Composable
internal fun rememberProfileScrollState(): ProfileScrollState = remember { ProfileScrollState() }

@Stable
internal class ProfileScrollState : ScrollableState {
    private val motion = MutatorMutex()
    var attachment by mutableStateOf(ProfileBodyAttachment(ProfileSelection(), ProfileBodyOpening.Initial(0)))
        private set
    private var motionRevision by mutableLongStateOf(0L)
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
            attachment = ProfileBodyAttachment(next, ProfileBodyOpening.Selection)
        }
    }

    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
        motionRevision++
        withMotion(scrollPriority) { block() }
    }

    override fun dispatchRawDelta(delta: Float): Float = 0f

    internal suspend fun restore(
        target: ProfileBodyAttachment,
        location: ProfileBodyLocation,
        expectedMotionRevision: Long? = null,
    ) {
        fun current() = target === attachment &&
            (expectedMotionRevision == null || motionRevision == expectedMotionRevision)
        if (!current()) return
        if (expectedMotionRevision == null) motionRevision++
        withMotion(MutatePriority.PreventUserInput) {
            if (!current()) return@withMotion
            geometry = geometry.copy(collapsed = location.collapsed.coerceIn(0f, geometry.headerHeight.toFloat()))
            body.snapToItem(location.index, location.offset)
        }
    }

    internal suspend fun reveal(region: ProfileRegion, bounds: () -> Rect?) {
        motionRevision++
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
}

internal enum class ProfileRegion { Header, Categories, Body }
