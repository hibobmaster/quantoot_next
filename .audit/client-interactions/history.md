# Interaction regression: source-control investigation

## Source

Local git and versioned repository audit/design records in `/Users/bobmaster/Projects/QuanToot_Next`, anchored to baseline `67d1f79`. No production changes, builds, devices, accounts, or network mutations. The root added reproduction commit `a1514d8` during investigation; that is current-task evidence and is not treated as historical motivation.

All historical commit metadata names `hibobmaster <bobmaster@hibobmaster.com>` on 2026-11-17 (+0800). The audit documents speak in agent/orchestrator terms and are treated as recorded agent plans, not attributable original human rulings. No original user transcript establishing the exact old restrictions is present in these records.

## What I searched

- Read investigator-prompt.md, epistemics.md, and sources/code-archaeology.md from the installed pstack why skill.
- `git log --all --format=... --` the four anchors; individual `git log --follow --oneline --` Accounts/Feeds/QuanTootApp/MediaGallery/FeedCache.
- `git blame -L 157,399 -- .../Accounts.kt`, `-L 20,225 -- .../Feeds.kt`, `-L 108,280 -- .../QuanTootApp.kt`, `-L 292,350 -- .../MediaGallery.kt`; document blame for accepted design lines.
- `git log -S 'heightIn(max = 380.dp)' -- .../Accounts.kt`; `git log -S 'feed_cache_v1_' -- .../FeedCache.kt`; `git log --all -G '380|FeedSnapshot|feedCacheKey|HomeScreen|GalleryImage|dismiss' --` anchors and cache; commit-message search `profile|scroll|cache|gallery|federat|gesture`.
- Opened relevant complete commit diffs: c23ded6, e971b76, 3441b80, 4ae09f0, e5d095a; introduction/stat and surviving-line blame for 4d07a5b; root-snapshot Home implementation.
- Read `.audit/client-refinement/{brief,design,design-a,design-b}.md` and `.audit/client-polish/{design,grounding}.md`; searched related review/grounding/verification records for `380|header|federated|home.*select|navigation|swipe|drag|dismiss`.
- Inspected FeedSnapshot/cache/restore source and existing FeedBehaviorTest/MediaBehaviorTest tests. `git remote -v` is empty. No configured remote, PR number, ticket, or connected document identity was found for this local snapshot history. CHANGELOG.md does not exist.

## Direct evidence found (documented plans, with provenance limits)

1. **Earlier Profile scope explicitly deferred combining scroll owners.** Candidate A says: “This makes the action discoverable even within the existing capped scrolling header. Do not combine the profile feed and header into a new pager implementation during this refinement.” It later records: “Accept the existing separately scrolling Profile header in exchange for a bounded redesign; moving actions before long biography content reduces its practical cost.” Sources: `.audit/client-refinement/design-a.md:85,311`, committed at 3441b80, 18:27:53 +0800. This supplies an explicit *candidate's* reason for retaining the old structure in the previous refinement. It does not establish why 380 dp was originally chosen or a permanent human prohibition. Candidate B was the final base (`design.md:3`); A's Profile restriction is not explicitly among the enumerated grafts (`design.md:5`).

2. **The accepted refinement prioritized action hierarchy and bounded utilities while preserving existing behavioral owners.** “Profile primary actions move before a long biography, with filled/tonal Edit or Follow, bounded QR/Share, outlined Favorites/Bookmarks, compact counts and overflow moderation. Relationship writes remain behind current auth checks and gain one local busy state.” Source: `.audit/client-refinement/design.md:7`, 3441b80. Candidate B also says “QR/Share as compact labelled secondary controls,” preserves the routes/counts/fields, permits action wrapping/stacking, and retains 48 dp tap areas (`design-b.md:85,87`). This documents why the actions were promoted, while allowing their visual compactness to be improved now.

3. **Feed persistence ownership was deliberately retained in the preceding synchronization correction.** “Existing `FeedPager` and `FeedCache` retain membership, pending arrivals, cursor, bounds and saved position. Project and prune before cache snapshots. Do not add a persistent journal or migrate cache schemas in this correction.” Source: `.audit/client-polish/design.md:5`, committed at 858ab35, 16:01:27 +0800. This explicitly scopes the prior no-migration constraint to that correction. `.audit/client-polish/grounding.md:15` also records account/instance/source bounds and reading-position preservation as baseline guarantees. The accepted refinement repeats preservation of lifecycle/stream/projection ownership (`.audit/client-refinement/design.md:7`).

4. **Media ordering and original-image behavior have an explicit preservation contract.** “Media receives the complete stable attachment list and tapped index. Previews crop to explicit bounds, while original images fit in the gallery. Keep unsupported attachments at their original index.” Source: `.audit/client-polish/design.md:13`; accepted gallery signature at line 15 is `MediaGallery(ui, attachments, initialIndex, onDismiss)`. This is directly relevant to adding dismissal without changing selected media or pager behavior.

5. **Large-text Home geometry has a later explicit correction.** “When a word cannot fit a weighted tab without splitting, use the existing scrollable fallback. Measure natural word width, not the already-wrapped result. Default-size tabs retain equal widths.” Source: `.audit/client-refinement/design.md:33`, 3441b80. The same commit changes only HomeTimelineSelector's natural-word measurement in Feeds.kt. It supplies no rationale for changing selection lifetime, and does not change it.

## Indirect / circumstantial evidence (code is mechanism, not intent)

- **Profile's stuck-header mechanism predates recent UI refinements.** Blame traces Accounts.kt:202-205 (`heightIn(max = 380.dp).verticalScroll(rememberScrollState())`) and the separate tabs/feed at :321-381 to the root snapshot 3309739, 13:43:04 +0800, titled only “chore: snapshot the Kotlin Multiplatform client baseline.” The pickaxe finds no later cap change. c23ded6 (16:45:45) moves/adds 48 dp action rows and counts inside that old cap without changing the cap/scroll owner. Supported reading: the recent layout increased content inside a retained structure; the structure itself was inherited. Alternative: the initial migration already intentionally chose independent scrolling, but the local history does not state that rationale.

- **Home source lifetime and child-route disposal also predate recent refinement.** Feeds.kt:31 local `remember` selection and QuanTootApp.kt:265-280 route-keyed conditional rendering both blame to 3309739. c23ded6 replaces the tab selector, 3441b80 corrects its fit measurement, and e971b76 adds projection/stream/navigation reducers; none lift this selection. The mechanism supports the observed reset after child-route return, but supplies no evidence that resetting to Home was an intended product behavior.

- **FeedSnapshot uses status-array coordinates, rather than arbitrary visible-list coordinates.** FeedCache.kt:13-18 stores `items/index/offset` with cursor; :21-29 keys it by instance/account/source under `feed_cache_v1_`; :137-138 snapshots projected status rows. Feeds.kt:114-120 looks up `saved.items[saved.index]` by status ID before restoring into current projected rows. That identity-anchor restore was added in e971b76. Supported reading: the coordinate coupling is a consequence of a status-only lazy list plus cache schema; it is not evidence of a requirement that Profile can never become one scrolling list.

- **Image gestures currently have transformation and horizontal-paging ownership, but no vertical-dismiss branch.** The entire GalleryImage transform/double-tap block (MediaGallery.kt:292-344) blames to 4d07a5b, 14:14:31, “Restore attachment galleries and safe content links.” It consumes movement for multiple pointers or zoom >1. HorizontalPager is enabled only at scale <=1 (:196-202). The gallery close button/dialog already receives onDismiss (:166-170). e5d095a subsequently changes save feedback, leaving these gestures intact. Mechanism suggests an additive axis/zoom ownership design is possible; historical evidence does not say why vertical dismissal was omitted.

- Existing tests encode stable attachment snapshot/index (`MediaBehaviorTest.kt:27-42`), bounded zoom/pan (:61-68), pending arrivals preserving read rows (`FeedBehaviorTest.kt:52-70`), raw paging cursor (:30-48), and bounded/corrupt cache behavior (:178 onward). These support preservation obligations, not claims that the new requested interactions were already tested.

## Contradictions / scope differences

- Candidate A explicitly accepts separate Profile scrolling and defers combination (design-a.md:85,311). Candidate B, chosen as base, asks compact utilities and scrollable content at large text (design-b.md:87), while the final contract only says bounded QR/Share and existing lifecycle/owners (design.md:7). The record does **not** establish that A's scroll restriction survived synthesis as an immutable rule. Treating it as a permanent user constraint would exceed the evidence.
- Candidate A originally prohibited Home horizontal scrolling even at large text (design-a.md:134). The accepted contract and final word-fit correction explicitly allow a measured scrollable fallback (design.md:7,33; 3441b80 Feeds.kt diff). Preserve the latter behavior.
- No evidence contradicts the current user's new scrolling/dismissal/retention requirements. Earlier bounded-task choices are historical context, not overriding authorization.

## Gaps

- Root snapshot 3309739 has no preceding local migration commits or rationale for 380 dp, composition-local Home selection, or route disposal. Those original motivations remain unknown.
- No local textual rationale found for omitting vertical image dismissal, intentionally resetting Federated after child routes, or accepting that reset as a product requirement.
- Audit records are agent-authored summaries/plans stored under a human-named commit identity. Original attributable human wording is absent; repeated summaries do not independently prove human approval of exact scope.
- Remote/PR/ticket identity is unavailable. No unrelated connected private source was searched; no Slack, issue, infra, error, or warehouse source is configured for this generated local history.
- Existing tests do not establish correct mixed Profile prefix/status cache mapping; that would be a newly introduced coordinate problem, not evidence of an old guarantee.

## Planning constraints for the synthesizer

**Preserve (documented and supported):** feed membership/order/pending/cursor/bounds/account-instance-source isolation; StatusProjection freshness/deletion ownership; source-to-Timeline mapping, signed-out Local default and Home login prompt; default equal-width and large-text natural-word fallback; profile destinations/auth/busy handling/counts/fields; gallery stable list/index, fit originals, horizontal page selection, zoom/pan, close/share/save and captured save target.

**Change (current user request, with no contrary historical human ruling found):** Profile/header and content should participate in the requested common scrolling experience; tighten secondary action layout; give unzoomed image vertical drag an explicit dismissal path; move Home source selection to a lifetime that survives child-route disposal. These are planning implications, not implementation prescriptions.

**Avoid:** interpreting old candidate scope as a permanent restriction; introducing another status/feed cache owner; keeping the whole Home feed composed merely to preserve selection without examining hidden stream/poll ownership; changing cache coordinates silently; allowing vertical dismiss to steal pinch/zoom pan or horizontal pager gestures.

**Risk requiring an explicit design:** adding header/tabs/filter/about prefix items to FeedScreen's LazyColumn while continuing to persist its raw `firstVisibleItemIndex/offset` in the existing status-only `FeedSnapshot` would make `saved.items[saved.index]` refer to a different status or clamp it incorrectly. Account for prefix/status coordinates in both persistence and restoration, or represent Profile scroll state separately with an explicit compatibility policy. Also re-evaluate `readingOlder()`, explicit reveal-to-index-0, and pagination thresholds, which currently assume status-only list coordinates (Feeds.kt:112,127-128,150-156,177-178,204). The historical “no schema migration” was scoped to a prior correction, not a reason to silently reuse incompatible coordinates now.

## Additional leads

No cross-source identity leads. The main agent already owns runtime grounding and native reproduction; synthesize with that evidence. No further broad archaeology is warranted for the four requested interactions.
