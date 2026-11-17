# Accepted client correction design

Use design A as the base. Independent judge gpt-6-luna scored it 5 of 5, versus 3 of 5 for B. Both candidates converged on shared status freshness and independent feed ordering. Retain A's encapsulated `StatusProjection.read` and `action` wrappers. Graft B's per-status, per-action completion generation and action-only flag/count patches. Keep tickets private.

`StatusProjection.kt` owns canonical values, event/read/action freshness and session deletion tombstones. Existing `FeedPager` and `FeedCache` retain membership, pending arrivals, cursor, bounds and saved position. Project and prune before cache snapshots. Do not add a persistent journal or migrate cache schemas in this correction.

The transport must distinguish Created, Edited, Deleted, Notify and Reconnected events and carry StreamSource. Home uses the session's User stream. The visible local, federated or hashtag view may own one extra stream. Guests use foreground REST reconciliation because current Mastodon requires user tokens for streaming. Unsupported sources retain REST membership. Do not place bearer tokens in URLs or logs.

REST reads cannot overwrite newer events or confirmed actions. An edit never creates membership or changes order. Session tombstones beat older REST, cache and action responses. An action changes only its own viewer flag and count. All old `ui.changed` and `ui.deleted` callers must migrate in this wave.

An open thread owns focal, ancestor and descendant membership. Preserve rows through refresh errors, merge relevant arrivals received during a context request, and reconcile on foreground, reconnect and a 20-second visible timer. Context is authoritative for unknown ancestry. Body and reply-count taps open the thread. A separate labelled reply action opens Compose. New replies return to the parent thread and focus the confirmed reply.

Media receives the complete stable attachment list and tapped index. Previews crop to explicit bounds, while original images fit in the gallery. Keep unsupported attachments at their original index. Sensitive media reveal is independent of body content-warning reveal. Mention/tag navigation uses validated URLs matched to supplied metadata, never a remote URL's apparent local identifier.

The accepted gallery interface is `MediaGallery(ui, attachments, initialIndex, onDismiss)`. UI selection retains the existing Choice signature. New API metadata fields are optional with defaults. Each worker owns its worktree. The root integrates, reviews and verifies the existing logged-in simulator without publishing messages or resetting storage.

Onboarding and blank input were implemented at `227278a` and runtime-verified at `7615510`. The earlier grounding file describes the baseline. The accepted synchronization and media architecture was integrated in `e971b76`, `4d07a5b`, `e1658a9`, `ca411ab` and `ab63c17`.

Accepted implementation refinement from the sync worker: StreamSource.LocalWrite represents confirmed publish, edit and deletion results. It has no network subscription mapping. Local creation prompts a REST membership reconciliation in a visible feed and can immediately add a confirmed reply to its parent thread. This preserves provenance without guessing source membership.

Accepted review correction: a confirmed reply opens the conversation whose ID is `compose.reply.id`, then focuses the returned status. ThreadState only includes a published status whose parent is the focal ID or a known descendant. This prevents replying to an ancestor from adding a sibling to the wrong conversation. Existing navigation reuse remains intentional: if the parent thread is already earlier in the route stack, return to that route with the new focus and pop intervening routes. Otherwise retain the old routes and push the parent thread. A review brief that said every prior route must remain was too broad; preserving duplicate-free thread reuse is the accepted policy.
