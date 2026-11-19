# QuanToot Kotlin Multiplatform architecture

QuanToot shares Mastodon behavior and Compose UI between Android and iOS. The Android entry uses `com.android.application`. The shared library uses Google's `com.android.kotlin.multiplatform.library` plugin. The iOS entry embeds a static Kotlin framework.

The `model` package defines immutable account, status, poll, media, notification and settings values. `data` owns HTTPS requests, pagination, bounded decoding, external content validation and streaming. `auth` owns all session mutations and the pending OAuth transaction. `ui` owns screen state and navigation. `platform` defines secure storage, browser, media and operating system operations.

An account switch changes the root composition key. This disposes its requests, navigation and streaming before rendering the next account. Clients capture an instance and token. Screens cannot retarget an existing client. The composer is disposed with its account, so it cannot submit through a newly selected account.

AccountStore writes the encrypted snapshot before publishing a change. Instance and locale preferences contain no tokens. OAuth state is random, expires, validates the exact callback, and is consumed once. WebSocket credentials use the Authorization header. JSON bodies and nesting are bounded before deserialization.

## Design comparison

Two independent sketches compared a shared service design and separate feature modules. The shared service design was selected. An independent gpt-6-luna review preferred it for simpler ownership and a more complete OAuth transaction contract. Both sketches agreed on shared Compose screens and native operating system adapters.

The implementation combines both sketches' account-scoped screen lifetimes and desired-state status actions. Feature Gradle modules were rejected because the source has roughly 1 MiB of Dart and no demonstrated need for that many build boundaries. One shared module keeps calls short while packages make responsibilities visible.

## Accepted tradeoffs

- Compose UI is shared, while media, browser authorization and storage use native APIs.
- Serializable immutable models also represent the API projection. Unknown server fields are ignored. Maintaining parallel wire and domain models would duplicate the same data without protecting another consumer.
- The root discards account-specific navigation and drafts on switching accounts. The composer asks before discarding unsent work.
- Blank instance input uses `mastodon.social`. Explicit hosts pass normalization and HTTPS validation. Guests enter Explore immediately. A background request refreshes public timeline capabilities independently of the selected tab, and the last successful result is cached per instance.
- Android requires API 26. iOS requires 15.0, matching the source iOS floor.

## Verification

Shared tests use Ktor MockEngine to inspect requests and exercise host validation, response bounds, account restoration, OAuth state, posting and pagination. Android and iOS builds verify both native adapters. Runtime checks inspect actual instance selection, guest navigation and preference restoration. Live-server write tests use a supplied test account, and system photo permissions are checked on a device.

`./scripts/verify.sh` also runs Android Compose host tests with Robolectric and a local MockEngine fixture. These tests cover draft retention on window resize, cancel and discard, private-post publication and profile edits, and guest navigation during slow or unavailable instance metadata requests. They use fixed local records and require no Mastodon server, credentials, or emulator. The foldable device and system multitasking checks remain part of device QA.

## Implementation reconciliation

The selected design uses a single `MastodonApi` with typed operations instead of separate Reader and Member interfaces. Mutations enforce authentication at the API boundary. Screens expose login prompts for guests. AccountStore owns secure account records, and pages own their captured clients. This preserves the ownership and cancellation contracts while keeping the API close to the existing client.

Timeline and people requests return `Page<T>` with an explicit server continuation. Feed snapshots persist that continuation together with the rows. Deferred refresh preserves the loaded tail and its cursor. Old or truncated caches require a replacement refresh before paging. The previous public-ID cursor map was removed after independent review found it could lose cursors across refresh or restart.

Profile uses a naturally measured header, pinned category row and one lazy body. `ProfileScrollState` owns their continuous drag, fling and reveal motion. Category and Activity-filter changes retain header collapse and replace the body at its beginning. About uses the same layout without a pager. The body is measured at its full reading height but clipped until the header moves away; pagination intersects lazy items with that outer clip. Empty bodies therefore do not prevent header travel.

`FeedViewport` adapts ordinary lists and Profile to the shared status-pager lifecycle. Profile keeps status indices separate from legacy leading pixels, and a footer has a safe cached status position independent of whether the reader is deferring arrivals. A selected body's captured attachment and motion revision reject obsolete cache restores. Closing a ready pager checkpoints its final position before detaching and cancels its source work.

`UiContext` owns separate typed Status and Notification cache sessions. Each key has an attachment epoch, immediate bounded memory snapshot and one ordered disk writer with a 400ms debounce. Unchanged row payloads are reused during scrolling. Snapshots remain limited to 100 items and 512KiB; clean inactive entries release after persistence. Dirty keys may accumulate while storage is blocked, and account/process teardown remains best effort. FeedPager owns membership and cursors; StatusProjection owns content freshness and deletion.

Timeline and notification snapshots use a prefix derived from the instance and saved account. Account removal serializes deletion with snapshot writes, invalidates old writers, and preserves other accounts' snapshots and reading positions. On first cache use after upgrade, legacy v1 snapshots are cleared because their keys cannot be attributed to accounts. This also resets reading positions saved under those keys.

Settings children are typed `Screen.Settings(section)` routes. `SettingsSection` supplies the localized title key and account requirement. The shell owns the title and standard back action; navigating directly to a child inserts the index below it, and sibling children replace each other. Existing composer back interception remains in force. Profile's selected category is local to its route and starts on Posts when that route is recreated.

## Principles applied

Model the Domain led to explicit session, pending authorization, route, page and feed state. Foundational Thinking put the build and shared contracts before screen migration. Sequence Work into Verifiable Units led to independent API and authentication tests before UI integration. Separate Before Serializing Shared State gave each implementation worker its own output directory. Prove It Works required actual Android and iOS runs and native credential tests. Never Block on the Human allowed reversible implementation choices to proceed within the requested migration. Attack the Premise changed the iOS diagnosis when native writes succeeded and reads returned an invalid-parameter error.
