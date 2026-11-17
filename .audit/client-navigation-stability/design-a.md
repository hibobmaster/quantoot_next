# Profile geometry and Settings navigation, candidate A

## Problem

Profile category switches must preserve the visible header and category row. Settings children need one page title and one standard back action. The existing Profile uses a separate source-restored list for each category, while About has another list. An empty body also limits how far the leading header can scroll. The root measured a category-row jump from Y373 to Y458 and separately found that an upward drag within empty Media left the row at Y458. Settings keeps its subsection inside `SettingsScreen`, so the app bar cannot title or pop that subsection.

This package is a design against product baseline `b9d72fa`. The later `7f9f85f` adds native evidence without changing product code. I read the root's brief, grounding, history, lineage, failing native tests, both result files, and all three baseline screenshots. No product files, builds, native sessions, Git state, or external applications were changed.

## Usage, caller's view

The consumer contract is that `ProfileTimeline` owns category selection and vertical presentation for one person. The profile's existing account, relationship, action, and dialog handlers stay in `ProfileScreen`. It supplies ordinary non-scrollable header and About content. No caller chooses whether a cache restore is allowed or supplies a scroll offset.

```kotlin
// Accounts.kt, inside ProfileScreen after the account has loaded.
ProfileTimeline(
	ui = ui,
	person = person,
	header = { ProfileHeader(ui, person, relationship, existingActionHandlers) },
	about = { ProfileAbout(ui, person) },
)
```

`ProfileHeader` and `ProfileAbout` above mean the existing code extracted into private composables. The sketch does not introduce an `existingActionHandlers` bag. Keep the current explicit callbacks, or retain these bodies inline if extraction adds more parameters than it removes.

Ordinary timelines keep their current calling contract. Home selection remains owned by AppShell.

```kotlin
// Feeds.kt and QuanTootApp.kt retain these ordinary callers.
FeedScreen(ui, selection.source)
FeedScreen(ui, route.source)

// Settings index row, using the section's canonical localized title key.
SettingsRow(tr(SettingsSection.Server.titleKey), Icons.Default.Info) {
	ui.navigate(Screen.Settings(SettingsSection.Server))
}
```

The shell derives a Settings title and body from the same route value. Existing `ui.requestBack()` remains the only app-bar and platform back entry point.

```kotlin
// QuanTootApp.kt.
is Screen.Settings -> Text(tr(screen.section?.titleKey ?: "settings_title"))
is Screen.Settings -> SettingsScreen(ui, state, screen.section)

// The toolbar opens the index. Its Settings button is absent on Settings routes.
ui.navigate(Screen.Settings())
```

## Two whole Profile forms

### Form 1: one shared lazy viewport with a minimum body extent

Keep the entire header, tabs, and selected body in one `LazyColumn`. Move its state outside all category and source keys. Replace only the active pager. About contributes items to that same list. Capture the common leading-item offset before changing selection, and let a source cache restore rows while the transition retains that leading position.

This form still needs a geometric floor. Let `L` be the leading-item height, `V` the list viewport height, and `B` the selected body's total height after the leading item, including padding. To permit every leading offset `s` through `L`, the list must satisfy `L + B - V >= L`, hence `B >= V`. A header-height clamp alone does not establish that condition.

There are two concrete ways to supply the floor. A viewport-height trailing spacer is straightforward and always sufficient. It also allows a full blank page after long timelines. A custom lazy measurement policy can instead append `max(0, V - B)` after measuring the complete short body. Such a policy must compute the floor in the same measure pass. Summing `onSizeChanged` values in composition is insufficient because a deleted row or shrinking media can make the remembered height stale for one frame, allowing a clamp before compensation.

This form has the smallest gesture API. It retains native LazyColumn flings, focus scrolling, and nonsticky tabs. Its cost is either a persistent blank tail or custom lazy measurement machinery. A guessed height, resetting only the leading key, or a post-layout `scrollToItem` correction is not a viable version of this form. Old snapshots are easy to interpret because the logical leading item remains intact. Source switches must still suppress incoming cached positions.

### Form 2: one scroll owner, a collapsing header, and one lazy body

Measure the complete header naturally. Place the category row immediately below it. The parent owns the header's collapsed distance and the single vertical gesture. The selected body is the only lazy list. The row reaches the top of the Profile viewport and stays there after the header scrolls away.

The body has a constant measured height equal to the viewport minus the tab row. It is placed below the current tab position and clipped by the outer viewport. As the header collapses, the body moves upward into view. Empty and loading bodies still occupy that measured viewport. Their own maximum scroll can be zero without limiting header travel.

The outer controller routes each scroll delta through header travel and then body travel, with the inverse order on the way back. The body does not run a second gesture recognizer or independent fling. This is one vertical interaction with one lazy list, not a nested pair of vertically scrolling lists. A long header can exceed the screen because its natural height defines its travel. There is no header cap.

This form hides more geometry behind a small component API, but the controller must own fling cancellation, accessibility scrolling, and refresh ordering. Snapshot translation must account for the formerly combined leading row. The geometry proof is independent of lazy measurement of status cards.

## Shape

Recommend form 2. The independently reproduced empty-Media drag failure makes body-independent header travel valuable enough to accept a dedicated Profile controller. Pinned tabs are a deliberate change to the earlier bounded nonsticky choice. They keep every category reachable after the header scrolls away.

The selected body starts at its beginning on every category or Activity-filter change. The current collapsed header distance stays unchanged. This gives tab selection one predictable meaning and avoids reviving a previous category's deep position while part of the common header is still visible. Returning from a child route restores the current category, filters, and reading position. A first Profile visit uses its selected source's existing cache position. A process restart still begins on Posts because there is no new durable Profile selection schema.

### Data shape and signatures

Only `ProfileTimeline` is used by `Accounts.kt`. The presentation registry holds small passive state within the existing `UiContext` account lifetime. It holds no pager, jobs, row membership, cursors, or `StatusProjection` copy. A profile ID identifies the presentation because revisiting the same person in one account session should resume the same selected category. Entries can be evicted when their profile is no longer in the route stack, except the signed-in root Profile entry.

```kotlin
internal enum class ProfileCategory { Posts, Media, Featured, Activity, About }

internal data class ActivityFilter(
	val showReplies: Boolean = false,
	val showBoosts: Boolean = true,
)

private data class ProfileSelection(
	val category: ProfileCategory,
	val activity: ActivityFilter,
) {
	fun source(profileId: String): Timeline.AccountPosts? = error("not implemented")
}

private sealed interface ProfileLocation {
	// The collapse is either a pixel distance in the current header or its end.
	data class Header(val collapsedPx: Int) : ProfileLocation
	data class Body(val anchor: BodyAnchor) : ProfileLocation
}

private sealed interface BodyAnchor {
	data object Start : BodyAnchor
	data class Status(val id: String, val offsetPx: Int) : BodyAnchor
	data class ActivityControls(val offsetPx: Int) : BodyAnchor
	data class About(val offsetPx: Int) : BodyAnchor
}

internal class ProfilePresentations {
	// Implemented in ProfileTimeline.kt. The returned value is not a data owner.
	internal fun forPerson(id: String): ProfilePresentation = error("not implemented")
	internal fun retain(profileIds: Set<String>) = error("not implemented")
}

internal class ProfilePresentation internal constructor() {
	// Selection and saved presentation are readable only by ProfileTimeline.kt helpers.
	// These are not parameters exposed to ProfileScreen.
}

@Composable
internal fun ProfileTimeline(
	ui: UiContext,
	person: Account,
	header: @Composable () -> Unit,
	about: @Composable () -> Unit,
) { error("not implemented") }

// Feeds.kt, one adapter contract for the existing generic pager lifecycle.
internal interface FeedViewport {
	val emptyPosition: FeedPosition
	fun position(): FeedPosition
	fun nearEnd(rowCount: Int): Boolean
	suspend fun restore(position: FeedPosition)
	suspend fun revealFirstStatus()
}

@Composable
internal fun rememberStatusPager(
	ui: UiContext,
	source: Timeline,
	viewport: FeedViewport,
	isActive: Boolean = true,
): FeedPager<Status> = error("not implemented")

enum class SettingsSection(val titleKey: String, val requiresAccount: Boolean) {
	Privacy("mastodon_settings_privacy", true),
	PostingDefaults("mastodon_settings_posting_defaults", true),
	Filters("mastodon_settings_filters", true),
	Notifications("mastodon_settings_notifications", true),
	Server("mastodon_settings_about_server", false),
	PrivacyPolicy("settings_privacy_policy", false),
	Licenses("settings_open_source_licenses", false),
}

// Replace the old object inside Screen. A null section is the Settings index.
data class Settings(val section: SettingsSection? = null) : Screen

@Composable
fun SettingsScreen(ui: UiContext, state: SessionState, section: SettingsSection?) {
	error("not implemented")
}
```

`SettingsSection` has the same public visibility as the existing `Screen` route. Profile's geometry types stay private to its implementation. They do not become parameters on `ProfileScreen`.

The current `rememberFeedPager` becomes the one owner of translating `FeedSnapshot` into the adapter's domain `FeedPosition`. Its `readingOlder` decision is `viewport.position().readingOlder()`. Its snapshot construction retains the current status-identity remap between displayed rows and `pager.snapshot()` rows, including pending arrivals. `FeedSnapshot` is not added to the new viewport interface.

`rememberStatusPager` extracts the existing status fetch, projection, stream, local-write, and Home mark-read code from `FeedScreen`. `FeedScreen` and `ProfileTimeline` both call it. It returns the existing pager instead of introducing another row store. Notifications keep the same generic pager lifecycle and use the ordinary list adapter. The interface hides restoration geometry while exposing only the existing lifecycle capabilities.

This applies Model the Domain by separating category selection, header location, and status location. Boundary Discipline keeps persisted coordinates at the cache adapter. Laziness Protocol keeps the existing pager and source semantics instead of creating a second Profile data layer. Exhaust the Design Space produced the two whole forms above.

### Module ownership and caller migration

| Module | Final responsibility and migration |
| --- | --- |
| `Accounts.kt` | Keep account and relationship calls, action handlers, dialogs, and header/About content. Delete the integer selection and outer category/filter `key`, local About `LazyColumn`, and `leadingContent` feed call. Call `ProfileTimeline` once. |
| New `ProfileTimeline.kt` | Own category/filter state, naturally measured header and tabs, the one motion controller, selected body, passive route-return location, and the Profile `FeedViewport` adapter. Keep source mapping here. |
| `Feeds.kt` | Extract the existing status-pager lifecycle. Convert its existing list viewport class to an ordinary implementation of `FeedViewport`. Remove `leadingContent` from public `FeedScreen`, since Profile was its only caller. Keep ordinary feed rendering and Home selection behavior. |
| `FeedPosition.kt` | Preserve status coordinate recovery and identity fallback. Add small pure Profile leading-offset conversion functions only if they earn tests. No new persisted coordinate system. |
| `FeedCache.kt` | Continue owning snapshot validation, size bounds, serialization, and cache identity. Add the immediate in-memory checkpoint and ordered disk-write behavior described below behind the existing cache boundary. |
| `Foundation.kt` | Replace object `Screen.Settings` with the typed route. Give `UiContext` an account-lifetime Profile presentation registry and the typed cache-session owners. Do not alter `beforeBack` or composer behavior. |
| `QuanTootApp.kt` | Update both exhaustive Settings branches and the toolbar call. Derive the title from the route and current locale. Keep `homeTimeline` outside the content key. Retain passive Profile state across child routes and prune it when its profile leaves the route stack. |
| `Settings.kt` | Receive the route's section. Replace each string assignment with navigation to the typed section. Delete local section state, the inner Settings return button, and repeated top-level body titles. Keep child calls and write handlers. |

The root Profile entry must always remain in the registry while the account is active. The route retention set is the signed-in account ID plus every `Screen.Profile.id` still in `routes`. This list is derived from the existing route stack. It is not a second navigation stack.

## Geometry and continuous motion

Use pixels throughout the controller. Let `V` be the available Profile height below the app bar, `H` the naturally measured header height before the category row, `T` the category-row height, and `c` the collapsed header distance with `0 <= c <= H`.

```text
header y       = -c
category y     = H - c
body y         = H - c + T
body height    = max(0, V - T)
outer clip     = [0, V]
```

The body height does not shrink with the visible header. Its offscreen portion is clipped until the header moves away. Once `c == H`, it fills the useful reading area below the tabs. The header may be taller than `V`; it still has the full `H` travel. A zero or one-pixel temporary body when `T >= V` is an extreme text/layout boundary, not a reason to cap the header. In that case the tab row must wrap or constrain its own text so at least one content line remains available before release.

For a category change with unchanged `H` and `T`, retain `c`. Then both header y and category y are algebraically unchanged. `B`, the selected body's content height, does not appear in either equation. A source switch from 30 posts to empty Media, a loading-to-empty change, and a loading-to-ready change therefore cannot move the row through list max-scroll clamping.

For an upward drag of `d` pixels while the empty body has zero body scroll range, the controller consumes `min(d, H - c)` into the header. The category row moves upward by that amount. This directly addresses the added native counterexample. At full collapse the header can move no farther and the empty body remains below pinned tabs.

### Controller pseudocode

Use one parent `ScrollableState` with normal fling handling. The body LazyColumn has `userScrollEnabled = false`. The controller acquires the body's `scroll` scope while it owns a gesture, so the body cannot race an independent animation. Its own transition, restore, reveal, and accessibility operations acquire the same controller mutation lock.

```kotlin
// Positive delta means forward reading, the finger moves upward.
fun consume(delta: Float, bodyScroll: (Float) -> Float): Float {
	if (delta >= 0f) {
		val header = min(delta, headerHeight - collapsed)
		collapsed += header
		return header + bodyScroll(delta - header)
	}
	val body = bodyScroll(delta)
	val remaining = delta - body
	val header = max(remaining, -collapsed)
	collapsed += header
	return body + header
}

suspend fun scroll(priority: MutatePriority, block: suspend ScrollScope.() -> Unit) {
	motionMutex.mutate(priority) {
		val attachedBody = currentBody
		attachedBody.list.scroll(priority) {
			val bodyScope = this
			block(ScrollScope { delta -> consume(delta, bodyScope::scrollBy) })
		}
	}
}
```

The sketch's `ScrollScope` construction is pseudocode for an object implementation. It is not a claim that the interface has a lambda constructor. Do not implement gestures by launching one coroutine per delta. The normal path uses the body's `scroll` scope, not unrestricted `dispatchRawDelta` calls. The latter explicitly bypasses priority, cancellation, and nested-scroll behavior. [Compose LazyListState contract](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListState).

Use the installed Compose `scrollableArea` building block if available in the pinned multiplatform dependency. It supplies the natural delta direction, clipping, and overscroll plumbing, while this layout supplies placement. Otherwise its ordinary `scrollable` equivalent needs the same sign and clip explicitly. This is a private implementation choice, not a caller option. [Compose scrollable-area contract](https://developer.android.com/reference/kotlin/androidx/compose/foundation/scrollableArea.modifier).

There is one fling. A fling that starts over the header can consume its remaining velocity in posts after collapse. A downward fling consumes body travel before expanding the header. Pull-to-refresh sits outside the controller, receives downward unconsumed motion only when body and header are both at their beginnings, and invokes the existing `pager.load()`. About has no feed refresh action.

Disabling LazyColumn gestures also disables its accessibility scroll actions. The parent must supply one `scrollBy` action and scroll range semantics backed by this controller. Forward and backward accessibility actions follow the same delta routing. Bring-into-view requests for header actions and body rows must use the same controller. Preserve the existing tab roles, labels, horizontal scroll, and 48dp action targets. This behavior is a release gate, not an optional polish item.

### Actual visibility and layout changes

The body is measured at full height even when partly clipped. Pagination must therefore intersect each item's positioned bounds with the outer viewport before deciding that a status or footer is visible. Testing `layoutInfo.visibleItemsInfo` alone would count content currently below the screen. Convert only actually visible status keys to status ordinals and preserve the current last-three-status/footer pagination rule. Header, tabs, Activity controls, error notices, and pending-arrival controls never count as statuses.

Place Activity filters, feed errors, and the pending-arrival control in the selected body, below the category row. These source-dependent elements must not sit above the common header. The filters remain horizontally scrollable. A filter change retains `c`, resets the body to its start, and changes the API query without changing header measurement.

Measure the header and row in one layout, not through delayed `onSizeChanged` corrections. When only category data changes, neither measurement changes. If the profile header later changes height, preserve the category row's previous y where physical bounds permit it: `cNew = clamp(HNew - oldTabY, 0, HNew)`. If the header was fully collapsed, retain full collapse. If a font, width, or account-content change makes the new expanded header shorter than the previous visible tab y, clamp to its true top. That is content reflow, not a category transition. Do not manufacture a permanent empty header to preserve an impossible anchor.

Media changing a status card's height affects the body list only. Stable status keys retain the native body anchor. If deletion or shrinkage forces that list to clamp, `c` stays unchanged. At large text, use measured `H` and `T`, keep the existing zero tab edge padding, and never reserve a fixed header height.

## Source activation and cache compatibility

### Source meaning and ownership

The mapping must remain identical to `Accounts.kt` today.

| Category | `onlyMedia` | `pinned` | `excludeReplies` | `excludeReblogs` |
| --- | --- | --- | --- | --- |
| Posts | false | false | true | true |
| Media | true | false | false | false |
| Featured | false | true | false | false |
| Activity | false | false | `!showReplies` | `!showBoosts` |
| About | No feed source | No feed source | No feed source | No feed source |

FeedPager remains the only owner of row membership, pending arrivals, continuation cursor, request generation, and pagination readiness. StatusProjection remains the only cross-feed freshness/deletion owner. Cache identity remains the existing instance/account/source identity, including Activity query flags. Profile presentation uses the existing account-scoped `UiContext`, which resets on account, instance, and authorization changes.

Only the selected body's session is composed. Switching disposes its source coroutine scope, polling, and event collector. The passive presentation registry and cache writer may outlive it; they perform no network polling or feed work.

### Internal activation state

An activation carries a monotonically increasing epoch. These are private cases inside ProfileTimeline, not booleans on `FeedScreen`.

```kotlin
private sealed interface Opening {
	data object Initial : Opening
	data class Switch(val collapsedPx: Float) : Opening
	data class Resume(val location: ProfileLocation) : Opening
}

private sealed interface Attachment {
	data class Awaiting(val epoch: Long, val opening: Opening) : Attachment
	data class Ready(val epoch: Long) : Attachment
}
```

Initial creation uses the selected source's cache position after projected rows and header measurement exist. A category/filter switch retains the current header and uses body start, ignoring the incoming source's cached position. Resume uses the passive Profile location captured when entering a child route, with status identity recovered against the cached rows. The cache still supplies rows and continuation state in all three cases.

```text
select(next):
  if next equals current selection: return
  acquire the controller mutation lock and cancel its fling/restore
  checkpoint the old source's latest ready pager and current position
  capture c, increment epoch, install selection and Switch(c)
  detach old body and cancel its source scope
  create the new body's list at start, keyed by activation epoch
  release the lock; common header and tabs remain attached

attach(epoch, source):
  saved = cache.readLatestOrDisk(source)
  abort if epoch is no longer current
  pager.restore(saved)                 // existing projection and cursor logic
  recover old FeedPosition by status identity using projected pager.rows
  await measured common header and the attached body's first layout
  abort if epoch is no longer current
  under controller lock:
    Initial -> apply recovered cache position unless the user moved meanwhile
    Switch(c) -> retain current c, body remains at start
    Resume(location) -> restore retained location against current projected rows
    mark this attachment Ready
  enable snapshot observation and the existing active refresh/pagination effects
```

Capture a motion revision when Initial begins. If the user scrolls before cache I/O completes, their newer position wins over Initial. Switch must retain the controller's current `c` at attachment time because the user may also have scrolled during loading. Its captured `c` initializes the replacement, not a later reset. `collectLatest` or the existing composition cancellation handles obsolete I/O, and the epoch check prevents an old result from mutating a new attachment even if an underlying operation does not cancel promptly.

Each activation has a fresh body list at its known opening position. This avoids a one-frame display of an outgoing source index against unrelated incoming rows. Repeated rapid A/B/A switches retain the same header owner. If Posts and Activity happen to map to the same source, the cache key remains the same; selection still starts its body at the beginning. No source meaning is encoded in the category ordinal.

About attaches the same body viewport without a pager or cache read. After its first layout, it applies the retained About offset for Resume, or body start for Initial and Switch, then marks the attachment Ready. A Resume status anchor missing from projected rows uses the recovered cache position, including the existing next-neighbor, previous-neighbor, and leading fallback order. The cache checkpoint captured on departure contains the ordered IDs needed for that recovery.

### Old snapshot interpretation

Keep `FeedSnapshot.index` and `offset` as status coordinates. Keep `leadingOffset` separate, with the same cache key and schema. Continue using `restoredPosition` to follow saved status identity, then a surviving neighbor at zero offset, then the leading fallback. Do not clear legacy cache.

The old leading item was header, category row, and optional Activity filter row. Let `F` be the newly measured Activity-controls height, or zero for other categories. Translate an old `Leading(s)` as follows.

```text
if s <= H:
  c = clamp(s, 0, H)
  body = start
else:
  c = H
  if Activity and s >= H + T:
    body = ActivityControls(clamp(s - H - T, 0, max(0, F - 1)))
  else:
    body = start
```

Offsets formerly inside the tab row now restore pinned tabs with body start. That is the explicit consequence of the new pinned-tab contract. They never become status pixels. An old `Status(i, o)` restores `c = H` and the recovered status at offset `o` below the pinned tabs. If no status survives, the existing `Leading(0)` fallback expands the header.

### New snapshot production

Produce the same schema from the Profile adapter.

```text
if c < H:
  position = Leading(round(c))
else if the first actually visible body item is a status:
  position = Status(statusOrdinal, statusOffsetPx)
else if Activity controls are first and scrolled by f > 0:
  position = Leading(round(H + T + f))
else if the first body item is the footer and there are statuses:
  position = Status(lastStatusOrdinal, 0)
else:
  position = Leading(round(H))

snapshot = pager.snapshot()
remap a Status position from displayed rows to snapshot.items by identity
snapshot = snapshot.atPosition(position)
```

The last branch covers empty/loading bodies and body controls. It does not save control pixels as status pixels. Leading snapshots set status index and offset to zero through the existing `atPosition`. Status snapshots clear `leadingOffset`. Persisted Activity leading offsets remain positions inside the conceptual old combined leading row. About keeps its own passive `BodyAnchor.About` and produces no feed snapshot.

`revealFirstStatus` sets full collapse and scrolls to the first status key at zero status offset. It does not expose the entire profile header. Deferred-arrival decisions retain the current `FeedPosition.readingOlder()` threshold. Footer clamping must map to the last surviving status at zero offset, as the existing viewport does, rather than recording a footer index as a status.

### Immediate checkpoints and ordered persistence

The current 400ms delayed write is scoped to the feed composition, so rapid disposal can discard a pending checkpoint. Preserve the 400ms disk debounce while moving its ownership into `FeedCache.kt` at the `UiContext` lifetime. Use typed cache sessions, one for `Status` and one for `Notification`, so a keyed entry never requires an unchecked serializer cast. Existing FeedCache validation and size bounds apply to both memory and disk snapshots.

A keyed entry stores the latest bounded immutable snapshot immediately and owns one serial disk writer. It is a cache entry, not an active feed. On activation, read that latest snapshot first. If no memory snapshot exists, read the existing disk format. FeedPager still restores and projects it. The session is account scoped; the existing fully qualified cache key continues to separate account, instance, and source.

```text
checkpoint(key, snapshot):
  bounded = apply existing FeedCache bounds to snapshot
  entry.latest = bounded              // synchronous, before returning
  entry.revision += 1
  signal the entry's single writer

writer loop:
  wait for a signal, then the existing debounce interval
  capture the newest revision and latest snapshot
  await the existing disk write       // writes for this key never overlap
  if revision advanced during write, repeat with newest snapshot

readLatestOrDisk(key):
  if entry.latest exists: return it
  disk = read/validate existing snapshot
  return entry.latest if published during read, otherwise disk
```

Snapshot observation starts only after the attachment is Ready and `pager.ready`. On selection change or disposal, synchronously checkpoint a ready pager one last time before the body detaches. Do not checkpoint an unready empty pager over a valid cache. On child navigation, capture the passive Profile location in the same operation. The session writer survives feed disposal and cannot write an older revision after a newer revision for the same key. It does not perform network work.

Closing an activation is idempotent. A switch checkpoints and closes the old epoch before installing the new one. The later disposal callback sees that closed epoch and does not checkpoint it again through the new body. A route disposal closes the still-current epoch from its directly captured body adapter before unregistering it. An observer or finalizer from a closed epoch cannot publish another snapshot.

The cache session can release a clean inactive memory entry after its disk write, provided its current activation no longer needs it. Returning before that write finishes reads the memory snapshot. Returning later reads the completed disk snapshot. Account teardown cancels its writers; process-kill durability remains the same best-effort class as the existing local cache. No new cross-account global cache is introduced.

## Settings route contract

The route stack becomes the sole owner of Settings identity. The app bar resolves `titleKey` through the current `LocalStrings`, so a locale change needs no stored title update. The Settings index retains Language, media-cache clearing, account-gated sections, About this server, Privacy policy, app/version information, licenses, support, and feedback in their current order.

Navigation must guarantee an index below a child. Add this small normalization to the existing `navigateTo`, preserving its Thread behavior.

```text
navigateTo(routes, Settings(section)):
  if section is null:
    use existing duplicate-top suppression and append behavior
  else if top is Settings(index):
    append the child
  else if top is Settings(child):
    replace that child with the requested sibling
  else:
    append Settings(index), then the child
```

This gives ordinary `requestBack` the correct behavior without another handler.

| Starting state and action | Result |
| --- | --- |
| Caller opens Settings | Caller, index |
| Index opens any child | Caller, index, child |
| Standard back in child | Caller, index |
| Standard back in index | Caller |
| Child opens Accounts | Caller, index, child, Accounts |
| Back from Accounts | Same Settings child and its derived title |
| Accounts changes active account | Existing app account lifetime resets routes and presentation state |
| Locale changes | Current title and index labels recompose from route identity |
| Guest opens Settings | Public index rows only; server, policy, and licenses remain available |

Only index rows for `requiresAccount` sections are gated. An impossible direct guest route to an account section should render the existing login prompt instead of running that section's data calls. Account logout normally eliminates such a route through the existing account lifetime. Do not make the UI invent a second section state to repair navigation.

The shell's Settings action is hidden when the current route is Settings, avoiding a second index pushed from within a child. The Accounts action remains available on every Settings child. There is no new Settings `beforeBack` callback. Composer continues to install its existing unsaved-change handler and `requestBack` continues to honor it first.

Delete the local `section` string, all seven string dispatch cases, `LaunchedEffect(section)` reset, and the `‹ Settings` button. Each route starts with a new ordinary scroll state under AppShell's existing content key. Returning from Accounts reconstructs the same child route; preserving unsaved Settings forms beyond their current behavior is not part of this change. Their existing API calls, Save buttons, error handling, and write handlers remain verbatim.

Remove the Licenses `titleLarge` copy and the Privacy Policy `QuanToot Privacy Policy` page heading. The app bar already identifies those pages. Retain the policy's updated date and topic headings. Retain the server's actual name, domain, version, and About/Rules/Contact tabs because those describe the server rather than duplicate the Settings page title. Account privacy, posting defaults, filters, and notification policy have no duplicate general heading to remove.

## Synthesis decision

This is one candidate for the root's comparison. Form 2 is this candidate's base because header travel remains attainable for every selected body, including the independently failing empty-Media case. Form 1 contributes the constraint that visible state must be independent of source-restored data. No other runner's work was read or incorporated. The root owns the final base selection and any grafts.

## Tradeoffs accepted

- We accept pinned tabs in exchange for a constant full-height post viewport after header collapse and reachable categories at any body position.
- We accept a Profile-specific motion controller in exchange for a geometry guarantee that does not depend on measured status heights or artificial blank tails.
- We accept resetting the body to its start on category/filter selection in exchange for predictable transitions that never revive a cached category's header position.
- We accept a small passive presentation registry and cache-owned checkpoint writer in exchange for route return and rapid switching without hidden active feeds.
- We accept clamping an old offset that was inside the now-pinned tab row in exchange for retaining its header meaning without turning those pixels into a status offset.

## Alternatives considered

Form 1 has a simpler gesture implementation and exact old leading-row coordinates. Its fixed-spacer implementation exposes a blank viewport at the end of long feeds. Its exact-fill implementation makes lazy item measurement and end padding a new custom subsystem. I would choose it only after a native prototype shows a bounded same-pass floor using the existing lazy APIs, without delayed correction or blank end screens.

Retaining one live feed subtree per category would hide source reattachment from the caller, but it preserves several viewport owners and keeps unnecessary jobs alive. It does not solve About or a short body by itself and violates the active-feed constraint.

Keeping Settings local state and publishing a mutable title or `beforeBack` callback to AppShell offers a smaller initial diff. It exposes synchronization rules to both files, recreates the current split ownership, and competes with composer interception. A typed route gives the current shell all the information it already uses for other screens.

## Design red-flag review

The design has one public Profile composition call. Callers do not coordinate restore stages or cache readiness. The controller owns one body and one motion stream. The cache boundary owns storage coordinates and write ordering. The route owns the Settings title and back target.

Remove the former Profile feed-leading path in the same change; do not leave two Profile rendering modes. Keep control/body item-key translation private to ProfileTimeline. Keep section title keys on the enum and use exhaustive section rendering. Derive retained Profile IDs from the route stack. Do not add a second route list, global title, status membership map, per-category polling scope, or parallel pager abstraction.

The controller is the principal complexity risk. Its existence earns its place only if it handles header-origin flings, accessibility scroll, and refresh with one mutation owner. If implementation repeatedly needs extra scroll flags or independently animated states, reject that implementation and return to the two whole forms.

## Implementation reconciliation

No implementation has occurred. Any accepted change to pinned tabs, body-start selection, old leading-offset mapping, cache checkpoint ownership, or Settings route normalization must update this package before another implementation unit consumes it. The root is the acceptance owner.

## Open questions and risks

The following are implementation verification questions, not questions that require a human product decision.

- Does the pinned Compose Multiplatform implementation provide the expected `scrollableArea` behavior on iOS, and can the custom state preserve normal drag/fling cancellation while locking the body scroll scope?
- Do VoiceOver scrolling and focus bring-into-view pass through the single controller after body gestures are disabled, including a profile header taller than one screen?
- Does clipping the full-height body exclude offscreen rows from accessibility hit testing and from pagination calculations?
- Does a route change capture its last ready position before the old body detaches, including toolbar Accounts navigation, and can an obsolete restoration ever touch the next epoch?
- Does the cache entry publish memory before disposal and serialize writes under rapid A/B/A switching, without changing cursor or pending-arrival ownership?

## Next implementation step

Build the Profile layout/controller with fixed mock header and empty, short, and tall bodies in the root's authorized implementation phase, then run the empty-Media and category-row native assertions before connecting the existing feed lifecycle.

The complete acceptance set also includes all five categories, Activity filters, loading-to-empty and loading-to-ready, short About, large text, asynchronous header/status measurement, a header-origin fling crossing into posts, reverse fling, pull-to-refresh, pending reveal, deleted-anchor fallback, rapid switching, Accounts child return, legacy cache restoration, all seven Settings children, Settings Accounts return, guest routes, locale changes, composer unsaved-back behavior, and the existing Home source-retention checks.
