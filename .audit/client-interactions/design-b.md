# Interaction design candidate B

## Problem

Profile needs one continuous vertical reading surface, without changing ownership of `FeedPager`, projection, source identity, account lifetime, or status-only cache coordinates. Feed restoration, deferred arrivals, refresh, pagination, Activity filters, About, and changing text size must continue to work. Home selection must outlive route composition; gallery dismissal must coexist with paging, zoom and native controls.

## Usage (caller's view)

The profile supplies its header and tabs as one leading list item. `FeedScreen` owns the entire list and translates all feed coordinates; `ProfileScreen` owns content and actions. A normal feed passes no leading content. About uses the same profile surface, without a feed pager.

```kotlin
// Accounts.kt, Posts / Media / Featured / Activity
FeedScreen(ui, source = profileTimeline(id, selected, showReplies, showBoosts),
    leading = {
        ProfileHeader(person, /* existing handlers */)
        ProfileTabs(selected, onSelect = { selected = it })
        if (selected == ACTIVITY) ActivityFilters(/* existing state */)
    })

// Accounts.kt, About. No vertically scrolling child.
ProfileAboutScreen(leading = { ProfileHeaderAndTabs(/* same owners */) }) {
    ProfileAbout(person)
}

// QuanTootApp.kt, AppShell, outside key(route, tab)
var homeSelection by remember { mutableIntStateOf(if (ui.me == null) 1 else 0) }
// Inside route dispatch:
HomeScreen(ui, homeSelection, onSelect = { homeSelection = it })

// MediaGallery.kt: only the image viewport handles dismissal, not its siblings.
GalleryPage(ui, media, image, active, onDismiss = onDismiss)
```

## Shape

```kotlin
// Feeds.kt: one stable, optional prefix item; all coordinate details private.
@Composable
fun FeedScreen(
    ui: UiContext,
    source: Timeline,
    isActive: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
)

// Internal model of the logical first visible position. Never place a prefix
// index or prefix pixel offset into FeedSnapshot.index / .offset.
private sealed interface FeedPosition {
    data class Leading(val offset: Int) : FeedPosition
    data class Status(val id: String, val offset: Int) : FeedPosition
    data object Top : FeedPosition // no visible status, including empty feed
}

// FeedCache.kt: additive optional field on existing serialized snapshot;
// null means the existing status-only position. Defaults preserve old snapshots.
@Serializable
internal data class FeedSnapshot<T>(
    val items: List<T> = emptyList(),
    val index: Int = 0,                 // index in items, always status-only
    val offset: Int = 0,                // offset into that status, never header
    val nextCursor: String? = null,
    val continuationKnown: Boolean = false,
    val leadingOffset: Int? = null,     // explicit separate header position
)

// Private to feed rendering: 0 or 1 leading items, stable keyed status items,
// and one keyed footer. All source/index conversions happen here.
private class FeedListCoordinates(private val hasLeading: Boolean) {
    fun capture(layout: LazyListLayoutInfo, rows: List<Status>): FeedPosition = TODO()
    fun restore(saved: FeedSnapshot<Status>, projected: List<Status>): Pair<Int, Int> = TODO()
    fun isReadingOlder(position: FeedPosition): Boolean = TODO()
    fun lastVisibleStatus(layout: LazyListLayoutInfo): Int? = TODO()
    val firstStatusListIndex: Int get() = TODO()
}

// MediaGallery.kt: private transient interaction state, reset on page change.
private sealed interface ImageDrag {
    data object Undecided : ImageDrag
    data object Horizontal : ImageDrag
    data object Transform : ImageDrag
    data class Vertical(val displacement: Float) : ImageDrag
}
```

The real signatures may adapt to Kotlin/Compose constraints; `TODO()` marks behavior to fill in, not a second public API. Feed identity and cache key remain untouched. The prefix item contains header, tabs and Activity chips in a non-scrolling `Column`, so one stable prefix offset captures all of them. Status keys must be namespaced separately from prefix/footer. Measure and clamp a restored prefix offset to current layout after size/font-scale changes; when no leading UI exists, ignore the extra field. When the header is visible, snapshot uses neutral status index/offset `0, 0` and records `leadingOffset` separately. This preserves exact header position across route changes and process restart without interpreting header pixels as status pixels. When a status is visible, set `leadingOffset = null` and persist its index within the projected status snapshot and its own offset. Snapshot writing must retain the additive field when it bounds/serializes items; adding it to the data class alone is insufficient. When no statuses exist, preserve the header position independently.

For status restoration, look up the stored status ID among projected rows. If deleted, prefer the next surviving stored ID, then the previous one; use offset zero for a replacement. If none survive, go to prefix top. Translate its projected index to a list index only at `scrollToItem`. For reading-older/deferred arrivals use status-relative position; header visibility means not reading older. Pending reveal scrolls to the first status after the leading item, so new posts are visible without forcing the user through the header. Pagination is based on visible status/footer and status count, never raw mixed-list count; a footer visible below a short page may trigger the next page, whereas a tall header alone must not. Apply this same translation to retry, periodic loads and event handling; one implementation of reading-older avoids drift. Preserve scroll and source behavior when switching filters. About shares header/tabs and has one list with About content; it does not instantiate a pager or an unbounded nested vertical list.

The leading slot hides the coordinate machinery from profile callers while leaving the existing feed API nearly unchanged. Cache ownership remains singular; the new optional field is specific to the profile's leading element, not a navigation framework. There is one additional serialized field and a small private translator. Self actions keep Edit as the weighted primary button, with 48dp QR and Share icon controls alongside; non-self keeps Follow plus QR, Share and existing moderation overflow. All have spoken labels, and URL-dependent controls retain current validation. At narrow width or large font scale, use a full-width primary row with a balanced row of secondary controls; self Favorites and Bookmarks remain their existing distinct actions. No button clips its label or loses its 48dp touch target.

`homeSelection` is shell-owned outside `key(route, tab)` and inside the existing account/instance/authorization key. Selection remains across root tabs and child routes; initial guest selection and existing account resets stay intact. Avoid modifying routes or persisting selection into FeedCache.

Gallery: image gesture arbitration begins undecided and collects one-finger movement until touch slop. Horizontal intent yields without consuming to `HorizontalPager`; vertical intent consumes and updates signed displacement in either direction, rendering image movement interactively and optionally fading its backdrop. Short release/cancel animates to zero; a sufficiently long drag dismisses, with direction-symmetric threshold based on viewport height. Multi-touch before vertical lock or scale > 1 yields to existing image transform; if another pointer arrives mid-dismiss, cancel and snap back before processing the next gesture. Do not intercept controls, native video/audio, or ALT scrolling. Ensure canceled gestures reset, page transitions reset displacement, and animation jobs cannot dismiss a changed page. Existing save state and target capture stay untouched. Keep double-tap zoom, keyboard/accessibility controls and existing close action.

## Alternatives and comparison

1. **Unified `LazyColumn` with translated anchors (selected).** The profile header, tabs, filters, post rows and footer have a single natural vertical owner. The slot is small; callers need not know offsets. Cost: coordinate translation and explicit header restoration in cache, plus ensuring all position consumers use the translator. This is preferable because collapse, fling, large-text sizing, pull-to-refresh, About, and scroll-to-post use standard list behavior.
2. **Coordinated collapsing header with nested scroll.** Retain `FeedScreen`'s status-only list and unchanged indices, but add a measured header container outside it and a `NestedScrollConnection` to consume upward drag/fling until the header is gone, then feed; reverse consumption expands the header only once feed is at top. Profile needs a shared collapse offset, measured height and velocity/fling handling; restore must combine a distinct header position with status cache, guard against content/font changes, and define how About and tab/filter changes coordinate. This hides feed indexing but exposes cross-container motion and restoration policy; nested pre/post scroll, flings, pull-to-refresh, and scroll-to-top all become coupled. It also risks diverging from a single continuous scroll. Reject unless testing demonstrates the prefix design cannot meet performance needs.
3. **Outer `verticalScroll` around existing `FeedScreen`.** Reject: two vertical scroll owners and unconstrained lazy measurement, with no single natural gesture or reliable list viewport.

## Tradeoffs and checks

- Accept one additive cache field for explicit header restoration; old snapshots remain readable and existing status indices retain their meaning. Do not silently repurpose the old fields.
- Accept that a long profile header is one lazy item; profile headers are bounded by account data, but inspect extreme content and accessibility text to confirm responsiveness. Avoid hard clipping.
- Native checks: scroll both directions through profile header into posts; return from a post when header partially visible and when several posts down, including deleted anchor and large text. Change profile tabs/Activity filters and visit About; refresh, generate or receive pending arrivals and paginate a short and a long feed. Check self and other profiles, narrow width, labels/targets, all existing action handlers. Switch Local/Federated, visit a post, return, switch root tab and back, then verify account change resets as before. Drag image up/down past threshold and a short distance; page horizontally, pinch/pan while zoomed, introduce a second finger mid-drag, use video and scroll ALT. Verify existing save success, failure/retry and page-specific target behavior.

## Synthesis decision and next step

This is one independent candidate for the parent to compare; no cross-candidate synthesis or implementation has occurred. First implement and test the private coordinate translation and additive snapshot round trip, then wire the profile leading item and exercise the real UI. Main risk: gesture cancellation and pointer consumption with Compose's pager are platform-sensitive and must be checked on the native simulator.
