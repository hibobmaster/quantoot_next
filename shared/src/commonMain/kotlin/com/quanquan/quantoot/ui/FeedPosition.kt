package com.quanquan.quantoot.ui

internal sealed interface FeedPosition {
    val offset: Int

    data class Leading(override val offset: Int) : FeedPosition
    data class Status(val index: Int, override val offset: Int) : FeedPosition

    fun readingOlder(): Boolean = this is Status && (index > 0 || offset > 160)

    fun listIndex(hasLeading: Boolean): Int = when (this) {
        is Leading -> 0
        is Status -> index + if (hasLeading) 1 else 0
    }
}

internal fun feedPosition(index: Int, offset: Int, hasLeading: Boolean): FeedPosition =
    if (hasLeading && index == 0) FeedPosition.Leading(offset)
    else FeedPosition.Status(index - if (hasLeading) 1 else 0, offset)

internal fun <T> FeedSnapshot<T>.atPosition(position: FeedPosition): FeedSnapshot<T> = when (position) {
    is FeedPosition.Leading -> copy(index = 0, offset = 0, leadingOffset = position.offset)
    is FeedPosition.Status -> copy(index = position.index, offset = position.offset, leadingOffset = null)
}

internal fun <T> FeedSnapshot<T>.restoredPosition(
    rows: List<T>,
    id: (T) -> String,
    hasLeading: Boolean,
): FeedPosition {
    if (hasLeading && leadingOffset != null) return FeedPosition.Leading(leadingOffset)
    val positions = rows.mapIndexed { index, item -> id(item) to index }.toMap()
    val anchorIndex = index.coerceIn(0, items.lastIndex.coerceAtLeast(0))
    val neighbors = (anchorIndex..items.lastIndex) + (anchorIndex - 1 downTo 0)
    for (savedIndex in neighbors) {
        val restoredIndex = positions[id(items[savedIndex])] ?: continue
        return FeedPosition.Status(restoredIndex, if (savedIndex == index) offset else 0)
    }
    return if (hasLeading) FeedPosition.Leading(0) else FeedPosition.Status(0, 0)
}

internal fun FeedPosition.Leading.measuredOffset(height: Int): Int =
    offset.coerceIn(0, (height - 1).coerceAtLeast(0))

internal fun feedNeedsMore(visibleIndices: List<Int>, rowCount: Int, hasLeading: Boolean): Boolean {
    val firstStatus = if (hasLeading) 1 else 0
    val footer = firstStatus + rowCount
    return visibleIndices.any { index ->
        index == footer || (index in firstStatus until footer && index - firstStatus >= rowCount - 3)
    }
}
