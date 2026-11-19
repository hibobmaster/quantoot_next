package com.quanquan.quantoot.ui

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.runtime.snapshotFlow
import com.quanquan.quantoot.model.Status
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

internal enum class ProfileFeedControl(val key: String) {
    Activity("profile:activity"),
    Pending("profile:pending"),
    Error("profile:error"),
}

internal const val PROFILE_FOOTER_KEY = "profile:footer"
internal const val PROFILE_EMPTY_KEY = "profile:empty"
internal fun profileStatusKey(id: String) = "status:$id"

internal class ProfileFeedViewport(
    private val state: ProfileScrollState,
    private val body: ProfileBodyAttachment,
) : FeedViewport {
    private lateinit var pager: FeedPager<Status>

    fun attach(pager: FeedPager<Status>) {
        this.pager = pager
    }

    val controls: List<ProfileFeedControl>
        get() = buildList {
            if (body.selection.category == ProfileCategory.Activity) add(ProfileFeedControl.Activity)
            if (pager.pending.isNotEmpty()) add(ProfileFeedControl.Pending)
            if (pager.error != null) add(ProfileFeedControl.Error)
        }

    val footerKey: String get() = if (pager.rows.isEmpty()) PROFILE_EMPTY_KEY else PROFILE_FOOTER_KEY
    override val emptyPosition: FeedPosition = FeedPosition.Leading(0)

    private fun visibleItems(): List<ProfileVisibleItem> = body.list.layoutInfo.visibleItemsInfo
        .filter { state.geometry.bodyItemIsVisible(it.offset, it.size) }
        .map(::visibleItem)

    private fun visibleItem(item: LazyListItemInfo): ProfileVisibleItem {
        val offset = (-item.offset).coerceAtLeast(0)
        return when (item.key) {
            ProfileFeedControl.Activity.key -> ProfileVisibleItem.ActivityControls(offset)
            PROFILE_FOOTER_KEY, PROFILE_EMPTY_KEY -> ProfileVisibleItem.Footer
            else -> {
                val index = pager.rows.indexOfFirst { profileStatusKey(it.id) == item.key }
                if (index >= 0) ProfileVisibleItem.Status(index, offset) else ProfileVisibleItem.Control
            }
        }
    }

    override fun position(): FeedPosition =
        profileFeedPosition(state.geometry, visibleItems().firstOrNull(), pager.rows.size)

    override fun readingOlder(): Boolean =
        profileReadingOlder(state.geometry, visibleItems().firstOrNull(), pager.rows.size)

    override fun isSettledAtTop(): Boolean = topAfterReveal() != null

    override fun requestTop() {
        topAfterReveal()?.let { body.list.requestScrollToItem(it) }
    }

    private fun topAfterReveal(): Int? {
        val list = body.list
        if (state.attachment !== body || state.isScrollInProgress || list.isScrollInProgress) return null
        if (list.firstVisibleItemScrollOffset != 0) return null
        val index = list.firstVisibleItemIndex
        if (index == 0) return 0

        // Leading controls may have been scrolled away, or moved the first status down an index.
        val key = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.key ?: return null
        return profileHeadAfterReveal(key, pager.rows.firstOrNull()?.id, controls)
    }

    override fun nearEnd(rowCount: Int): Boolean = visibleItems().any {
        it == ProfileVisibleItem.Footer || it is ProfileVisibleItem.Status && it.index >= rowCount - 3
    }

    private fun layoutMatches(): Boolean {
        val controls = controls
        val rows = pager.rows
        val layout = body.list.layoutInfo
        return state.geometry.viewportHeight > 0 && layout.totalItemsCount == controls.size + rows.size + 1 &&
            layout.visibleItemsInfo.all { item ->
                val key = controls.getOrNull(item.index)?.key
                    ?: rows.getOrNull(item.index - controls.size)?.let { profileStatusKey(it.id) }
                    ?: footerKey
                item.key == key
            }
    }

    private suspend fun awaitLayout() {
        snapshotFlow { state.attachment !== body || layoutMatches() }.first { it }
    }

    override suspend fun restore(position: FeedPosition) {
        awaitLayout()
        val opening = body.opening
        if (opening !is ProfileBodyOpening.Initial) return
        val activityHeight = body.list.layoutInfo.visibleItemsInfo
            .firstOrNull { it.key == ProfileFeedControl.Activity.key }?.size ?: 0
        state.restore(
            body,
            profileBodyLocation(position, state.geometry, controls.size, activityHeight),
            opening.motionRevision,
        )
    }

    override suspend fun revealFirstStatus() {
        awaitLayout()
        state.restore(body, ProfileBodyLocation(state.geometry.headerHeight.toFloat(), controls.size))
    }
}

internal fun profileHeadAfterReveal(firstKey: Any, firstStatusId: String?, controls: List<ProfileFeedControl>): Int? =
    when (firstKey) {
        ProfileFeedControl.Activity.key -> 0
        ProfileFeedControl.Pending.key, ProfileFeedControl.Error.key ->
            controls.count { it == ProfileFeedControl.Activity }
        firstStatusId?.let(::profileStatusKey), PROFILE_EMPTY_KEY ->
            controls.count { it != ProfileFeedControl.Pending }
        else -> null
    }

internal sealed interface ProfileVisibleItem {
    data class Status(val index: Int, val offset: Int) : ProfileVisibleItem
    data class ActivityControls(val offset: Int) : ProfileVisibleItem
    data object Footer : ProfileVisibleItem
    data object Control : ProfileVisibleItem
}

internal fun profileFeedPosition(
    geometry: ProfileScrollGeometry,
    first: ProfileVisibleItem?,
    rowCount: Int,
): FeedPosition {
    if (geometry.collapsed < geometry.headerHeight) return FeedPosition.Leading(geometry.collapsed.roundToInt())
    return when {
        first is ProfileVisibleItem.Status -> FeedPosition.Status(first.index, first.offset)
        first is ProfileVisibleItem.ActivityControls && first.offset > 0 ->
            FeedPosition.Leading(geometry.headerHeight + geometry.categoryHeight + first.offset)
        first == ProfileVisibleItem.Footer && rowCount > 0 -> FeedPosition.Status(rowCount - 1, 0)
        else -> FeedPosition.Leading(geometry.headerHeight)
    }
}

internal fun profileReadingOlder(
    geometry: ProfileScrollGeometry,
    first: ProfileVisibleItem?,
    rowCount: Int,
): Boolean = geometry.collapsed == geometry.headerHeight.toFloat() &&
    (first == ProfileVisibleItem.Footer && rowCount > 0 || profileFeedPosition(geometry, first, rowCount).readingOlder())

internal fun profileBodyLocation(
    position: FeedPosition,
    geometry: ProfileScrollGeometry,
    firstStatusIndex: Int,
    activityHeight: Int,
): ProfileBodyLocation = when (position) {
    is FeedPosition.Status ->
        ProfileBodyLocation(geometry.headerHeight.toFloat(), firstStatusIndex + position.index, position.offset)
    is FeedPosition.Leading -> ProfileBodyLocation(
        collapsed = position.offset.coerceIn(0, geometry.headerHeight).toFloat(),
        offset = (position.offset - geometry.headerHeight - geometry.categoryHeight)
            .coerceIn(0, (activityHeight - 1).coerceAtLeast(0)),
    )
}
