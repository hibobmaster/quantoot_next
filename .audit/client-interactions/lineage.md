### The Question

Why did Profile’s separate scrolling, spread-out actions, missing image dismissal, and lost Federated selection survive earlier refinements? What historical constraints should guide the requested correction?

### The Code in Question

`ProfileScreen` in Accounts.kt:157–381; `HomeScreen`, `FeedScreen`, and `rememberFeedPager` in Feeds.kt:30–226; route rendering in QuanTootApp.kt:265–282.
`FeedSnapshot`/`FeedCache` in FeedCache.kt:13–138; gallery state and gestures in MediaGallery.kt:144–344. Baseline: `67d1f79`; current reproduction: `a1514d8`.

### What We Found

- **[Direct]** Earlier candidate A explicitly retained separate Profile scrolling “in exchange for a bounded redesign,” and prohibited combining header/feed “during this refinement.” These are agent-authored candidate statements, not original attributable human instructions. The accepted synthesis chose B and enumerated other A grafts; it does not explicitly carry that restriction forward. Sources: [candidate A:85](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-refinement/design-a.md:85), [A:311](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-refinement/design-a.md:311), [accepted:3](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-refinement/design.md:3).
- **[Direct]** The accepted refinement promoted Edit/Follow before long biographies, bounded QR/Share, and retained relationship authorization/busy handling. Candidate B called secondary controls compact and preserved 48dp targets, routes, counts, and fields. Sources: [accepted:7](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-refinement/design.md:7), [B:85](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-refinement/design-b.md:85), [B:87](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-refinement/design-b.md:87).
- **[Supported]** The Profile scroll split and Home selection lifetime were inherited rather than introduced by the latest visual refinement: blame places the capped header, composition-local selection, and route replacement in root snapshot `3309739`; `c23ded6` changed presentation around them. Native reproduction independently records the stuck header, action-row geometry, and lost Federated selection. Evidence: [Accounts:203](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Accounts.kt:203), [Feeds:31](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Feeds.kt:31), [route:266](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/QuanTootApp.kt:266), [baseline failures](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-interactions/native-baseline-first.json:17).
- **[Supported]** Vertical image dismissal is absent from the inherited gallery gesture branch (`4d07a5b`); both directional dismissal tests fail. The first run’s sixth failure was a mismatched zoom accessibility label, not evidence of broken zoom/paging: the calibrated counterexample passes on unchanged application code. Sources: [gesture:297](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/MediaGallery.kt:297), [first run](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-interactions/native-baseline-first.json:17), [calibrated result:17](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-interactions/native-baseline-zoom.json:17).
- **[Direct]** Earlier correction contracts preserve FeedPager/FeedCache membership, pending arrivals, cursor, bounds, position, and StatusProjection freshness/deletions; preserve stable media list/index and fitted originals. The earlier schema restriction explicitly said “in this correction.” Home’s latest geometry correction retains equal widths normally and measured natural-word fallback at large text. Sources: [feed contract:5](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-polish/design.md:5), [media:13](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-polish/design.md:13), [Home:33](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-refinement/design.md:33).

### What We Can Reasonably Infer

- **[Inferred]** Given inherited mechanisms and the bounded prior candidate scope, the symptoms appear to be deferred interaction work, not established product requirements. The current user explicitly requests their correction and supersedes the historical bounded-header choice; no additional user approval is needed.
- **[Inferred]** Unified Profile scrolling likely requires explicit list/status coordinate translation: persistence currently saves raw visible indices but restores through `saved.items[saved.index]`. Header prefixes would change that meaning. Examine restoration, header offsets, reading-older detection, reveal-to-zero, and pagination together; retain account/instance/source isolation and existing feed/source/status/cache owners. Sources: [Feeds:112](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Feeds.kt:112), [pagination:150](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Feeds.kt:150), [reveal:204](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Feeds.kt:204), [cache identity:21](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/FeedCache.kt:21).
- **[Inferred]** Retaining Home selection across child disposal should not require keeping hidden feeds active. Image dismissal should arbitrate vertical movement against paging and zoom/pinch ownership; retain the shared gallery save state, captured save target/retry, feedback, and platform save owner. Sources: [route:266](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/QuanTootApp.kt:266), [gallery:153](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/MediaGallery.kt:153), [paging:196](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/MediaGallery.kt:196).

### What We Don't Know

**[Unknown]** No local record explains the original 380dp choice, intentional Federated resets, or omission of vertical dismissal. Root history starts at a snapshot. Original human wording behind earlier agent plans is unavailable; repeated agent summaries are one evidence chain.

### Sources Consulted

- **Source control/comments:** local log, blame, pickaxe, five complete historical diffs, gallery introduction and root snapshot; investigator report plus source-line spotchecks. Native JSON files are current-task observations, not historical motivation.
- **Issue/ticket tracker:** unavailable; no project source or identity configured.
- **Long-form documents:** versioned local audit/design records only; connected providers have no target document identity.
- **Real-time team chat:** no project source configured.
- **Infrastructure observability:** no project source configured.
- **Error/exception tracking:** no project source configured.
- **Product analytics warehouse:** no project source configured.
- **Remote/PR:** `git remote -v` is empty; no target repository/PR identity. Unrelated private connected sources were not searched.

### Confidence Summary

High confidence in inherited mechanisms, recorded prior scope, and the five reproduced failures. Original motivations and exact prior human rulings remain unknown; the passing zoom/paging counterexample constrains the correction.
