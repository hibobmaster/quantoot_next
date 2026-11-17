### The Question

Profile category changes must preserve the visible header and tab geometry. Settings children must show one page title and use one standard back action that returns to the Settings index. The architecture needs to meet those requirements while preserving existing reading, feed, and account behavior. [Current brief](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-navigation-stability/brief.md:3).

### The Code in Question

Profile category branches live in [Accounts.kt](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Accounts.kt:343). Source-keyed viewport creation and restoration live in [Feeds.kt](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Feeds.kt:95), with coordinates and persistence in `FeedPosition` and `FeedCache`.

Settings divides subsection state in [Settings.kt](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Settings.kt:22) from route title and back handling in [AppShell](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/QuanTootApp.kt:157). [Screen.Settings](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Foundation.kt:53) carries no subsection.

### What We Found

- **[Direct] The reported failures are reproduced.** Empty Media moved the category row from Y 373 to Y 458, exceeding the native test’s four-point tolerance. The Settings child retained its second `‹ Settings` control. Both intended assertions failed. [Native results](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-navigation-stability/native-baseline.json:52).

- **[Direct] The previous Profile design chose unified scrolling to reduce interaction complexity.** Its recorded comparison preferred ordinary list behavior over separate collapse state, fling handoff, refresh ordering, and restoration coordination. [Candidate A comparison](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-interactions/design-a.md:133), [candidate B comparison](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-interactions/design-b.md:95).

- **[Direct] Per-source viewport restoration and the independent About list were recorded agent choices.** Candidate A explicitly retained those behaviors. The accepted synthesis limited its promise to that earlier correction and accepted nonsticky tabs within that scope. These documents do not establish an attributable human requirement to retain those choices permanently. [Candidate A](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-interactions/design-a.md:94), [accepted scope](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-interactions/design.md:5), [persistence scope](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-interactions/design.md:13).

The architecture contract has three directly recorded parts:

- **[Direct] Preserve.** Keep continuous header-to-post reading, full-height posts, zero tab edge padding, horizontal access to later categories, accessible actions, and existing category and Activity-filter semantics. Keep account, instance, and source isolation, status-only cache coordinates, `FeedPager` cursor ownership, and `StatusProjection` freshness. Preserve Settings child functionality, authentication gates, forms, and translations. [Current constraints](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-navigation-stability/brief.md:9), [accepted feed contract](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-interactions/design.md:7), [presentation contract](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-visual-polish/brief.md:9).

- **[Direct] Change.** Category transitions must preserve visible Profile geometry across all five categories, including short, empty, and loading bodies. Settings must give the child title and standard back action one owner. Back from a child must return to the Settings index. [Current brief](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-navigation-stability/brief.md:11), [native acceptance checks](/Users/bobmaster/Projects/QuanToot_Next/iosApp/QuanTootUITests/ClientNavigationStabilityTests.swift:24).

- **[Direct] Avoid.** Do not restore the capped independently scrolling header, introduce nested vertical scrollers, reinterpret persisted status pixels as header pixels, or keep hidden feeds active solely to retain presentation state. [Current brief](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-navigation-stability/brief.md:9), [accepted coordinate contract](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-interactions/design.md:9), [hidden-feed constraint](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-interactions/design.md:13).

### What We Can Reasonably Infer

- **[Inferred] Risk.** Moving viewport ownership appears necessary to consider, but sharing feed data or keeping every category active does not follow from the geometry requirement. The source currently ties list creation to category and source identity, while the current brief preserves source isolation. A candidate should explicitly separate visible Profile geometry from each source’s rows, cursor, and cache. [Profile keys](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Accounts.kt:368), [feed identity](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Feeds.kt:198).

- **[Inferred] Risk.** Any viewport change may disturb restoration, pending reveal, or pagination unless it retains their coordinate contract. Leading offsets and status offsets are distinct, deleted anchors fall to surviving statuses, and pagination counts statuses and the footer. The 400 ms cache-write delay also makes rapid switching a separate case from settled restoration. These are code-derived risks, not additional reproduced failures. [Position rules](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/FeedPosition.kt:21), [cache persistence](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Feeds.kt:160).

- **[Inferred] Risk.** Removing only the inner Settings control would likely preserve the navigation defect. The local subsection is invisible to AppShell, whose standard back action delegates to route back. The corrected owner must know whether it is leaving a child or leaving Settings. [Local subsection](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Settings.kt:23), [standard back delegation](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Foundation.kt:75).

### Competing Hypotheses

- **[Inferred] Source restoration contributes to the Profile jump.** Category changes replace the feed subtree and restore an independently cached viewport. That can change common-header position. The native failure does not isolate its contribution. [Restoration](/Users/bobmaster/Projects/QuanToot_Next/shared/src/commonMain/kotlin/com/quanquan/quantoot/ui/Feeds.kt:153).

- **[Inferred] Short-content clamping contributes to the Profile jump.** Media was empty, and the list provides no minimum body extent. Clamping an offset within the header does not establish that the complete list can attain that offset. This explanation fits the observation, but the baseline alone does not distinguish it from restoration. Both may contribute. [Source analysis](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-navigation-stability/grounding.md:40), [empty-Media observation](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-navigation-stability/brief.md:5).

### What We Don't Know

- **[Unknown]** The precise contribution of restoration and content-height clamping to the observed Profile transition remains unisolated.
- **[Unknown]** No searched local commit or document explains why repeated Settings wording was desirable. Original attributable human wording behind the earlier agent plans remains unavailable.
- **[Unknown]** External rationale remains a coverage gap. An empty Git remote and absent PR or ticket references prevent a project-bound issue search. Generic GitHub and ChatGPT Pages tools have no linked repository or Space identity. Unrelated account-wide content was not searched. No team-chat, observability, error-tracking, or analytics MCP is available.

### Sources Consulted

- Local Git history, blame, pickaxe searches, relevant complete diffs, comments, and related tests, as recorded by the investigator. Anchor metadata was spot-checked for `3309739`, `202cf7b`, `f4ee984`, `4ae09f0`, `52fcc31`, and `b9d72fa`. [Investigator coverage](/Users/bobmaster/Projects/QuanToot_Next/.audit/client-navigation-stability/history.md:29).
- Committed interaction candidates, accepted design, architecture documentation, visual-polish records, source grounding, current brief, and native result JSON.
- Issue tracker and connected long-form sources were unavailable for a project-bound search. Team chat, observability, error tracking, and analytics were unavailable.

### Confidence Summary

Confidence is high in the reproduced failures, current ownership split, and recorded agent design tradeoffs. Confidence is moderate in the Profile causal explanations, and the original motivation for Settings duplication remains unknown. Review baseline `b9d72fa` adds reproduction artifacts and tests over `df7c305`; it does not change the 0.6.4 build 10 product implementation.

## Subsequent native evidence

After this synthesis, root kept the empty Media category active and dragged upward without changing its source. The tab row remained Y458 before and after the drag, failing the expected movement beyond Y418. This separately confirms that the empty body cannot support the requested header movement in the current layout. It does not quantify restoration versus clamping in the original category transition. See native-baseline-empty.json and screenshots/baseline-empty-media-scroll.txt.

## Planning clarification

The synthesis phrase about nested vertical scrollers does not establish a human prohibition on a coordinated collapsing header. The preserved user experience is one continuous gesture and no independently scrolling capped profile header. Two nested vertical lists or separate competing reading regions remain excluded. A naturally measured coordinated header with one lazy body is eligible for the current architecture comparison, as stated in design-task.md. The current no-jump requirement may supersede prior bounded agent choices.
