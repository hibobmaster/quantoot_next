# Candidate B: one reading column, contextual surfaces

## Usage: caller's view

```kotlin
// Feeds.kt: retain existing pager, keys, projection, stream and read handling.
PrimaryTabRow(selectedTabIndex = selection) {
    sources.forEachIndexed { i, source ->
        Tab(selected = selection == i, onClick = { selection = i },
            text = { Text(source.label) })
    }
}
items(groups, key = { it.first().id }) { group ->
    NotificationRow(ui, group, handling = group.first().id in handling,
        onFollowRequest = { approve -> /* existing request and pager removal */ })
}

// PostContent.kt: quote resolution remains with StatusProjection.
QuotedPost(ui, status) // internally renders StatusCard(ui, target, nested = true)

// Composer.kt: only the draft owns current values, default provenance and baseline.
val draft = remember(key) { ui.drafts.getOrPut(key) { ComposerDraft(route, ui.me) } }
LaunchedEffect(ui.api, draft) {
    val settings = ui.api.accountSettings()
    draft.applyDefaults(settings) // checks current provenance after suspension
}
Choice(..., draft.visibility, ...) { draft.chooseVisibility(it) }
if (draft.dirty) showDiscardConfirmation()

// MediaGallery.kt: explicit feedback belongs inside the full-screen dialog.
try {
    ui.platform.saveMedia(originalUrl, "quantoot-${media.id}")
    saveFeedback = GalleryFeedback.Saved
} catch (e: CancellationException) { throw e }
catch (e: Exception) { saveFeedback = GalleryFeedback.Failed(e.message) }
```

## Problem and existing ownership

Five rough edges make the same feed harder to scan: forced typography, undifferentiated profile actions, crowded feed selection, a quote that visually competes with its parent, and identical notification cards for different events. UI changes must not redefine network or freshness behavior. `FeedPager`/`FeedCache` own membership, raw notification IDs, grouping, and pagination. `StatusProjection` owns canonical status updates, deletion and quote resolution. ComposerDraft owns draft payload and operation IDs; UI owns presentation and transient gallery feedback. Existing navigation, login checks, mutation handling, URL validation, and stream ingestion remain on their existing paths. The design fits the existing Material theme rather than adding a library.

## Shape and boundaries

```kotlin
// Feeds.kt: private presentation only; group is the existing nonempty FeedCache group.
@Composable private fun NotificationRow(
    ui: UiContext, group: List<Notification>, handling: Boolean,
    onFollowRequest: (Boolean) -> Unit,
) { /* not implemented */ }
@Composable private fun InteractionPreview(ui: UiContext, entry: Status) { /* not implemented */ }

// Composer.kt: field setters are the ONLY UI path for defaults-covered fields.
class ComposerDraft(route: Screen.Compose, me: Account?) {
    val visibility: String get() = TODO()
    val language: String get() = TODO()
    val quotePolicy: String get() = TODO()
    val sensitive: Boolean get() = TODO()
    fun chooseVisibility(value: String) { /* not implemented */ }
    fun chooseLanguage(value: String) { /* not implemented */ }
    fun chooseQuotePolicy(value: String) { /* not implemented */ }
    fun chooseSensitive(value: Boolean) { /* not implemented */ }
    fun applyDefaults(settings: AccountSettings) { /* not implemented */ }
    val dirty: Boolean get() = TODO()
    // Existing payload state and keyFor(payload) stay here.
}

// MediaGallery.kt: transient dialog state, not a global status owner.
private sealed interface GalleryFeedback {
    data object Saved : GalleryFeedback
    data class Failed(val detail: String?) : GalleryFeedback
}
```

Internal field state needs current value, baseline value, and whether the user has touched it; this can be a private small helper or four explicit fields, whichever integrates most cleanly with Compose state. Fields seeded from edit or reply have route precedence, as below. `applyDefaults` updates a field and its baseline together only when eligible and untouched **at completion**, even if the request began before the choice. A touched field is never replaced even if it is changed back; this tracks causality rather than equality. Repeated/late calls are harmless for touched fields. Once defaults are applied, their values are baseline, so the defaults alone do not trigger discard.

- New post: apply settings privacy, language, sensitive and quote policy independently when each remains untouched. A user's change to one field does not block unrelated defaults.
- Reply: preserve reply visibility; apply other defaults only when untouched. Edit: preserve edit-derived fields and never apply account defaults. Route-specific initial values are authoritative. Retain existing behavior for edit quote policy unless a reliable source is already supplied.
- Baseline snapshot includes text, content warning, sensitive, visibility, language, quote policy, attachment identity/alt changes, poll enabled and poll inputs, including duration/multiple/hide totals. Compare current effective payload to baseline, not nonempty checks: an unchanged edit with existing attachments/poll is clean; a settings-only choice is dirty; reverting to baseline is clean. Avoid treating cursor/selection changes as edits. Rebase only each untouched defaults field, never a whole snapshot after other edits.
- Ensure `applyDefaults` is invoked against the same draft object after the suspend returns. Cancellation and account/route transitions must not apply values to a different draft. Keep `keyFor(payload)` unchanged so a changed payload receives a new operation ID.

This is one public draft operation per user intent and one default application; the implementation hides race handling, while the callers cannot silently bypass provenance through public setters. No extra draft owner, status cache, presenter hierarchy, or synchronization layer.

### Visual language and responsive behavior

Keep existing light/dark palette, switch `QuanTheme` to platform system font (`Typography()` with no bundled family); use `sp` Material styles and system font scaling. Standard rhythm: 4/8/12/16/24 dp; outer feed gutter 8–16 dp, post interior 14–16 dp, 8 dp between related rows, 12–16 dp between groups. Reading hierarchy: titleMedium for profile name, bodyMedium or bodyLarge for post text, bodySmall for handles/timestamps, labelMedium for type labels and actions. Keep tap areas at least 48 dp even where icons are 18–24 dp. Use semantic Material colors for text and surfaces, not hard-coded dark-mode shades; `outlineVariant` and a distinct `surfaceContainer` color bound quotes in both themes. Verify text and indicator contrast in each palette.

Profile: make Edit profile a prominent filled/tonal primary action for the owner; use outlined labelled icon buttons for Favorites and Bookmarks, with QR/Share as compact labelled secondary controls. On others' profiles, Follow/Requested/Following is the prominent filled/tonal control; group mute, block and report in an overflow menu, preserve current relationship operations, login requirement and report flow, and disable duplicate relationship mutations until completion. Do not convey danger by placement or color alone. Preserve QR, Share, counts, fields, and existing routes. Let action rows wrap or stack instead of clipping, and let the header scroll with large text without hiding controls.

Home: replace the three-item scrollable row with equal-width full-available-width tabs; selected item has both visible indicator and semantic selected state. Keep source mapping and callbacks. At large font scale, permit wrapping and a taller tab row; never shrink text or reduce target below 48 dp. If labels cannot fit even wrapped on the narrowest supported width, use a measured accessible scrollable fallback rather than clipping or changing the source mapping.

Nested quote: keep `StatusCard(... nested = true)` and the current projection, navigation, content warnings, media, and poll logic. Give nested card an inset rounded outlined surface and a small identity row (e.g. 28 dp avatar) against parent at 44 dp, with 12 dp inner padding. Show author, body, timestamp and privacy where applicable, keep quote tap to thread and author tap to profile independently accessible. Preserve existing no action bar/no recursive quote rule and do not introduce duplicate mutation controls; retain other currently supported routes. Use the existing card's compact branch rather than a second status model.

Show post privacy metadata adjacent to timestamp: localized visibility label plus icon with accessibility text, including Direct/Followers/Unlisted/Public. The composer choice shows selected visibility in text, so the author can inspect direct visibility both before publishing and on the resulting post. Do not infer confidentiality beyond server visibility.

### Notification semantics

Each group is rendered once with distinct leading event icon and localized action label, relative timestamp, and navigable actors. The existing grouping determines order and membership; use up to eight avatar links with localized remaining-actor count when more exist, and meaningful individual accessible labels. No new grouping cache. Keep `group.first().id` list key, raw IDs, pagination, pending reveals and mark-read behavior.

- Mention: event header and full projected `StatusCard`, retaining all standard actions, content warnings and quote behavior. If status missing, render a safe default event row.
- Favourite and reblog: actor grouping plus an indented compact projected author/body preview, bounded and tappable to the thread. It has no action controls, uses safe existing rich content rendering, respects content warnings and sensitivity, and does not expose hidden body before reveal; missing/deleted status degrades to the header. Do not use a full status card.
- Follow, poll, update and unknown: compact icon, actor, localized event and timestamp row; when status exists navigate to its thread, otherwise actor profile. Unknown falls back to readable event type. Follow requests use a row with filled Approve and outlined Reject, 48 dp targets, preserving the existing async per-request busy/error/removal logic.

Allow long localized labels/actor names to wrap, compact previews to grow vertically, and stacked buttons at large text sizes. The element order remains intelligible with screen readers; icon descriptions are absent when adjacent text already names the event, while actor avatars and tap actions have clear labels.

### Image save boundary and feedback

Keep `PlatformServices.saveMedia(url, name): Unit` and `saveImageBytes(bytes, name): Unit` unless the native verification harness requires an identifier; this is the smallest boundary because gallery only needs success or an exception. iOS continues enforcing URL scheme/host/no credentials, 2xx response, declared and streaming 30 MiB limits, nonempty size and supported-image decode validation. After validation, use `PHAssetCreationRequest.forAsset()` and `addResource` with the **original validated bytes** and `PHAssetResourceCreationOptions.originalFilename` derived from a safe name plus appropriate original format extension. Do not serialize `UIImage`, as that changes the resource. Let Photos accept/reject the original format and signal success only in the completion callback. Keep any temporary resource alive through completion if a file URL is needed and clean it up afterward; byte data avoids a temp file when feasible. Preserve cancellation semantics, avoid double resuming continuations, and surface accurate Photos failure without declaring all errors a permission denial. Do not weaken validation for animated/unsupported content or introduce another download path.

Inside the gallery dialog, show a dismissible, accessible status strip or snackbar over the black surface for completed save or actionable failure, with adequate contrast; disable Save while pending, clear stale feedback when starting a new save, and allow retry. Capture the selected URL/id at click time so changing pages cannot relabel the in-flight save. Do not report success until Photos has committed. Preserve close/share/pager behavior.

## Wireframes

```text
PROFILE (own)                     HOME / selector
[cover]                           ┌────────┬────────┬────────┐
[avatar] Name                    │ Home   │ Local  │Federated│
         @handle                 └━━━━━━━─┴────────┴────────┘
Bio                               [post surface             ]
[ Edit profile             ]      [  ╭────────────────────╮ ]
[☆ Favorites] [▣ Bookmarks]       [  │ avatar  quoted name│ ]
[QR] [Share]                      [  │ nested quoted text │ ]
counts / fields                   [  ╰────────────────────╯ ]

NOTIFICATIONS                    LARGE TEXT
★ [a][b][c] + 5 others          [ Home   ][ Local   ][ Federated ]
  favorited your post             each label may wrap; row grows
  ┌─ [avatar] Author @acct ──┐   [ Edit profile                 ]
  │ compact content preview  │   [ Favorites ] [ Bookmarks     ]
  └──────────────────────────┘   or stacked when width requires
↩ [avatar] replied               no fixed-height text clipping
  [full status + actions]         error/success visible in gallery
👤 [avatar] followed you · 2h
👤 [avatar] requested · 3h
  [ Approve ] [ Reject ]
```

In light theme the parent is `surfaceContainerLowest`, quote `surfaceContainerLow` or `surfaceContainer` with outline; dark uses those same semantic roles, with an outline strong enough to remain visible independent of luminance order. Large fonts expand vertically and retain the boundary; do not rely on hue alone. Gallery feedback remains visible against its black surface with on-surface contrast.

## Alternatives and synthesis decision

Chosen: contextual surfaces in one vertical reading column. Statuses remain the interaction-rich unit, while notifications choose a compact row or preview by semantics. The existing full card and quote path hold status behavior, the small notification renderer hides presentation choices, and the draft itself hides async default races. This has a small caller surface and keeps ownership legible.

Rejected: a unified inbox of identical full status cards for all events. It hides which type of interaction happened, repeats action bars and increases screen height; callers still need type-specific exceptions for requests and events with no status, so its apparent single interface is shallow. Rejected: a dashboard of independent Profile/notification tiles with separate view-models and a separate compact-status cache. It could provide visual richness but exposes synchronization and routing to callers, introduces competing freshness owners, and splits an existing stream-driven list into unrelated state. Both fail the split-ownership or two-ways-to-do-one-task checks. The chosen nested branch is deliberately not a new recursive card API, and preview is read-only rather than a second status owner.

## Tradeoffs accepted

- We accept a local conditional rendering branch for notification semantics in exchange for keeping the existing grouping and routing contracts.
- We accept internal per-field provenance in ComposerDraft in exchange for deterministic late defaults and accurate discard prompts.
- We accept semantic compact previews with fewer controls in exchange for easily scanned interactions; the full post remains one tap away.
- We accept platform Photos rejection of unsupported original resource formats in exchange for preserving validated original bytes without silent conversion.

## Validation and reconciliation

Run shared tests plus targeted behavior tests: delayed defaults after privacy choice, independent untouched defaults, reply/edit precedence, unchanged edit with media/poll, settings-only changes and reverts. Test grouped notification mapping including >8 actors, mention, missing/deleted status, request busy/actions; projected status changes should remain visible. Verify both palettes and enlarged system fonts on actual UI, especially nested outline, tab fit, profile buttons, readable privacy and gallery feedback. Root owns the serial UI verification: create a unique direct post with no recipients, inspect visibility, edit only it, delete only it, and confirm absence via a fresh read/restart; save a gallery image and inspect the actual new Photos asset. Existing 90 shared and seven read-only native scenarios remain the regression baseline. No implementation deviations have been accepted yet.

## Open questions and risks

- Does Compose's platform typography resolve to the native system family consistently on both target platforms and scale according to accessibility settings? Verify on device.
- Which filename/UTType combination does Photos preserve for each supported image format? Verify with the actual saved asset and keep errors explicit if the platform rejects it.
- Can the existing rich content renderer safely constrain compact previews while retaining spoiler and link semantics? If not, use a safe plain-text body with content warning rather than an uncontrolled full card.

## Next implementation step

Implement and test ComposerDraft's field-level defaults and baseline rules before performing any authenticated write.
