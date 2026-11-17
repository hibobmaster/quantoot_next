# Refinement grounding

The source candidate starts at `858ab35`. Two read-only explorers traced presentation and native write/save behavior, and a separate how explainer reconciled their findings.

`UiContext` connects authenticated API access, navigation and platform services. `StatusProjection` remains the only owner of status freshness and mutation results. `FeedPager` and `FeedCache` retain membership, pagination, pending arrivals and raw notification IDs. `groupFeedNotifications` already matches Flutter grouping; presentation should consume its groups without replacing it.

`QuanTheme` centrally forces Wenkai. `ProfileScreen` has a separately scrolling header capped at 380 dp; own-profile and secondary actions are text buttons, while Follow already uses a filled button. Relationship operations lack busy state. `HomeScreen` maps the correct sources through a scrollable tab row with default edge padding.

KMP notifications expand every actor into a full AccountRow and every status into a full StatusCard. Flutter's mastodon_notifications_view.dart instead uses full posts for mentions, compact indented author/body previews for grouped favourite/reblog interactions, default icon/timestamp rows for follow/poll/update, and filled Approve plus outlined Reject for requests. Groups show at most eight avatars and localized remaining-actor wording. Existing translated keys cover these semantics.

QuotedPost resolves statuses through StatusProjection, then uses StatusCard(nested = true). The flag removes gutters, actions and recursive quotes but retains the same background and author density. A distinct nested surface, border, inset and smaller identity row can establish hierarchy without changing resolution.

ComposerDraft owns payload state and operation IDs. Publishing and editing call API POST/PUT, ingest LocalWrite Created/Edited, then return to the prior route; ordinary creation does not open the new post. PostActionSheet exposes Edit/Delete only for the signed-in author. StatusCard confirms deletion and ingests a tombstone only after API success. Root must locate the exact test marker and verify absence after a fresh read or process restart.

The settings guard in Composer is checked before the suspended accountSettings request; a late response can overwrite a user's privacy choice. Fix that causal boundary and include settings-only changes in dirty tracking before real write validation. Avoid introducing another draft/status owner.

MediaGallery saves the original URL. IosPlatformServices caps the download at 30 MiB and awaits Photos creation completion. It currently converts bytes to UIImage, supplies no Photos asset identifier, ignores the requested name, and puts success/error feedback behind the full-screen gallery. A saved asset needs before/after Photos inspection. Improving original-resource saving and visible gallery feedback is a confirmed gap within this request.

References are shared/ui/Foundation.kt, Accounts.kt, Feeds.kt, FeedCache.kt:241, StatusCard.kt, PostContent.kt, Composer.kt, MediaGallery.kt; shared/data/MastodonApi.kt:370; iosMain/platform/IosPlatformServices.kt:169; original lib/ui/mastodon/widgets/mastodon_notifications_view.dart:537-895 and mastodon_profile_detail_screen.dart:679-797. Existing 90 shared and seven read-only native UI scenarios form the regression baseline.
