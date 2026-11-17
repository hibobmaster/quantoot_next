# Client correction grounding

The requested outcome is an improved KMP client on the already logged-in iPhone 17 Pro simulator, with milestone commits. The Flutter reference is `/Users/bobmaster/Projects/quantoot` and must remain unchanged.

The current baseline is commit `3309739`. A running screenshot is `baseline-home.jpg` in this directory. The visible reply-count control was clicked through Device Hub and opened New post instead of context. The post body has no navigation action. Timestamp navigation is the only ordinary thread entry.

Observed source mechanisms:

- `Foundation.kt` Choice anchors a narrow DropdownMenu to a full-width OutlinedButton. Selection shape and callback already form a useful shared boundary.
- `Accounts.kt` InstanceScreen disables both actions for blank input even though its placeholder names m.qqs.tw.
- `StatusCard.kt` uses ContentScale.Fit inside a 320 dp media height cap. Portrait previews leave unused horizontal space. Sensitive media also hides body text when there is no content warning.
- `FeedScreen` already has PullToRefreshBox, plus a redundant Refresh row. Home alone consumes status events.
- `MastodonApi.events` does not distinguish new statuses from edits. AppShell routes deletes but does not update its local changed-status map on stream edits. ThreadScreen fetches context once and has no stream handling.
- `FeedPager` treats streamed edits during refresh as new arrivals, which can reorder old posts. REST completion can overwrite edits or resurrect deleted rows. UI local overrides can conceal newer server versions.
- Current feed cache stores bounded items, continuation and reading position per account, instance and source. Preserve those guarantees and API token recreation.

Acceptance requires no Refresh row, functioning overscroll refresh, correct new/edit/delete synchronization, discoverable threads and reply counts, full preview bounds with access to uncropped originals, usable language selection, and blank input using m.qqs.tw. Preserve the existing simulator session. Tests must not publish messages or reset its storage.

Design rubric, one point for each criterion:

1. Distinguishes creation, edit and deletion without reordering old edits or duplicates.
2. A concurrent REST request cannot overwrite newer events or restore deleted content.
3. Correct feed/source/account scope, foreground/reconnect behavior, and preserved reading position.
4. Open threads display and synchronize context and replies with a clear reply action.
5. A small coherent API, testable without network, with one owner per invariant.

Expected units are onboarding and shared controls, feed synchronization, media and threads, and verified parity additions. Reads and design sketches are parallel. The logged-in simulator and Xcode builds are serial resources.
