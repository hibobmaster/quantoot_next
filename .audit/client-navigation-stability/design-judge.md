**Recommendation.** Use candidate A’s coordinated header and body as the base, subject to a native interaction prototype before wiring the feed lifecycle. Candidate B’s footer floor makes the tab offset attainable, but its current reserve can leave a blank end viewport. With one status, the footer reserves `V − 48dp`, then the existing 88dp bottom padding and any extra card height add to that. Subtracting the 88dp fixes only part of the overrun; a simple formula cannot account for tall or changing cards without measurement.

| Candidate | Geometry | Reading and gestures | Feed invariants | Settings routes | Bounded design | Total |
|---|---:|---:|---:|---:|---:|---:|
| A | 1 | 1* | 1 | 1 | 0 | 4/5 |
| B | 1 | 0 | 1 | 1 | 1 | 4/5 |

`*` Candidate A describes the needed fling handoff, refresh ordering, clipping, and accessibility behavior, but none is runtime-verified. This is a design score, not a claim that its scroll controller works on iOS. Candidate B’s geometry arithmetic is sound, but the resulting tail can exceed its stated “up to nearly one viewport” tradeoff because the proof excludes existing padding and natural status height.

A preserves the tab position independently of body height and provides a full post viewport after header collapse. It also stops the selected feed when hidden and keeps cache identity, status coordinates, pager cursor ownership, and projection freshness. Candidate B offers native list gestures with a smaller API, but its blank tail misses the useful-reading-area criterion as written.

**Two changes to carry forward.**

- Give A’s header a typed selection interface like B’s `leading(selection, select)` signature. As written, A owns selection but supplies a zero-argument header lambda, so the tabs cannot read or change that selection.
- Drop A’s `ProfilePresentations` registry and its promise to retain the selected category across child routes. That behavior is outside the stated request and the existing route lifecycle returns to Posts. Keep the source cache position behavior.

Candidate A’s Settings route handles title and back ownership, all seven child pages, and direct child navigation. Keep its guest guard for account-only pages. Candidate B’s version also works for index-originated children, but should make the direct guest-route guard explicit.

**Verification gates.** Before accepting A’s controller, verify the pinned Compose 1.12.2 API and run native checks for header-origin fling into posts, reverse fling, pull-to-refresh ordering, accessibility scrolling and bring-into-view, clipped-item pagination, large-text reflow, and empty/loading transitions. Also verify rapid source switches and delayed cache writes, all Settings child/back paths, guest visibility, locale changes, and unchanged Home source behavior. No build or UI result from either candidate is evidence of these gates passing.

One source gap is implementable once the selection callback is added. The remaining A risks are verification risks, not demonstrated runtime behavior.
