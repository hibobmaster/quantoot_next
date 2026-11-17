package com.quanquan.quantoot.ui

import androidx.compose.foundation.MutatePriority
import com.quanquan.quantoot.model.Timeline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest

class ProfileFeedViewportTest {
    private val geometry = ProfileScrollGeometry(400, 48, 700, 400f)

    @Test
    fun legacyHeaderAndTabPixelsNeverBecomeStatusPixels() {
        assertEquals(
            ProfileBodyLocation(170f),
            profileBodyLocation(FeedPosition.Leading(170), geometry, firstStatusIndex = 3, activityHeight = 80),
        )
        assertEquals(
            ProfileBodyLocation(400f),
            profileBodyLocation(FeedPosition.Leading(425), geometry, firstStatusIndex = 3, activityHeight = 80),
        )
        assertEquals(
            ProfileBodyLocation(400f),
            profileBodyLocation(FeedPosition.Leading(600), geometry, firstStatusIndex = 0, activityHeight = 0),
        )
    }

    @Test
    fun legacyActivityPixelsRestoreInsideTheControlsAndClampToTheirHeight() {
        assertEquals(
            ProfileBodyLocation(400f, offset = 35),
            profileBodyLocation(FeedPosition.Leading(483), geometry, firstStatusIndex = 1, activityHeight = 80),
        )
        assertEquals(
            ProfileBodyLocation(400f, offset = 79),
            profileBodyLocation(FeedPosition.Leading(900), geometry, firstStatusIndex = 1, activityHeight = 80),
        )
        assertEquals(
            FeedPosition.Leading(483),
            profileFeedPosition(geometry, ProfileVisibleItem.ActivityControls(35), rowCount = 20),
        )
    }

    @Test
    fun statusRestorationAccountsForBodyControlsWithoutChangingStatusOffset() {
        assertEquals(
            ProfileBodyLocation(400f, index = 5, offset = 72),
            profileBodyLocation(FeedPosition.Status(2, 72), geometry, firstStatusIndex = 3, activityHeight = 80),
        )
        assertEquals(
            FeedPosition.Status(2, 72),
            profileFeedPosition(geometry, ProfileVisibleItem.Status(2, 72), rowCount = 20),
        )
    }

    @Test
    fun anExpandedHeaderAndBodyControlsProduceOnlyLeadingCoordinates() {
        assertEquals(
            FeedPosition.Leading(180),
            profileFeedPosition(geometry.copy(collapsed = 180f), ProfileVisibleItem.Status(0, 0), rowCount = 20),
        )
        for (item in listOf(ProfileVisibleItem.Control, ProfileVisibleItem.ActivityControls(0), null)) {
            assertEquals(FeedPosition.Leading(400), profileFeedPosition(geometry, item, rowCount = 20))
            assertFalse(profileReadingOlder(geometry, item, rowCount = 20))
        }
    }

    @Test
    fun footerAfterOneStatusDefersArrivalsButCachesASafeStatusPosition() {
        val footer = ProfileVisibleItem.Footer
        assertEquals(FeedPosition.Status(0, 0), profileFeedPosition(geometry, footer, rowCount = 1))
        assertTrue(profileReadingOlder(geometry, footer, rowCount = 1))
        assertEquals(FeedPosition.Leading(400), profileFeedPosition(geometry, footer, rowCount = 0))
        assertFalse(profileReadingOlder(geometry, footer, rowCount = 0))
        assertFalse(profileReadingOlder(geometry.copy(collapsed = 180f), footer, rowCount = 1))
    }

    @Test
    fun statusDeferralKeepsTheExistingReadingThreshold() {
        assertFalse(profileReadingOlder(geometry, ProfileVisibleItem.Status(0, 160), rowCount = 4))
        assertTrue(profileReadingOlder(geometry, ProfileVisibleItem.Status(0, 161), rowCount = 4))
        assertTrue(profileReadingOlder(geometry, ProfileVisibleItem.Status(1, 0), rowCount = 4))
    }

    @Test
    fun categoryQueriesKeepTheirExistingMeaning() {
        val id = "person"
        assertEquals(Timeline.AccountPosts(id, excludeReplies = true, excludeReblogs = true), ProfileSelection().source(id))
        assertEquals(Timeline.AccountPosts(id, onlyMedia = true), ProfileSelection(ProfileCategory.Media).source(id))
        assertEquals(Timeline.AccountPosts(id, pinned = true), ProfileSelection(ProfileCategory.Featured).source(id))
        for (replies in listOf(false, true)) for (boosts in listOf(false, true)) {
            assertEquals(
                Timeline.AccountPosts(id, excludeReplies = !replies, excludeReblogs = !boosts),
                ProfileSelection(ProfileCategory.Activity, replies, boosts).source(id),
            )
        }
        assertNull(ProfileSelection(ProfileCategory.About).source(id))
    }

    @Test
    fun delayedCacheRestorationCannotMoveAReplacementBody() = runTest {
        val state = ProfileScrollState()
        state.measured(400, 48, 700)
        val outgoing = state.attachment
        state.select(ProfileSelection(ProfileCategory.Media))
        state.restore(outgoing, ProfileBodyLocation(400f, index = 10, offset = 200))
        assertEquals(ProfileCategory.Media, state.selection.category)
        assertEquals(0f, state.geometry.collapsed)
        assertEquals(0, state.bodyState.firstVisibleItemIndex)
        assertEquals(0, state.bodyState.firstVisibleItemScrollOffset)
    }

    @Test
    fun delayedCacheRestorationCannotOverrideNewerInput() = runTest {
        val state = ProfileScrollState()
        state.measured(400, 48, 700)
        val initial = state.attachment
        val reading = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            state.scroll(MutatePriority.UserInput) { scrollBy(100f) }
        }
        state.restore(initial, ProfileBodyLocation(400f, index = 10), expectedMotionRevision = 0)
        assertEquals(0f, state.geometry.collapsed)
        assertEquals(0, state.bodyState.firstVisibleItemIndex)
        reading.cancel()
    }
}
