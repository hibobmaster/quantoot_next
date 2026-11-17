# Profile and Settings navigation candidate B

## Usage (caller's view)

The Profile caller supplies its existing profile header and About content. `ProfileTimeline` owns selection, the one visible list, and source transitions. The header receives the current selection and one callback. It does not read caches or coordinate scroll restoration.

```kotlin
// Accounts.kt, inside ProfileScreen after the account loads
ProfileTimeline(
    ui = ui,
    accountId = id,
    leading = { selection, select ->
        ProfileHeader(person, relationship, selection, select, /* existing actions */)
    },
    about = { ProfileAbout(person) },
)

// Within ProfileHeader. Keep Activity filters after the tabs in this same item.
Tab(selection.tab == ProfileTab.Media,
    onClick = { select(selection.copy(tab = ProfileTab.Media)) },
    text = { Text(tr("mastodon_media")) })
FilterChip(selection.showReplies,
    onClick = { select(selection.copy(showReplies = !selection.showReplies)) },
    label = { Text(tr("mastodon_filter_show_replies")) })

// Settings index and shell use the same route value.
SettingsRow(SettingsPage.Server) { ui.navigate(Screen.Settings(SettingsPage.Server)) }
// AppShell
is Screen.Settings -> SettingsScreen(ui, state, route.page)
// Its TopAppBar title is tr(route.page?.titleKey ?: "settings_title").
// ui.requestBack() continues to pop one route after the composer guard.
```

The existing app-bar Settings action opens `Screen.Settings()` and each Settings index row opens its corresponding `Screen.Settings(page)`. Back from the child pops to the index. The visible title is derived from the current route on every composition, so changing locale changes the title without a mutable title variable.

## Problem

The source change currently recreates both Profile's `LazyListState` and its pager. An independently restored viewport can move the common header. An empty body also cannot support the old offset. Root's second native case confirms the latter independently: an upward drag on empty Media left the tab row at Y458. Settings stores its section in `SettingsScreen`, while AppShell owns the title and back action. The design must preserve existing status-coordinate snapshots, cache identity, pager and projection ownership, source meanings, Home retention, one continuous vertical gesture, and accessible full-height posts.

## Whole Profile forms considered

### A. One lazy viewport and a bounded footer

Keep a single `LazyColumn` and `LazyListState` for one visible Profile. The leading item contains the profile, the tabs, and the Activity filters below the tabs, in their existing order. Source-specific pending and error notices also go below the tabs within that item. The active source supplies status items and one footer. About supplies one body item. A target source can replace its pager without replacing the viewport. All reading, flings, refresh, and accessibility scrolling remain ordinary list behavior.

Guarantee that the body beneath the leading item is at least the actual viewport height. The footer provides any necessary minimum height. This guarantees enough scroll range to hide the header even when the source is empty. Details and a proof follow below. This form uses the existing `FeedPosition` mapping and old snapshots without a storage migration. It accepts some extra footer space in sparse feeds to make the guarantee independent of lazy item measurement.

### B. Coordinated collapsing header and body list

Place a measured, uncapped header and a single body `LazyColumn` under one scroll coordinator. For a viewport of height `V`, header height `H`, and collapse `c` in `[0,H]`, the body starts at `H-c`. An upward gesture first increases `c` to `H`, then scrolls the body. A downward gesture first returns the body to its top, then decreases `c`. A custom parent connection and gesture handling for the header must hand off drag deltas, fling velocity, overscroll, pull-to-refresh, keyboard and accessibility scroll. There is no vertical list inside another list, and the body has the whole viewport after collapse.

This form has enough empty-body range without a footer: `c` can advance to `H` even when the body has no rows. A category change keeps `c` and resets the body to the target's first row. Dynamic header remeasurement must preserve the tab's screen position, not blindly reuse an old collapse amount. For old snapshots, `leadingOffset` becomes collapse within the measured old-compatible leading header, while `index` and `offset` restore the body status after setting `c=H`. When taking a new snapshot, a partial collapse writes only `leadingOffset`; a body position writes only status `index/offset`. Both source changes and first restoration need cancellation and readiness rules identical to the recommended form.

This is viable in principle, but it exposes more state to the scroll coordinator and duplicates behavior already supplied by `LazyColumn`, particularly fling handoff, pull-to-refresh, and accessibility. It is not the old 380dp capped header. It loses here because the footer guarantee lets one native scroll owner meet the requirements with a smaller behavioral interface.

## Shape

Recommend A. Put the Profile-specific timeline beside the existing feed code, sharing the active status-pager lifecycle with `FeedScreen`. The caller's public internal interface only describes Profile content; no caller passes a restore-stage flag.

```kotlin
internal enum class ProfileTab { Posts, Media, Featured, Activity, About }

internal data class ProfileSelection(
    val tab: ProfileTab = ProfileTab.Posts,
    val showReplies: Boolean = false,
    val showBoosts: Boolean = true,
)

@Composable
internal fun ProfileTimeline(
    ui: UiContext,
    accountId: String,
    leading: @Composable (ProfileSelection, (ProfileSelection) -> Unit) -> Unit,
    about: @Composable () -> Unit,
) { error("not implemented") }

// Private to feed/Profile implementation, not supplied by ProfileScreen.
private fun ProfileSelection.source(accountId: String): Timeline.AccountPosts? = when (tab) {
    ProfileTab.Posts -> Timeline.AccountPosts(accountId, excludeReplies = true, excludeReblogs = true)
    ProfileTab.Media -> Timeline.AccountPosts(accountId, onlyMedia = true)
    ProfileTab.Featured -> Timeline.AccountPosts(accountId, pinned = true)
    ProfileTab.Activity -> Timeline.AccountPosts(
        accountId, excludeReplies = !showReplies, excludeReblogs = !showBoosts,
    )
    ProfileTab.About -> null
}

// Move the current FeedViewport operations behind this internal contract so the
// same pager lifecycle can use normal and Profile-specific restoration policies.
internal interface PagerViewport {
    fun readingOlder(): Boolean
    fun nearEnd(rowCount: Int): Boolean
    fun <T> snapshot(pager: FeedPager<T>, id: (T) -> String): FeedSnapshot<T>
    suspend fun <T> restore(saved: FeedSnapshot<T>?, rows: List<T>, id: (T) -> String)
    suspend fun revealFirstStatus()
}

// Internal cache access, scoped to the existing UiContext/account lifetime.
// Read and enqueue are serialized in FIFO order for the complete existing cache key.
internal interface FeedCacheAccess {
    suspend fun <T> read(key: String, cache: FeedCache<T>): FeedSnapshot<T>?
    fun <T> enqueue(key: String, cache: FeedCache<T>, snapshot: FeedSnapshot<T>)
}

internal enum class SettingsPage(val titleKey: String, val requiresAccount: Boolean) {
    Privacy("mastodon_settings_privacy", true),
    Defaults("mastodon_settings_posting_defaults", true),
    Filters("mastodon_settings_filters", true),
    Notifications("mastodon_settings_notifications", true),
    Server("mastodon_settings_about_server", false),
    PrivacyPolicy("settings_privacy_policy", false),
    Licenses("settings_open_source_licenses", false),
}

// Foundation.kt: replace the data object.
data class Settings(val page: SettingsPage? = null) : Screen

@Composable
fun SettingsScreen(ui: UiContext, state: SessionState, page: SettingsPage?) {
    error("not implemented")
}
```

`SettingsPage` and the route must have compatible Kotlin visibility. Make them both internal if all `Screen` consumers are internal, or make the enum public with the existing public `Screen`. The enum's `titleKey` is the single source for both the index row label and app-bar title. Preserve index row order, including the version block before Licenses. `requiresAccount` drives row visibility and any direct-route guard; the account/instance shell key still resets routes on account changes.

Model the domain sets one typed Profile selection and one Settings route identity, rather than synchronizing a title with a string section. Boundary discipline leaves cache validation in `FeedCache` and translation of wire snapshots inside the viewport. The design's small caller interface hides source construction, scroll range, pager activation, and restore ordering. The new cache sequencer is storage coordination, not a second pager or a retained hidden feed. The laziness and reader-load principles keep one list and share the current feed lifecycle rather than duplicate it.

### Scroll geometry and short bodies

Measure `V`, the actual `LazyColumn` viewport height in pixels after shell, error, and refresh layouts. Do not use device screen height or a fixed dp guess. For a feed, wrap each status item in an actual `heightIn(min = 48.dp)` constraint. Convert that minimum using the current density and rounding used by layout. Let `m` be that minimum in pixels, and `n` the number of currently rendered statuses. Set the existing footer's minimum height to

```text
footerMinPx = max(0, V - n * m)
```

Round the conversion to dp upward so that the laid-out footer is never smaller than `footerMinPx`. Do not count unmeasured card heights, footer contents, spacing, or the existing 88dp bottom padding toward this bound. They are extra nonnegative height. If the footer is taller because it contains Loading, Empty, Retry, or pagination, keep its natural height. About is one regular body item with `heightIn(min = V)` and natural height above that. No scrolling child is introduced.

Let `H` be the measured leading height and `B` the actual height after it. The status wrappers and footer guarantee `B >= n*m + max(0, V-n*m) >= V`; About guarantees the same directly. The total extent is therefore at least `H + V`, so the maximum scroll is at least `H`. Every leading offset from zero through its last pixel is attainable, including when no statuses exist. Thus empty Media can scroll its header away and a visible tab at any leading offset remains attainable when loading becomes empty, a page arrives, rows disappear, or a new source is selected. A loading spinner and zero rows already get a full-height footer before the next frame; there is no post-layout fix-up window.

With a tab visible, `firstVisibleItemIndex == 0` because the tabs are in the leading item. The selected tab is above Activity's conditional filter chips. The current `firstVisibleItemScrollOffset` and the stable leading key therefore keep the tab row's screen Y unchanged when Activity adds or removes the chips. The same holds for other source changes, including About. In Profile, move the source's pending-arrivals button and retry/error notice from the separate `Column` above `PullToRefreshBox` into the leading item after the tabs and filters. Otherwise a source's banner can shift the whole list even with a stable list state. If an already-loaded profile account refresh fails, put its retry notice there as well; preserve the full-screen loading or error presentation before the account first loads. These additions do not change the first status index, and pending reveal still targets the first status. Measure the header naturally each time. Large text, a changing note, relationship actions, or a differently sized media card never rely on cached header or body heights. Status body changes cannot clamp a visible header because of the bound. A change to the profile text or font can itself change the tab's local Y; that is distinct from a tab selection. If a measured header reflow occurs during a pending selection, capture the old tab Y and adjust the leading offset by the measured tab-local-Y delta when the desired offset remains nonnegative. If the new header is shorter than the old screen Y, clamp at the natural top because a negative scroll is impossible; do not add an invisible persistent header gap or reinterpret its pixels as status pixels.

The minimum is conservative. One large status in a sparse feed may be followed by substantial blank space because its actual height is not counted. This is a visible tradeoff, bounded to one viewport of footer reserve. It buys a synchronous guarantee with no stale `onSizeChanged` measurements, no eager composition of all statuses, and no dependency on card contents. If product verification finds that space unacceptable, a same-layout-pass exact body measurement or the coordinated form is a design change, not an asynchronous measurement patch that can briefly clamp the list.

### Transition, readiness, and cache pseudocode

The visible `LazyListState`, leading item key, and selection belong to one `ProfileTimeline` keyed by Profile identity, not by tab, filters, or source. Each actual source still has one keyed pager session. The normal source is recomputed exactly as in `Accounts.kt`. Selecting another tab with the same effective `Timeline.AccountPosts` changes selection and filter presentation but may keep the same pager. About owns no pager. Rows, requests, pending arrivals, cursor, generation, and projection continue to belong to the active `FeedPager` and `StatusProjection`.

```text
first Profile composition:
    selection = Posts with existing default filter values
    create one list state and one Profile viewport controller
    open Posts session under the existing instance/account/source cache key
    keep the list laid out but hidden and input-disabled while boot restoration runs
    saved = cacheAccess.read(key, cache)            // ordered after earlier writes
    pager.restore(saved)                          // projects rows and cursor as today
    wait for leading item to be measured; do not wait for offscreen rows
    viewport.restoreInitial(saved, pager.rows)    // existing FeedPosition rules
    await the resulting list layout
    expose list; start persistence, refresh, stream, pagination

select(next):                                    // called synchronously by header
    if next == selection: return
    if next.source(accountId) == selection.source(accountId):
        selection = next                        // retain the one active pager
        return
    old = active session
    freeze old writes and cancel its pending debounce before changing selection
    if old is ready: enqueue an immutable snapshot of old rows and current viewport
    mark old closed; cancel old network and collection jobs
    selection = next                             // stable list and leading key survive
    create target session only if it is a feed and its effective source changed
    render target loading footer with its minimum in the same composition
    read and restore target rows/cursor; do not apply target saved scroll position
    enable target persistence and loading only after its rows are restored

on route disposal:
    close active session once, enqueue its current ready snapshot, cancel feed jobs
    retain no active pager for the hidden route
```

`ProfileViewport` privately distinguishes its one initial restoration from later source activations. For the latter its `restore` is deliberately a no-op for scroll position, but `pager.restore(saved)` still restores that source's projected rows and cursor. Do not expose that choice as a boolean to `ProfileScreen` or `FeedScreen` callers. While the first restore waits, a loading overlay can cover the laid-out list; hide its semantics and pointer input until restoration is complete. Afterwards the user can scroll during an asynchronous target cache read. The completion must not move a visible leading item, even if the user has scrolled in the meantime. If the user has already scrolled past the header into an empty loading footer and the first statuses arrive, anchor the next layout to the first status, not the old footer key; do not strand the user below the newly inserted posts. If a new drag starts before that adjustment, the user's gesture wins.

Every activation has an increasing private generation. Before applying any asynchronous read, row restoration, layout callback, write, or load, check that the session is still current. A new selection closes the old session before mutating selection, and the keyed composition cancels its effects. A completed old result is discarded. When the same effective source remains active, do not create a second writer or cancel its pending arrivals. Do not let an obsolete `LaunchedEffect` restore an older cached position after a rapid A to B to A sequence.

The existing 400ms debounce is retained during ordinary interaction, but selection and disposal capture a final snapshot synchronously before the list is reused. `FeedCacheAccess` enqueues immutable snapshots on the existing account-lifetime `UiContext` scope. The generic pager already calculates the complete cache key alongside its `FeedCache`; pass that same key to the sequencer rather than trying to inspect the cache's private field. For each complete existing `feedCacheKey(instance, account, source)`, process reads and writes in enqueue order. A new read waits behind an already enqueued final write for that key. Close a session idempotently, prevent it from enqueuing after close, and complete or ignore the response to a canceled read without canceling an in-progress storage write. This is necessary because an Android preferences commit can already be underway when its coroutine is canceled. Different keys need no shared ordering. Empty idle queues may be removed; there are no retained network jobs. On account-lifetime teardown, unfinished cache writes remain best effort, as they are today. Do not wipe old keys.

Initial restoration uses the existing `FeedSnapshot.restoredPosition` unchanged: a non-null `leadingOffset` is a leading pixel offset, clamped to the newly measured leading height; otherwise `items[index]` identifies the status and `offset` is that status's pixel offset. Projection keeps the same ID or chooses the existing next/previous surviving neighbor with zero offset. If no row survives, restore the leading item at zero. Cache truncation still resets invalid status offsets and keeps separate leading metadata. Activity's filter remains inside the leading item, so an old leading offset keeps its old coordinate system.

New snapshots likewise call `atPosition(Leading(offset))` only while the leading item is visible, and `atPosition(Status(index, offset))` only for a real status item. If the first visible item is the footer after an empty body, record `Leading(max(0, measuredLeadingHeight - 1))`. If it is a footer after real rows, record the last status with offset zero. Never record spinner or reserved-footer pixels as a status offset. For deferred arrivals, the empty footer means `readingOlder = false`, while a footer after real statuses means `readingOlder = true`; do not infer that decision from a synthetic status offset. The Profile viewport can distinguish those cases from the keyed first item and the actual displayed status count. Repeated writes of the same position and repeated close calls converge to the same snapshot. Keep the existing bounding, cursor, and cache-key logic. Normal `FeedScreen` and notifications retain their present no-leading mapping and activation behavior.

When a child route replaces Profile, the active session is disposed and flushed as above. On return, the existing Profile behavior starts at Posts and restores that source's saved header or status position. There is no promise of retaining the selected category across route disposal. Home timeline selection remains in AppShell and its existing feed does not stay active while hidden.

### Settings route behavior and migration

Replace the local nullable string and inner `‹ Settings` control. AppShell's route list becomes the sole owner of section identity. The index navigates to one typed child route, and the app bar derives its localized title from `page.titleKey`. The existing toolbar and platform back both call `ui.requestBack()`: from a child they pop to index, from index they pop to its caller, and from Accounts opened by a child they pop back to that child. The existing composer `beforeBack` guard and its disposal are unchanged. A locale change recomposes `tr` without resetting routes because locale is not part of the existing shell key.

Authenticated rows remain Privacy, Defaults, Filters, and Notifications. Server, Privacy Policy, and Licenses remain available to guests. All child data loads and save handlers remain in their current functions. Remove the Licenses child `titleLarge` text because the app bar now names it. Remove the redundant `QuanToot Privacy Policy` child heading; keep the policy text, date, and section headings. Keep the server's actual name and its About, Rules, and Contact content because they identify the server and its content rather than duplicate the page title. Hide the redundant Settings toolbar action while a Settings route is active, so the index and child do not stack another Settings index on top of themselves. Retain Accounts in the toolbar.

Migrate the only app-bar `Screen.Settings` construction to `Screen.Settings()`, every Settings index row to its typed child route, both AppShell `when` branches to `is Screen.Settings`, and `SettingsScreen` to the route's page argument in the same unit. Delete `section`, `LaunchedEffect(section)`, the back label, and the old string dispatch. Each new keyed route creates its own scroll state at zero. Keep `navigateTo`'s generic route behavior and `beforeBack`; no mutable global title is introduced.

### Required behavioral checks

- Run root's three native cases: stable row across all five tabs, scrolling empty Media, and one Settings title/back. Repeat with Activity chips toggled and at large text size.
- Exercise short, empty, loading-to-empty, loading-to-ready, very tall status media, long About text, header reflow, and repeated fast tab changes. Confirm tabs remain horizontally reachable and the footer reserve does not cover a post or intercept a gesture.
- Verify Posts and Activity when their filter values yield the same source, then different source values. Check pending reveal scrolls to the first status, pagination uses the real status/footer indices, and source jobs stop when hidden.
- Restore a legacy status snapshot, a separate leading snapshot, deleted-anchor fallback, short/empty profiles, cache truncation, and immediate A to B to A. Delay a cache write while reentering the same source and verify the newer snapshot wins. Return from Accounts or another child after both a leading and a status position.
- Check all seven Settings children, index-to-caller back, Accounts from a child and back, guest visibility, locale change, and a dirty composer back action. Confirm existing data and save calls still work. Check retained Home source after root-tab and child navigation.

## Synthesis decision

Candidate B recommends the single lazy viewport. The parent architect owns cross-candidate synthesis. The coordinated header remains a whole-shape fallback if the footer-space tradeoff fails native review.

## Tradeoffs accepted

- Accept up to nearly one viewport of conservative trailing space in sparse feeds in exchange for a same-frame, measurement-independent scroll-range guarantee.
- Accept one internal per-key cache operation queue in exchange for deterministic rapid switch and immediate route-return restoration without hidden network jobs.
- Accept a loading overlay during the first cache and layout restoration in exchange for avoiding a visible initial scroll jump or user input being overwritten.
- Accept the existing reset to Posts after Profile route disposal in exchange for preserving the current route lifetime and avoiding an unrelated navigation-retention change.

## Alternatives considered

- Coordinated header plus body list has a small content-facing API and an exact short-body solution, but hides a larger gesture, fling, refresh, accessibility, and dual-position protocol. Its body viewport also needs the same cache ownership and cancellation protections.
- Keeping independently restored lists and copying a header offset has a superficially small API, but every caller must coordinate source lifetimes, restoration order, and attainable maximum scroll. It still needs a body-height guarantee and offers two owners for a visual invariant.
- Measuring visible cards asynchronously and subtracting their recorded heights reduces blank space, but a card that shrinks or changes width can make those recorded sizes stale for a layout pass. The list can clamp before the filler grows.

## Red-flag screen

One Profile timeline owns selection and its one viewport. One source session owns the active pager. One existing cache key has one FIFO storage writer. No caller imports snapshot internals or coordinates restore stages. No alternative Profile viewport remains after migrating About and feed tabs. Shared feed behavior is extracted once and is still used by Home, route feeds, and Profile; notification grouping keeps its current adapter. Settings title, section, and back derive from the route; its single page enum supplies row and toolbar title keys. These checks reject split ownership, hand-synced title lists, pass-through wrappers, and parallel legacy paths. The helper interface for viewport operations earns its size by hiding two different restoration policies behind the existing pager lifecycle; keep it private or internal to the feed module.

## Implementation reconciliation

No implementation has been made. Record accepted deviations here and update this contract before a later worker relies on changed ownership or outcome semantics.

## Open questions and risks

- Does the bounded blank footer in one- or two-post feeds pass visual review on compact phones and large tablets? If not, choose a synchronous exact layout or coordinated header before shipping rather than weakening the range invariant.
- Can the Compose version keep the laid-out boot list hidden from semantics and input without affecting its measurement? Verify on both native targets.
- Can every existing feed-cache caller that can address a Profile key use the same per-key sequencer? If not, the queue cannot claim ordered writes and the ownership must be revised.

## Next implementation step

Extract the shared active status-pager lifecycle and give Profile one stable leading-keyed `LazyColumn`, then implement and verify the footer bound before wiring Settings routes.
