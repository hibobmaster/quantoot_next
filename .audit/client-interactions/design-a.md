# Interaction design candidate A

The user needs Profile to scroll into readable posts, compact profile actions, vertical image dismissal, and Home selection that survives child routes. This sketch recommends one profile `LazyColumn`, a private feed viewport adapter, and local gallery gesture ownership. It is based on source at `67d1f79`, the supplied grounding, and the root's native baseline at `a1514d8`. The root reports five intended failures and a passing zoomed-pan and horizontal-paging counterexample. I did not run devices, builds, or network operations.

## Usage, written before the types

Profile supplies its existing content as one leading item. Feed owns status loading, projection, notices, scrolling, and restoration.

```kotlin
FeedScreen(ui, source = accountTimeline) {
	ProfileHeaderAndActions(person, existingHandlers)
	ProfileTabs(selected, onSelect)
	if (selected == Activity) ActivityFilters(existingFilterState)
}
```

The About branch uses one `LazyColumn` containing that same header, tabs, and About fields. It does not place a `FeedScreen` or another vertical scroller inside the list.

Home selection lives above the existing route key in `AppShell`.

```kotlin
var homeTimeline by remember {
	mutableStateOf(if (state.activeSession == null) HomeTimeline.Local else HomeTimeline.Home)
}
// Inside the existing root-tab branch.
HomeScreen(ui, homeTimeline) { homeTimeline = it }
```

Gallery passes its existing dismissal callback only to image content.

```kotlin
GalleryPage(ui, media, image, active, onDismiss = onDismiss)
// The image branch alone installs image gesture arbitration.
GalleryImage(media, url, image, onDismiss)
```

These are illustrative call sites. `existingHandlers` and `existingFilterState` stand for current local callbacks and values, not new public wrapper types.

## Shape and ownership

```kotlin
@Composable
fun FeedScreen(
	ui: UiContext,
	source: Timeline,
	isActive: Boolean = true,
	leadingContent: (@Composable () -> Unit)? = null,
) { error("not implemented") }

private sealed interface FeedAnchor {
	data class Leading(val offsetPx: Int) : FeedAnchor
	data class Status(val index: Int, val offsetPx: Int) : FeedAnchor
}

@Serializable
internal data class FeedLeadingAnchor(val offsetPx: Int)

// Add to FeedSnapshot. Existing items/index/offset remain status coordinates.
val leadingAnchor: FeedLeadingAnchor? = null

private class FeedViewport(
	private val list: LazyListState,
	private val hasLeadingContent: Boolean,
) {
	fun anchor(statusCount: Int): FeedAnchor = error("not implemented")
	fun readingOlder(): Boolean = error("not implemented")
	fun nearEnd(statusCount: Int): Boolean = error("not implemented")
	suspend fun restore(anchor: FeedAnchor) { error("not implemented") }
	suspend fun revealFirstStatus() { error("not implemented") }
}

internal enum class HomeTimeline { Home, Local, Federated }

@Composable
fun HomeScreen(ui: UiContext, selected: HomeTimeline, onSelect: (HomeTimeline) -> Unit) {
	error("not implemented")
}

private enum class ImageGestureOwner { Undecided, Pager, Transform, Dismiss }

private class GalleryDismissState {
	val offsetPx: Float get() = error("not implemented")
	fun dragBy(deltaPx: Float) { error("not implemented") }
	suspend fun finish(velocityPx: Float, viewportHeightPx: Int): Boolean =
		error("not implemented")
	suspend fun cancel() { error("not implemented") }
}
```

`Feeds.kt` owns the adapter and the leading-item layout. Profile never receives a raw prefix count or writes cache positions. With leading content, a single stable-key item contains profile content and feed notices before status rows. Without it, ordinary feeds retain their existing structure. The adapter alone knows whether statuses start at index zero or one. Notification grouping remains outside this change.

`FeedCache.kt` retains `FeedPager`, cache identity, continuation, pending arrivals, and status snapshots. The nullable leading anchor is optional viewport metadata. `FeedPager` does not interpret it. `rememberFeedPager` translates the viewport before writing a snapshot and after restoring projected rows. A profile header offset never enters `FeedSnapshot.offset`. This follows Model the Domain. The distinct anchor cases encode two different coordinate systems.

`Accounts.kt` retains profile loading, filters, relationship writes, dialogs, and all existing action handlers. Remove the 380dp cap and vertical scroll from its header. Remove About's inner vertical scroll. The current selected-tab and filter lifetimes remain unchanged, so this does not promise restoration of a formerly selected Activity or About tab after route removal. Each feed source still restores its own viewport when selected. About has its own ordinary lazy-list state and no status cache.

`QuanTootApp.kt` owns one Home enum outside `key(route, tab)`. It survives both child routes and root-tab switches. The existing account, instance, and authorization key still resets `AppShell`. Home maps the enum to `Timeline`; route stack functions remain untouched. This follows Laziness Protocol by changing the state owner without introducing navigation state storage.

`MediaGallery.kt` owns dismissal displacement and gesture choice. `GalleryImageState` keeps zoom and pan. `GallerySaveState` remains untouched. The callback does not expose route stack operations.

## Feed coordinate and lifecycle rules

The adapter converts every relevant operation, not only cache reads.

* While the leading item is visible, save `leadingAnchor` and canonical status index zero, offset zero. Restore the leading offset only after content measurement and clamp it within that item's current height. Empty feeds can still restore their header.
* While statuses are visible, clear `leadingAnchor`, subtract the leading-item count, and save a status index and offset. Restore by saved status ID after `StatusProjection` runs, then add the leading-item count exactly once.
* If the anchored status was deleted, choose the next surviving saved status, then the preceding survivor. Reset the pixel offset when the identity changes. If none survive, restore the header for a profile and the start for an ordinary feed.
* If cache size limits remove the anchored row, clamp the status index and reset its offset. Do not attach the old row's pixel offset to a different row.
* A legacy snapshot has no leading anchor. Its saved position remains a status position. A cache miss begins at the profile header. This explicit migration may put a previously cached profile directly at its first post once; it never interprets old status pixels as header pixels.
* `readingOlder` is false while the header is visible. In the status region it uses the translated status index and existing 160px threshold. Stream events, retry, foreground refresh, and periodic refresh use the same predicate, plus existing activity rules.
* Pagination compares the last visible status index against `pager.rows.lastIndex`. Header and footer items never count as statuses. Empty and short feeds still request continuation when the terminal content is visible and a continuation exists. Existing busy, ready, exhaustion, and error gates remain.
* Pending reveal calls `pager.reveal()` and scrolls to the first status, not lazy-list index zero. It exposes the new posts without re-expanding the profile. Manual pull-to-refresh remains at the actual start of the unified list.

Keep restoration and cache writes ordered. Do not persist the default list position before cached position restoration completes. Cache validation accepts only nonnegative leading offsets. This follows Boundary Discipline by concentrating serialized-data validation and coordinate conversion at the cache-to-list boundary.

## Profile actions and large text

Use one row with a flexible text primary action, then equal 48dp QR and Share targets. Keep Edit, Follow, Following, and Requested labels, busy state, safe-URL enablement, and the existing callbacks. Give QR a localized accessible name and Share its existing description. Keep the moderation overflow beside these controls when it fits. Self Favorites and Bookmarks remain a secondary row.

Use available-width and measured primary-label width to select the fallback. At large text or narrow widths, put the primary action on its own full-width row and place QR, Share, and applicable overflow together below it. Never shrink targets or truncate the relationship state to force a single row. Reuse the current scalable typography. Header height becomes content-driven, so long bios and fields scroll naturally before posts. The Activity filters keep their horizontal scrolling behavior. Tabs remain in the unified content; sticky tabs are not required by this correction.

## Gallery gesture contract

Replace the image's transform-only pointer loop with one owner decision. Keep double-tap handling and accessible close and zoom controls.

1. Begin undecided and do not consume the initial down. Accumulate single-pointer movement until touch slop. Choose the dominant axis once. Horizontal movement yields to `HorizontalPager` for that gesture. Vertical movement at scale one claims dismissal and consumes subsequent movement.
2. Multiple pointers or scale greater than one select Transform. Cancel any dismissal displacement and use the existing centroid, pan, and zoom calculations. Transform ownership lasts until every pointer lifts, even when a pinch returns to scale one. Adding a finger during dismissal cancels dismissal before transforming.
3. Signed dismissal displacement follows the finger in both directions. Render it as an additional image translation, independent of image pan. The image moves against the existing background throughout the drag. A slight opacity or scale response is optional, not a required extra state.
4. On release, use absolute distance and velocity. A provisional threshold is the lesser of 20% of viewport height and 160dp. A deliberate fling may complete with matching signed velocity and meaningful movement beyond slop. A slow short drag animates to zero. A completed gesture animates toward the matching screen edge, then calls the existing callback once.
5. Cancellation, page changes, loss of the active page, or an external zoom change settle displacement to zero without dismissing. Consume only after a vertical or transform claim. Preserve current horizontal paging when unzoomed and zoomed pan bounds.

Attach this handler only to the image viewport. Video controls, audio, the ALT panel, save feedback, and toolbar targets do not participate. The gallery keeps its copied attachment list, save target capture, retry behavior, and save coroutine lifetime. There is no save-state rewrite.

## Two whole profile designs

The chosen unified-list design has one vertical physics owner and one optional caller slot. It hides mixed-list translation inside feed code. Its cost is explicit leading-anchor metadata and mandatory translation of all index-sensitive operations. Long headers, About content, accessibility text, and dragging across the header-to-post boundary use normal list behavior.

The coordinated-collapse alternative keeps a measured header above a bounded, status-only `FeedScreen`. A parent `NestedScrollConnection` consumes upward motion until the header collapses, then lets the list move. Downward unconsumed motion expands the header only after the status list reaches its start. About supplies a bounded scrollable child through the same connection. Status snapshot indices remain unchanged.

That alternative loses because it needs a second scroll state, fling handoff, pull-refresh ordering, header measurement, and separate collapse restoration. A long header larger than the viewport must remain traversable even when its child receives no visible space. Empty feeds and About must still generate the parent scrolling behavior. It can hide these policies behind `CollapsingProfile`, but the caller must also connect the selected feed's top boundary and restoration lifecycle. Preserving feed indices saves arithmetic while introducing a broader interaction contract.

A larger navigation state holder or general cache framework adds no required capability here. An unbounded nested `LazyColumn` fails both measurement and scroll ownership and is rejected.

## Regression checks and synthesis status

Pure checks cover leading-versus-status anchors, cache round trips, changed header height, deleted anchors, bounded-cache truncation, last-status pagination, and pending reveal. Existing feed continuation, account/source isolation, projection, and gallery save-state tests remain meaningful.

Root native checks should verify that one upward profile swipe moves header content offscreen and increases the post area. Test long bio, empty Posts, About, Activity filters, and large text. Check action alignment, minimum targets, accessible labels, and existing handlers. Reopen a post from a header-visible profile and a later status position and compare the restored anchor. Verify Local and Federated after child-route return and root-tab switching, then account-reset defaults.

Gallery checks need upward and downward interactive motion, successful dismissal in each direction, short-drag snapback, a horizontal-dominant diagonal, two-finger cancellation, double tap, and zoomed pan. Drag the ALT panel and video controls to prove they retain their gestures. Repeat the existing save-success, retry, and page-change checks.

This is candidate A, awaiting parent synthesis. No implementation deviations exist. The first implementation unit is the private viewport translation and explicit leading-anchor storage, with behavioral checks before moving Profile into the list. The native runner must settle threshold tuning and Compose gesture arbitration; those are verification tasks, not user preference questions.
