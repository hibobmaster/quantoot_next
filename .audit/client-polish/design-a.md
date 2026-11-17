# Status synchronization with separate feed ordering

## Problem

The KMP client must show fresh posts and conversations without moving edited posts or discarding the reader's position.
`FeedPager.receive` currently journals edits as arrivals. `UiContext.changed` can mask later server values, while `ThreadScreen` loads once.
The existing cache already owns bounded snapshots, opaque continuation, and reading position. Preserve that ownership and its account, instance, and source key.
This is a design package only. No source, simulator, credentials, or Git state changed.

## Usage before types

```kotlin
// FeedScreen supplies the existing pager's network callback.
val pager = rememberFeedPager(/* existing cache and list arguments */) { cursor ->
	ui.statuses.read({ ui.api.timeline(source, maxId = cursor) }) { it.items }
}
// The screen consumes the session bus. Ingestion happens once before dispatch.
ui.events.collect { event ->
	when (event) {
		is StreamEvent.Created -> if (source.streamSource() == event.source)
			pager.created(event.status, defer = readingOlderPosts())
		is StreamEvent.Deleted -> pager.prune()
		is StreamEvent.Reconnected -> if (source.streamSource() == event.source)
			pager.load(defer = readingOlderPosts())
		else -> Unit
	}
}
// StatusCard is the sole presentation entry point, including nested quotes.
val entry = ui.statuses.project(rawEntry) ?: return
val status = entry.original
// ThreadState owns request generations and replies received during a request.
val thread = ThreadState(rootId, scope) {
	ui.statuses.read({ ThreadResult(ui.api.status(rootId), ui.api.context(rootId)) }) { it.posts }
}
thread.refresh()
// Existing action buttons acknowledge only the action they performed.
ui.statuses.action(StatusAction.Favourite) { ui.api.setStatusAction(status.id, StatusAction.Favourite, !status.favourited) }
```

## Types and ownership

```kotlin
sealed interface StreamSource {
	data object User : StreamSource
	data object Local : StreamSource
	data object Federated : StreamSource
	data class Hashtag(val name: String) : StreamSource
}
sealed interface StreamEvent {
	val source: StreamSource
	data class Created(override val source: StreamSource, val status: Status) : StreamEvent
	data class Edited(override val source: StreamSource, val status: Status) : StreamEvent
	data class Deleted(override val source: StreamSource, val id: String) : StreamEvent
	data class Notify(override val source: StreamSource, val notification: Notification) : StreamEvent
	data class Reconnected(override val source: StreamSource) : StreamEvent
}
fun Timeline.streamSource(): StreamSource? // Home, Public, Hashtag only
fun MastodonApi.events(source: StreamSource): Flow<StreamEvent>
internal class StatusProjection {
	suspend fun <T> read(load: suspend () -> T, values: (T) -> Iterable<Status>): T
	suspend fun action(action: StatusAction, load: suspend () -> Status): Status
	fun apply(event: StreamEvent)
	fun project(entry: Status): Status? // read-only, resolves wrapper, original, and quote
}
internal class FeedPager<T>(/* existing arguments */, project: (T) -> T? = { it }) {
	fun created(item: T, defer: Boolean) // replaces receive; unknown creations alone add IDs
	fun prune() // projects rows, pending, and in-flight creations, dropping nulls
	// Existing restore, snapshot, load, and reveal remain.
}
internal data class ThreadResult(val root: Status, val context: StatusContext) {
	val posts: List<Status> // ancestors + root + descendants, distinct by id
}
internal class ThreadState(val rootId: String, scope: CoroutineScope, fetch: suspend () -> ThreadResult) {
	fun refresh()
	fun apply(event: StreamEvent)
	val rows: List<Status>
}
```

`Models.kt` owns domain event/source types. `MastodonApi.kt` owns wire parsing, subscription parameters, reconnects, and transport limits.
`StatusProjection.kt` owns status freshness, action acknowledgements, nested status resolution, and terminal deletion within one API session.
`FeedCache.kt` keeps cache serialization and pager ordering. Each pager owns only its own membership, cursor, pending arrivals, and request generation.
`Foundation.kt` gives `UiContext` one projection and one `ingest(event)` path that applies then emits. Remove public `changed` and `deleted` mutation.
`QuanTootApp.kt` owns the foreground user stream. `Feeds.kt` owns the visible public/hashtag stream and the thread's active lifetime.
`StatusCard.kt` projects once. Remove every caller's `ui.changed[id] ?: status` branch, including notifications, search, and trends.
Composer and deletion successes enter this same projection and emit typed local changes. Local creation triggers source refresh rather than guessing public or hashtag membership.

## Merge invariants

1. Keep a private record by status ID with its newest accepted value, content edit time, read-start sequence, event sequence, and action field stamps.
2. `read` captures a sequence before suspension and merges extracted statuses after completion. Its caller never coordinates begin/finish tokens.
3. A response cannot replace a value changed by an event after that response's request began. Older concurrent reads cannot replace later reads.
4. Reject content with an older parsed `editedAt`, treating absent edit time as creation time. Equal edit times use the causal sequence rule.
5. Recursively ingest originals and quoted statuses with the same request sequence. A wrapper never supplies an older copy of its original.
6. Successful actions update only their own relationship flag and corresponding count. Merge returned content using the request's original sequence.
7. A stream edit cannot erase a newer action acknowledgement through absent/default relationship flags. A REST read begun after acknowledgement settles that field.
8. All reducer changes run without suspension on the session's UI dispatcher. Network calls run outside that reducer. A sequence spans every screen in the session.
9. Deleted IDs are terminal until this API session ends. Neither REST, cached data, nor an older creation can restore a deleted ID.
10. Deleting a wrapper removes that row only. Deleting an original removes its direct and boosted rows, while a quote keeps a deleted-quote placeholder.
11. Pager projection runs on restore, REST completion, pending reveal, prune, and cache snapshot. Canonical freshness stays in `StatusProjection`.
12. A creation for an existing row updates its value without moving its ID. An edit for an absent row never adds membership or increments pending count.
13. Replace `arrivalsDuringRefresh` with creations absent from the pre-request membership. Retain them across refresh completion, deduplicated by ID.
14. Deferred refresh preserves existing order and the exact old continuation. Append preserves raw server continuation even when projection removes every returned row.
15. Canceled and superseded requests never replace membership. Stable lazy-list keys preserve the visible anchor. Only explicit reveal scrolls to the top.
16. Keep the 100-item and 512 KiB cache bounds. Cache projected values and current position, never bearer tokens. Recreate the projection and subscriptions with the API identity.

## Source scope and threads

Map `update` to Created and `status.update` to Edited. These are distinct server events in the [Mastodon streaming contract](https://docs.joinmastodon.org/methods/streaming/#event-types-and-payloads).
Use one user connection while signed in and foreground. Add at most the visible Local, Federated, or Hashtag connection, with a separate `tag` parameter.
Tag every event from its requested source and validate any envelope source against that subscription. Never derive Home membership from public visibility.
Emit Reconnected when a previously connected stream connects again. Screen activation and foreground resume already trigger the initial REST reconciliation.
Current Mastodon WebSockets require a user token. Guests use visible foreground REST polling and pull-to-refresh, with no anonymous WebSocket attempt.
Account posts, favourites, and bookmarks have no equivalent source here. Preserve REST membership and apply known edits/deletes globally.
An open thread has no dedicated stream. Refresh context on entry, foreground resume, reconnection, and a relevant event, plus every 20 seconds while visible.
Coalesce relevant event refreshes and allow only one context request at a time. Keep rows during refresh and expose retry without blanking the conversation.
Relevant creations reply to the root or a known descendant. Retain such replies received during a context request until its merge completes.
Unknown ancestry waits for context retrieval. Do not append unrelated public posts or derive root reply counts by incrementing stream deliveries.
Server context order owns ancestors and descendants. The root status owns its reply count. Edits update values in place and deletion removes matching rows.
If the root is deleted, retain already loaded conversation rows and show its unavailable state. A definitive root 404 must not erase loaded replies.
Body/background taps and the reply-count control open `Screen.Thread(original.id)`. A separate labelled Reply action opens the composer.
Links, media, author rows, and action buttons keep their own hit targets. Text selection still works. Remove the redundant Refresh row and keep the new-posts banner.

## Small contracts for adjacent units

```kotlin
@Composable fun MediaGallery(ui: UiContext, attachments: List<MediaAttachment>, initialIndex: Int, onDismiss: () -> Unit)
sealed interface ContentTarget { data class Profile(val id: String) : ContentTarget
	data class Hashtag(val tag: String) : ContentTarget; data class External(val url: String) : ContentTarget }
fun contentTarget(href: String, mentions: List<Mention>, tags: List<Tag>): ContentTarget?
```

Open the gallery with an immutable nonempty attachment snapshot and its tapped index. Do not filter the list after deriving the index.
Previews crop to fill their bounded area. Gallery images use original URLs and Fit, with zoom reset per attachment and paging only at default zoom.
Keep unsupported media at its original index with an external-open control. ALT, share, and save refer to the current attachment.
Add `Status.tags` using the existing `Tag` type. Pass mentions and tags to `HtmlContent` together with the status content.
Resolve a safe HTTP(S) link internally only when its normalized URL matches supplied mention/tag metadata. A mention's ID is already local to this API instance.
Route a matched hashtag to the current instance's hashtag feed. Unknown safe links remain external, and unsafe links become plain text.
Never interpret an arbitrary remote URL path as a local account ID or send authentication to a link's host.

## Synthesis decision and regressions

Choose a session projection with independent pager order. It centralizes freshness while preserving the existing cache, generic notification pager, and screen structure.
The alternative is a normalized application store that owns entities, every timeline's ID list, cursor, and subscriptions. It offers smaller screen APIs but expands this fix into cache and lifecycle migration.
Reject that alternative because its subscription registry and global feed retention solve no observed extra requirement. Model the Domain separates values from membership; Laziness Protocol preserves the working cache.
Separate Before Serializing Shared State keeps each pager's order private. Only freshness across screens requires the shared reducer.
Accept session-lifetime tombstones and periodic thread reads in exchange for deletion safety and complete replies without a nonexistent thread subscription.
Regression cases must cover edit during refresh/page append, delete during either request, stale action response after edit, duplicate creation, pending-item edit/delete, and original deletion through boosts/quotes.
Also cover source cross-talk, API/account replacement, reconnect during an in-flight load, opaque cursor after deferred refresh, cache restore after a live deletion, and a reply arriving during context retrieval.
Verify unrelated thread events, root deletion, unknown reply ancestry, gallery index preservation, nested link taps, and malicious mention/tag URLs.
Start implementation with pure projection and pager race tests using deferred fake responses. Then wire transport and UI, and verify on the existing logged-in simulator without publishing.
No implementation deviations exist yet. Ground and Sketch are complete; Agree belongs to the orchestrator's synthesis; Implement and Scrap are outside this runner's scope.
