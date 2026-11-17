# Candidate A: a reading surface with inset references

## Usage (caller's view)

Keep all existing status, cache, routing and mutation owners. The UI calls a small number of presentation functions; defaults and saves remain operations on the owners that already exist.

```kotlin
// Feeds.kt: retain the existing selector-to-Timeline mapping and pager lifecycle.
HomeTimelineSelector(selection, onSelect = { selection = it })
key(selection) { /* existing LoginPrompt / FeedScreen mapping */ }

// PostContent.kt: quote loading still reads through StatusProjection.
val target = (embedded ?: loaded)?.let(ui.statuses::project)
if (target != null) {
    StatusCard(ui, target, presentation = StatusPresentation.Quoted)
}

// Feeds.kt: grouping, raw IDs, mark-read and streaming stay where they are.
items(groups, key = { it.first().id }) { group ->
    NotificationItem(
        ui, group,
        handling = group.first().id in handling,
        onFollowRequest = { approve -> /* existing API + pager removal callback */ },
    )
}

// Composer.kt: callers keep ordinary field assignments.
Choice(tr("mastodon_visibility"), draft.visibility, visibilityOptions()) {
    draft.visibility = it // This claims the draft before a suspended defaults response arrives.
}
LaunchedEffect(ui.api, draft) {
    // Independently handle server-info errors as today; do not gate settings on text equality.
    draft.applyDefaults(ui.api.accountSettings())
}
val payload = draft.payload()
val result = ui.api.publish(payload, route.edit?.id, draft.keyFor(payload))
ui.published(route, result) // Existing projection ingestion and destination.

// MediaGallery.kt: feedback is owned and drawn inside the open Dialog.
scope.launch {
    saving = true
    try {
        ui.platform.saveMedia(originalUrl, "quantoot-${media.id}")
        galleryFeedback = GalleryFeedback.Saved
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        galleryFeedback = GalleryFeedback.Failed(strings.text("mastodon_image_save_failed"))
    } finally {
        saving = false
    }
}
```

The examples specify the public surface. The draft does not require a caller-managed “defaults started” flag or mutation counter. The gallery does not require callers to understand Photos transactions or raw file formats. The notification caller passes the groups it already has; it does not build a second notification feed model.

## Problem and grounded constraints

The five presentation complaints share one cause: every object currently has similar visual weight. Wenkai overrides platform typography; Profile offers important operations as text links; Home adds scrollable-tab edge padding despite only three destinations; quotes inherit their parent's surface and author density; notifications expand every actor and referenced post as if each were primary content.

The existing behavioral architecture should survive intact. `StatusProjection` owns canonical status freshness, local edits and deletion tombstones. `FeedPager` and `FeedCache` own row membership and raw notification IDs. `groupFeedNotifications` already groups favourite/reblog events by status and type and deduplicates actor IDs. `QuotedPost` resolves accepted quotes through projection. `ComposerDraft` owns draft payload and idempotency operation IDs. `PlatformServices` already owns save validation and platform adaptation.

Source grounding: `Foundation.kt:151`, `Accounts.kt:151`, `Feeds.kt:22,356`, `FeedCache.kt:241`, `StatusCard.kt:54`, `PostContent.kt:54`, `Composer.kt:21,115`, `MediaGallery.kt:99`, `IosPlatformServices.kt:169`; Flutter `mastodon_notifications_view.dart:537–895`. This candidate reads these boundaries and proposes no new status/cache owner.

## Chosen whole layout

Use one calm, continuous reading surface for primary feed content. Reserve bordered, rounded containers for references inside it. This makes a quote's meaning visible before its text is read. Notifications use the same outer reading surface with distinct event anatomy. Profile becomes an identity header followed by a decisive action row and quieter utility controls. Home is a full-width, equal-width selector above the reading surface.

There is no dashboard, side rail, new navigation destination, design library, or global component framework. Existing composables and the Material theme are enough.

### System typography, rhythm and surfaces

Remove `Res.font.wenkai` and all `Font(…)` application in `QuanTheme`. Use `FontFamily.Default`/the platform default and the existing Material typography roles. Do not load fonts from the network. Do not substitute another bundled font. Remove the unused font import/resource only after confirming it has no other caller.

Use a small scale: screen/identity title 22sp/28sp medium; author name 16sp/22sp semibold; body 16sp/24sp regular; secondary text 14sp/20sp; metadata 12sp/16sp; action labels 14sp/20sp medium. Existing typography roles can express this (`titleLarge`, `titleMedium`, `bodyLarge`, `bodyMedium`, `bodySmall`, `labelLarge`). Set shared roles centrally, not ad hoc on each screen. Chinese, Latin, emoji and fallback glyphs must inherit the same role and scale.

Rhythm is 4, 8, 12, 16, 24dp. Page and row inset is 16dp; adjacent controls have 8dp gaps; inside reference cards use 12dp. All interactive targets are at least 48dp and grow with text. Visual avatars may be smaller inside their targets. Do not clamp the user's font scale or use fixed-height text containers.

Keep the existing green brand color, using color roles rather than per-screen raw colors. Main reading rows use `surfaceContainerLowest`, surrounding chrome uses `surface`, and nested references use `surfaceContainerLow` with a 1dp `outlineVariant` border and 12dp radius. Primary text is `onSurface`; secondary/meta is `onSurfaceVariant`. Primary rows have a restrained divider instead of a border around every object. Thread focus retains the existing `primaryContainer` highlight and does not erase the quote's outline.

Light mode must show the inset through both tonal difference and border. Dark mode uses the same roles: lighter/different inset surface plus a visible outline, never a black-on-black box. Do not encode quote, selected tab, privacy, event type, or error using color alone.

### Profile: identity, decision, utilities

Keep the header/biography data and profile destinations. Put account identity immediately below the cover, then the primary action row before a potentially long biography. This makes the action discoverable even within the existing capped scrolling header. Do not combine the profile feed and header into a new pager implementation during this refinement.

Own profile:

```text
┌───────────────────────────────┐
│            cover              │
│ ◉ Display name                │
│   @account@server             │
│ [ ✎  Edit profile           ] │  filled, full width
│ [ ☆ Favorites ][ ▣ Bookmarks ] │  outlined/tonal equal widths
│ [ ⇧ Share ] [ QR code ]        │  quieter icon + label utilities
│ biography, fields…            │
│ 128 Followers   84 Following  │  counts visually strong, labels quiet
│ Posts  Media  Featured …      │  existing profile destinations
└───────────────────────────────┘
```

Another profile:

```text
│ ◉ Display name     follows you│
│   @account@server             │
│ [ + Follow            ][ … ]  │
│ [ ⇧ Share ] [ QR code ]       │
│ biography…                   │
```

Follow is filled when available, tonal/outlined when Following or Requested, and displays the existing localized relationship state. A single overflow menu contains Mute/Unmute, Block/Unblock and Report using existing actions. Dangerous actions have clear labels and icons; do not expose them as a line of equal-weight text links. Share and QR remain visible utilities and use disabled semantics when the URL is invalid. Menus preserve existing login gates and reporting flow.

A local `relationshipBusy` flag disables relationship mutations during a request and shows progress in the invoking control; update from the server response exactly as today. Do not invent an optimistic relationship repository. Failed actions leave prior state and show the existing visible profile error notice.

At large text, action labels wrap and controls increase height. Favorites/Bookmarks can stack vertically when their measured content cannot fit two columns; the primary row remains full width. Statistics wrap rather than horizontally scroll. Existing five-item profile tabs can remain scrollable: they are not the three-item Home selector.

### Home: three equally available destinations

```text
┌────────────────────────────────┐
│ [   Home   |   Local   | World ]│  full available width; localized labels
│     ━━━━━                      │  selected indicator + semantics
├────────────────────────────────┤
│ ◉ Author               …       │
│ Post body…                     │
│ 12m · Public                   │
│ reply       boost       like   │
├────────────────────────────────┤
│ next post…                     │
```

Use the existing labels Home/Local/Federated; “World” in the wireframe only illustrates the column, not a proposed translation change. A fixed equal-width Material tab row or three weighted tabs removes scroll edge padding and makes every target one third of the width. Use 16dp outer inset only if the whole selector is drawn as a shallow rounded container; do not combine inset with another internal edge pad. Minimum height 48dp, natural height growth, selected text medium weight and primary indicator. All three labels remain visible. At large text, wrap labels to two or more lines with centered text and let the row's height match its tallest label; no ellipsis, hidden destination, or horizontal scrolling. Preserve selected state, signed-out Local default, Home login prompt, `key(selection)` and timeline mapping.

### Primary post versus quoted post

```text
│ ◉ Author name              ⋮  │
│   @author                     │
│ Main post content…            │
│                               │
│ ┌ Quoted post ──────────────┐  │
│ │ ◉ Quoted author       ⋮  │  │  28–32dp avatar, compact identity
│ │   @quoted                │  │
│ │ Quoted content…          │  │
│ │ [media / spoiler / poll] │  │  existing content behavior
│ │ 2h · Followers only      │  │
│ └──────────────────────────┘  │
│ 12m · Mentioned people only   │
│ reply       boost       like   │
```

`StatusPresentation.Quoted` replaces the ambiguous boolean. It controls surface, inset, compact author density and omission of the bottom reaction row and further nested quotes. Existing overflow actions and parent/thread navigation remain available. Keep spoiler reveal, media gallery, polls and safe link handling. Do not silently truncate quote content merely to make the card small. The hierarchy is bounded to one embedded quote, as today; unavailable/loading quotes use the same outer frame so state changes do not jump between unrelated layouts.

Full and quoted status metadata show time, edited state when present, and a localized visibility label with its matching icon. Privacy is explicit in text: Public, Unlisted, Followers only, Mentioned people only (use current translated values). Unknown visibility is a neutral “Visibility unavailable” fallback, never Public. For large text, metadata wraps to the next line. The label gives the post/edit verification an observable privacy surface and tells users what they are reading without opening a menu. A direct post is described as mentioned-people visibility; do not imply encryption or make extra confidentiality promises.

### Notifications: restore the original semantic families

Keep event rows on a common surface separated by dividers, not independent nested cards around all actors and content.

1. **Mentions:** compact reply/event heading with actor and timestamp, then a complete `StatusCard` including content warning, media, actions, thread navigation and quoting. This remains a post that the user can act upon.
2. **Favourite/reblog interactions:** one event heading, a bounded strip of at most eight actor avatars, localized “and N others …” language, then a compact inset author/body preview. The preview has no reaction toolbar, media grid, polls, nested quote, or overflow menu. Its thread target opens the full post. Respect content warnings by showing the warning/hidden-content state rather than leaking hidden body text in a preview. Do not reuse full `StatusCard` with a new pile of flags.
3. **Default events:** follow, poll, update and unknown types remain a compact icon/actor/action/time row. When a status exists the row opens its thread; otherwise it opens the actor profile. An actor avatar/name retains its profile target. Do not append a full post for poll/update.
4. **Follow request:** default header plus equal-width filled Approve and outlined Reject controls, disabled while the existing request handling state is active. Keep the existing API, success removal, error handling and raw event identity.

```text
│ ↪ Alice replied          12m   │  mention
│ ◉ Alice                      ⋮ │
│ full post…                     │
│ reply      boost       like    │
├────────────────────────────────┤
│ ☆ Alice and 11 others          │  interaction
│   favorited your post       1h │
│   ◉ ◉ ◉ ◉ ◉ ◉ ◉ ◉   +4         │  ≤8 visible actors, complete count in text
│   ┌ ◉ Your name · @you ──────┐ │
│   │ compact author/body     │ │
│   └─────────────────────────┘ │
├────────────────────────────────┤
│ + Bob followed you     2h   ◉ │  default
├────────────────────────────────┤
│ + Carol requested to follow ◉ │
│   [ Approve ] [ Reject ]      │
```

The translated sentence count is all actors other than the first, not only actors omitted by the eight-avatar cap. Avatar overflow count is independently `actorCount - visibleCount`. Names, action, timestamp and count have distinct typographic roles. Favourite/reblog icons and colors differ but text also identifies the action. Use existing translation keys, including `mastodon_notification_others`; add any missing new feedback/privacy fallback strings to all supported translations together.

At large text the heading wraps before its timestamp, the avatar strip wraps to a second row if necessary, and the inset's left indentation decreases from the normal 32dp event-icon column to 16dp when needed. Never shrink text to preserve a fixed card height. Every actor avatar that is separately interactive has its own sufficiently large semantics target; if eight large targets do not fit, wrap instead of using overlapping hit regions.

## Shape and module boundaries

Only the following shared presentation surface is needed. Helpers that have one caller remain private to their file.

```kotlin
// Foundation.kt: current QuanTheme is the only theme owner.
@Composable fun QuanTheme(content: @Composable () -> Unit) = TODO("not implemented")

// Feeds.kt: private; preserves source selection at its caller.
@Composable private fun HomeTimelineSelector(
    selected: Int,
    onSelect: (Int) -> Unit,
) = TODO("not implemented")

// StatusCard.kt / PostContent.kt: two presentations, not independently combinable flags.
enum class StatusPresentation { Full, Quoted }
@Composable fun StatusCard(
    ui: UiContext,
    entry: Status,
    highlighted: Boolean = false,
    presentation: StatusPresentation = StatusPresentation.Full,
) = TODO("not implemented")

@Composable private fun StatusMetadata(status: Status) = TODO("not implemented")

// Notification presentation may live in one new NotificationItem.kt file if Feeds gets crowded.
// Do not create a presentation-model repository. Input is an existing nonempty FeedCache group.
@Composable internal fun NotificationItem(
    ui: UiContext,
    group: List<Notification>,
    handling: Boolean,
    onFollowRequest: (approve: Boolean) -> Unit,
) = TODO("not implemented")

@Composable private fun InteractionStatusPreview(ui: UiContext, entry: Status) =
    TODO("project entry, render author/body/warning, route to Thread")

// MediaGallery.kt: private feedback, with the captured retry target kept in gallery state.
private sealed interface GalleryFeedback {
    data object Saved : GalleryFeedback
    data class Failed(val message: String) : GalleryFeedback
}

// Accounts.kt: local composition and local relationshipBusy; no shared profile-action registry.
// PlatformServices.kt: signatures unchanged, implementations retain platform details.
suspend fun saveMedia(url: String, name: String): Unit
suspend fun saveImageBytes(bytes: ByteArray, name: String): Unit
```

`StatusPresentation` has the same public visibility as the existing `StatusCard`; the private helper declarations remain private. These declarations are shape sketches, not production bodies.

Interface depth: `NotificationItem` hides visual family selection, actor count/strip and preview rules behind existing data and one callback. It never fetches, groups, marks read or removes rows. `StatusCard` continues to hide projection, status actions and content behavior, and its single presentation argument expresses a semantic role. No boolean combinations can accidentally turn an interaction preview into a complete post. Existing `UiContext` routing remains the presentation boundary.

### ComposerDraft: causal defaults and semantic dirty state

Keep one `ComposerDraft` per existing draft key and keep `keyFor` idempotency behavior. Add only `applyDefaults(settings)` and `payload()`; the public fields can remain source-compatible with current call sites.

```kotlin
class ComposerDraft(route: Screen.Compose, me: Account?) {
    // Existing properties remain, with backed setters.
    var textInput: TextFieldValue
    var text: String
    var spoiler: String
    var sensitive: Boolean
    var visibility: String
    var language: String
    var quotePolicy: String
    var attachments: List<MediaAttachment>
    var pollEnabled: Boolean
    var options: List<String>
    var multiple: Boolean
    var duration: Int
    var hideTotals: Boolean

    val dirty: Boolean get() = TODO("payload differs from initial/defaults baseline")
    fun applyDefaults(settings: AccountSettings): Unit = TODO("synchronous guarded apply")
    fun payload(): StatusDraft = TODO("same payload mapping currently in publish")
    fun keyFor(payload: StatusDraft): String = TODO("existing behavior")
}
```

Implementation can use one private immutable `DraftValues` state and explicit getters/setters. Every public setter replaces that state and closes a private `defaultsEligible` flag. There is no custom generic state framework or externally visible revision token. Updating only text selection/composition without changing text does not count as a payload edit. A user's settings choice claims the draft even when selecting the current value; this prevents a later server preference from overriding deliberate intent.

Contract:

- Start from edit/reply/account-source values exactly as today. An edit draft never accepts account defaults. A reply's inherited visibility is never replaced by account defaults.
- Fetch may start at any time. **Eligibility is checked inside `applyDefaults` after suspension has completed**, not before the network call. Application is synchronous on the same UI state context as field updates.
- At most one account-default application is accepted. Apply only while `defaultsEligible` is still true. The first user payload edit or explicit settings selection permanently closes eligibility, even if the user later reverts. Retained drafts therefore cannot be overwritten on re-entry.
- A successful application changes language, quote policy, sensitive and (only for ordinary new posts) visibility together, then updates the private clean baseline and closes eligibility. Defaults are clean initialization, not user edits. Calling it twice is a no-op after the first result.
- `dirty` compares the current normalized `StatusDraft` with that baseline. Include visibility, language, sensitive, spoiler, quote policy, attachment IDs and the effective poll payload as well as text. This fixes settings-only drafts and avoids treating an untouched edit with existing attachments/spoiler/poll as dirty. Cursor/selection movement is not dirty. Reverting to the baseline clears dirty but does not reopen default eligibility.
- `payload()` is the one mapping used by publish, dirty comparison and operation-ID input. This avoids a second hand-maintained list of fields. Constructor setup can derive the baseline once; it must not go through user-edit setters.
- If publish fails, keep the draft and its operation ID for the same payload. A changed payload obtains a new key exactly as today. Successful creation/edit continues through `ui.published`; deletion remains in its current status action owner.

This is deliberately conservative: typing before preferences arrive freezes the initial account-source defaults for that draft. It preserves the user's visible starting state and fixes the causal race with one owner and one eligibility bit. A more complex per-field touched/default source system is unnecessary for this bounded refinement.

### Original image resource: a small native correction

Keep the existing two `PlatformServices` signatures and Android behavior. `IosPlatformServices.saveMedia` still validates the HTTP(S) URL, rejects credentials/invalid hosts, checks status, enforces declared and streamed 30 MiB limits, closes the client, then calls `saveImageBytes`. Do not widen gallery saving to video or arbitrary resources in this change.

`saveImageBytes` continues to reject empty/oversized/unsupported images. It may use `UIImage.imageWithData` for the existing decodability check, but **must not use that decoded UIImage as the Photos creation source**. Submit the original `NSData` to a `PHAssetCreationRequest` photo resource within the existing `PHPhotoLibrary.performChanges` transaction. Use resource-creation options to set a sanitized original filename derived from `name`; preserve a supported existing extension or infer a matching extension/type from the validated bytes. Do not claim a `.jpg` filename for PNG/GIF/HEIC bytes, trust only a URL suffix, or re-encode a static first frame.

This is one private iOS implementation boundary: original bytes → validated image resource → Photos transaction. Verify the exact Kotlin/Native Photos resource-creation symbols against the installed SDK during implementation. All size, address and content checks remain before committing a Photos change. Return success only from the successful Photos completion callback. Report permission-denied and other Photos failure as failures; do not turn cancellation into a success message. Preserve `continuation.isActive` guards.

No new cross-platform “saved asset” object, save repository, or asset-ID cache is needed. The user-facing operation requires completion, while root's native verification can find the newly created resource by the before/after asset set and unique requested filename. If an identifier is useful to native verification, capture it privately during the transaction; it does not justify enlarging `PlatformServices`.

In the gallery, draw progress and feedback inside the full-screen dialog. Keep the save target captured at tap time while paging may continue. Disable repeated save taps until completion. Success is a visible “Saved to Photos”/existing localized saved message in a persistent-until-dismissed compact banner or a sufficiently long in-dialog snackbar, announced through accessibility. Failure is visible with Retry and Dismiss; Retry saves the same captured resource. Do not forward all save errors exclusively to `ui.error` or success to `ui.message` behind the dialog. Never display raw response bodies, full URLs, credentials or native diagnostics. Feedback remains safe over the black gallery surface in both app themes, respects safe-area insets, and wraps at large text.

## Alternatives considered and decision

**Selected: continuous reading surface with inset references.** A primary feed row is not boxed; secondary quoted/interaction content is. Profile actions follow the same primary/secondary distinction, and Home has three balanced positions. This solution hides semantic presentation rules in two existing boundaries and one notification composable. It directly addresses all five complaints without increasing navigation or state owners.

**Alternative B: universal card system.** Give every post, notification, actor cluster and Profile action group a rounded elevated card, with quoted cards using slightly different fill and elevation. This is a structurally distinct “cards of cards” page. Its public component system can make spacing uniform, but callers must understand and coordinate card depth/elevation/surface choices. It preserves the ambiguity that caused the issue, wastes width for multilingual content, and is fragile in dark mode where elevation differences are subtle. Rejected despite its easy incremental implementation.

**Alternative C: dashboard with a profile action grid, Home source tiles and notification inbox sections.** This offers clear entry points but changes the product's reading flow: feeds begin below destination tiles, notifications are regrouped by event family rather than preserving the existing chronology, and additional compact/full variants become caller responsibilities. It would require navigation and grouping policy changes outside this brief. Rejected for exposing more policy and moving behavior unnecessarily.

This artifact is one independent arena candidate. Cross-candidate synthesis belongs to the root orchestrator; no other candidate was inspected or copied.

## Tradeoffs accepted

- Accept flatter full-width primary rows in exchange for unambiguous reference hierarchy and more reading width.
- Accept a compact interaction preview without inline reaction tools in exchange for matching the original client and keeping grouped notifications scannable; its thread remains one tap away.
- Accept the existing separately scrolling Profile header in exchange for a bounded redesign; moving actions before long biography content reduces its practical cost.
- Accept conservative all-or-nothing account-default hydration after the first user edit in exchange for a small causal contract that cannot overwrite explicit intent.
- Accept native before/after asset verification in exchange for preserving the cross-platform save API's simple completion contract.

## Red-flag screen

No added status/cache owner; no second notification grouping algorithm; no importable presentation repository; no pass-through save service; no independent flags for full/quote/interaction status states; no per-screen typography overrides; no dirty-field list separate from publish payload. Existing temporal boundaries are corrected inside their owners. Dominant paths remain three files or fewer: feed → notification/status composition → projection already held by `UiContext`; gallery → platform service → Photos; composer → draft payload/API → existing published reducer.

## Validation and milestone boundaries

1. **Presentation milestone:** system-font screenshots and actionable Profile controls; balanced Home labels; quoted posts and every notification family in light, dark and large-text configurations. Check target size, wrapped labels, chronology, disabled state, content-warning behavior, and distinct click destinations. No account writes are required for these visual checks. Include fixtures for groups of 1, 8 and 9+ actors and poll/update default rows. Preserve the existing shared/read-only native regression baseline.
2. **Draft/save milestone:** meaningful common tests cover delayed defaults after privacy selection (including select-then-revert), clean defaults, retained drafts, edit/reply exceptions, settings-only dirty changes and retry key identity. Native save validation retains malformed URL, non-2xx, oversized declared/streamed data, empty/unsupported bytes and Photos failure behavior. Prove original-resource identity using a suitable saved fixture, not only a decoded pixel comparison.
3. **Authorized native flow milestone, root only:** keep the authenticated iPhone 17 Pro simulator/account intact. Through app UI create one unique marker with direct visibility and no additional recipients; verify metadata, open that exact post, edit only it, confirm changed text and retained visibility after a fresh read, then delete only it and verify absence after refresh/process restart. Save one gallery image; verify both visible in-dialog feedback and the actual new Photos asset. Record identifiers without tokens, exact observations and limitations. Commit milestones only after the corresponding checks pass.

Do not touch pre-existing posts, reset the simulator, operate the account in this design task, or read/print bearer tokens. UI actions and Photos inspection are serial resources owned by root.

## Implementation reconciliation

Design only; no production changes or simulator/account operations performed. Exact Photos interop spelling and localized fallback string names must be reconciled against compilation during implementation. These are local implementation choices; any change to notification family semantics, draft default eligibility, mutation ownership or save completion behavior requires updating this same contract before closing that implementation unit.

## Open questions and risks

No user clarification is required to start. Implementation should resolve: does the installed Kotlin/Native SDK expose the original-data photo resource initializer with the expected parameter labels, and do the existing supported gallery formats preserve their original resource type through Photos? Does platform text scaling reach Compose text consistently on both targets? These are verification questions for the implementer, not reasons to postpone the authorized work.

## Next implementation step

Remove the forced font and implement the full/quoted status surfaces plus visible metadata first, then use that same visual language for notifications, Home and Profile before validating writes.
