# Client synchronization design B

## Problem

A feed needs ordered entries, while one status can appear in feeds, notifications, quotes and threads.
`UiContext.changed` can mask newer data; streaming conflates insertion and edit; REST can undo both
edits and deletes. Account/instance/source separation, opaque cursors, bounded snapshots and position must survive.

## Usage (caller's view)

```kotlin
// AppShell, once per api/account and only while foregrounded.
api.events(StreamSource.User).collect { ui.accept(it) } // also updates unread

// FeedScreen; the source owns a stream only when Mastodon supports one.
val pager = rememberStatusPager(ui, source, listState)
LaunchedEffect(pager) { pager.start(source, defer = { !atTop }) }
// start subscribes, restores, starts the source if needed, and refreshes.
pager.rows.forEach { entry -> StatusCard(ui, entry) } // card calls projection.project(entry)

// ThreadScreen; start listening before fetching, and stop on disposal/background.
val thread = rememberThread(ui, rootId)
LaunchedEffect(thread) { thread.start() } // status + context, events, reconciliation
thread.rows.forEach { StatusCard(ui, it, highlighted = it.id == rootId) }

// A confirmed action has a field-specific ticket, not a whole-status override.
val ticket = ui.statuses.beginAction(status.id, StatusAction.Favourite)
val response = ui.api.setStatusAction(status.id, StatusAction.Favourite, true)
ui.statuses.completeAction(ticket, response)
```

These are ownership sketches. Projection/pager changes run on one UI dispatcher (or under one owner lock).

## Shape and ownership

```kotlin
sealed interface StreamSource {
    data object User : StreamSource
    data class Public(val local: Boolean) : StreamSource
    data class Hashtag(val tag: String) : StreamSource
}
sealed interface StreamEvent {
    val source: StreamSource
    data class New(override val source: StreamSource, val status: Status) : StreamEvent
    data class Edit(override val source: StreamSource, val status: Status) : StreamEvent
    data class Delete(override val source: StreamSource, val id: String) : StreamEvent
    data class Notify(override val source: StreamSource, val notification: Notification) : StreamEvent
    data class Reconnected(override val source: StreamSource) : StreamEvent
}
class StatusProjection {
    fun beginRead(): ReadTicket
    fun merge(ticket: ReadTicket, statuses: List<Status>)
    fun accept(event: StreamEvent)
    fun beginAction(id: String, action: StatusAction): ActionTicket
    fun completeAction(ticket: ActionTicket, status: Status)
    fun delete(id: String) // successful local deletion, same transition as stream deletion
    fun project(entry: Status): Status? // null when entry or original was deleted
}
// FeedPager retains its existing paging/cache API, with accept(event, defer).
// A thread owns root/ancestor/descendant membership; it uses the same projection.
```

- `MastodonApi.kt` maps `update` to `New`, `status.update` to `Edit`, and `delete` to `Delete`;
  notifications remain distinct. Re-established connections signal `Reconnected` for their source.
  Preserve frame/rate bounds, cancellation, exponential retry and API close.
- `QuanTootApp.kt`/`UiContext` own one account-scoped projection and event dispatch.
  Apply an event to the projection before broadcasting it; notification IDs are
  deduplicated for unread. API/token recreation replaces the owner and its sockets.
- `FeedCache.kt` owns ordered entry membership, pending arrivals, cursor and paging;
  `StatusProjection` alone owns status values and tombstones. Rows may retain raw
  snapshots for cache serialization, but every card projects them before display.
  Remove all `ui.changed`/`ui.deleted` reads and writers in the same migration.
- `StatusProjection` stores status by ID and a monotonic last-write sequence per
  logical field group, including tombstones. Normalize/project both wrappers and
  nested reblogs/quotes: editing an original updates all its appearances; deleting
  it hides its wrappers; deleting only a wrapper leaves its original intact.
- A REST call takes its ticket immediately before starting. On completion, merge each
  returned entity/group only if no event, action, delete, or newer completed REST
  merge has written it since that ticket. A tombstone always wins. Tickets protect
  search, trends, notifications and context too, not just feed refresh/pagination.
- `Edit` changes content in place, never membership or order; preserve confirmed interaction fields.
  Ignore demonstrably older `editedAt`; equal/missing timestamps use arrival order. `New` inserts only
  into the feed whose source delivered it; duplicates update neither order nor count. `Edit`/`Delete`
  apply across sources. A notification status seeds a missing entity but cannot replace a newer one.
- An action completion patches only the matching viewer flag/count, with a per-ID,
  per-action request generation; a late action cannot replace another action or an
  edit. Reconcile with a later ticketed REST read; no indefinite full-status local
  override. Composer results merge with a ticket; feed membership is learned from its stream or REST.
  A confirmed local reply is delivered to the open thread without inventing other feed membership.
- During restore subscribe/buffer first, hydrate the cache without overwriting later
  writes, then apply buffered events and begin refresh. During each request, journal
  new membership and deletions; merge that journal after the response, filter through
  projection, deduplicate IDs, and reject obsolete request generations. An edit is
  never in the arrivals journal. Apply this to both refresh and pagination, including
  notification rows. A deferred refresh updates known rows in place and keeps the
  existing tail cursor; only real new entries enter `pending`.
- Keep bounded cache snapshots and account/instance/source keys. Store projected values, index/offset,
  opaque cursor and deletion watermark. Persist a bounded per-account/instance delete journal and
  its low-water mark; await it before hydration and filter restored rows through its tombstones.
  When pruning, snapshots older than retained history become unusable and refresh, retaining a scroll
  anchor when resolvable. Legacy snapshots have no watermark and must refresh. Disk failures remain
  non-fatal; in-flight request fences are session-scoped.
- `Feeds.kt` subscribes to `User` for Home, `public:local` for local, `public` for federated,
  and `hashtag` with an encoded tag. Guest public streams work without a user socket.
  Account/favourites/bookmarks have no safe inferred stream membership: refresh, never inject
  unrelated arrivals. One connection per active source; no token in URLs/logs.
- On return to foreground or source reconnect, refresh the active view without
  moving a reader away from their anchor; coalesce overlapping refreshes. Poll/open
  unsupported feeds on activation. Keep existing mark-read and pagination behavior.

## Threads, media and links

- Reply count and post body open `Screen.Thread(original.id)`; controls, links, profile, media and
  quote consume their own taps. A labeled Reply action opens `Screen.Compose(reply = currentRoot)`.
- `ThreadScreen` owns root, ancestor and descendant IDs. On a `New` or notification
  status, append only if its `inReplyToId` is the root or a known descendant;
  never include a sibling just because its parent is an ancestor. Deduplicate and
  project edits/deletes. A reply whose parent was not yet seen waits for context
  reconciliation. Merge live arrivals/deletes received while `status`/`context`
  are in flight, with the same ticket/journal rule as the pager.
- The open thread observes the account User stream plus an available Public stream. These cannot
  guarantee all private, unlisted or remote replies; reconcile context on reconnect, relevant activity
  and a modest foreground timer. Cancel on exit/background, preserve rows on errors, and fetch root
  for its count instead of incrementing for potentially duplicated events.
- `MediaGallery(attachments, initialIndex)` receives the whole stable attachment
  list and the tapped original index. Preview tiles crop to fill their bounds; the
  viewer uses the safe original URL with Fit, preserves ALT/share/save/zoom, and
  navigates previous/next without mixing indices when a URL/type is unsupported.
  Sensitive media reveal is separate from content-warning body reveal.
- Add decoded status tags. `HtmlContent` receives the status's mentions/tags as
  optional routing context. Resolve an anchor internally only when its validated
  URL matches a supplied mention URL with a nonblank account ID, or a supplied tag
  URL with a valid tag; navigate to `Profile(id)` or `Feed(Hashtag(name))`. Do not
  infer identity from label text or arbitrary hosts. Other valid HTTP(S) links use
  `ui.open`; unsafe links remain inert. Profile text without metadata stays external.

## Synthesis decision and checks

Choose a shared status projection plus per-view pagers: it hides freshness and request fences while
keeping list order/cursors local. An app-wide reducer holding every feed, thread, notification and
entity is structurally distinct; its selectors are deep, but moving pagination, lifecycle and disk
snapshots into it expands the rewrite. Per-screen reducers would repeat cross-screen edits, tombstones
and race rules. Model-the-domain encodes membership; boundary-discipline puts decoding at the wire;
idempotence makes repeats converge; reader-load keeps one status owner without pass-through modules.

Regressions: edit during refresh/page never moves or reverts; new during refresh
appears once; delete beats delayed REST/cache/action in every view, including boost
originals; overlapping REST and action responses preserve newer body and flags;
source/account/guest isolation; reconnect and foreground preserve anchor/cursor;
cache and token recreation; notification deletion/grouping; nested live replies,
unseen-parent recovery, duplicate delivery and reconnect; unsupported link/URL,
multiple mixed media and sensitive-without-warning body. Use deterministic fake
requests/streams, plus the logged-in simulator without publishing or clearing state.

Risk: the protocol lacks a universal event sequence, so delayed equal-timestamp edits
or server snapshots already stale at request start cannot be perfectly ordered; a
later authoritative refresh converges. Implement typed events and projection first,
then migrate pagers/threads and verify each unit against these contracts.
