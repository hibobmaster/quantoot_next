package com.quanquan.quantoot.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class Page<T>(val items: List<T>, val nextCursor: String? = null)

@Serializable
data class CustomEmoji(
    val shortcode: String = "",
    val url: String = "",
    @SerialName("static_url") val staticUrl: String = "",
    @SerialName("visible_in_picker") val visibleInPicker: Boolean = true,
    val category: String? = null,
)

@Serializable
data class ProfileField(
    val name: String = "",
    val value: String = "",
    @SerialName("verified_at") val verifiedAt: String? = null,
)

@Serializable
data class AccountSource(
    val privacy: String = "public",
    val sensitive: Boolean = false,
    val language: String = "",
    val note: String = "",
    val fields: List<ProfileField> = emptyList(),
    @SerialName("follow_requests_count") val followRequestsCount: Int = 0,
)

@Serializable
data class Account(
    val id: String = "",
    val username: String = "",
    val acct: String = "",
    @SerialName("display_name") val displayName: String = "",
    val note: String = "",
    val url: String = "",
    val avatar: String = "",
    @SerialName("avatar_static") val avatarStatic: String = "",
    val header: String = "",
    val locked: Boolean = false,
    val bot: Boolean = false,
    val discoverable: Boolean? = null,
    val indexable: Boolean? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("followers_count") val followersCount: Int = 0,
    @SerialName("following_count") val followingCount: Int = 0,
    @SerialName("statuses_count") val statusesCount: Int = 0,
    val fields: List<ProfileField> = emptyList(),
    val emojis: List<CustomEmoji> = emptyList(),
    val source: AccountSource? = null,
) {
    val name: String
        get() = displayName.ifBlank { username.ifBlank { acct } }
}

@Serializable
data class Mention(
    val id: String = "",
    val username: String = "",
    val acct: String = "",
    val url: String = "",
)

@Serializable
data class MediaAttachment(
    val id: String = "",
    val type: String = "image",
    val url: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("remote_url") val remoteUrl: String? = null,
    val description: String? = null,
    val blurhash: String? = null,
    val meta: MediaMetadata? = null,
)

@Serializable
data class MediaMetadata(
    val original: MediaDimensions? = null,
    val small: MediaDimensions? = null,
)

@Serializable
data class MediaDimensions(val width: Int? = null, val height: Int? = null)

@Serializable
data class PollOption(
    val title: String = "",
    @SerialName("votes_count") val votesCount: Int? = null,
)

@Serializable
data class Poll(
    val id: String = "",
    @SerialName("expires_at") val expiresAt: String? = null,
    val expired: Boolean = false,
    val multiple: Boolean = false,
    @SerialName("votes_count") val votesCount: Int = 0,
    @SerialName("voters_count") val votersCount: Int? = null,
    val voted: Boolean = false,
    @SerialName("own_votes") val ownVotes: List<Int> = emptyList(),
    val options: List<PollOption> = emptyList(),
)

@Serializable
data class PreviewCard(
    val url: String = "",
    val title: String = "",
    val description: String = "",
    val image: String? = null,
    val type: String? = null,
    @SerialName("provider_name") val providerName: String? = null,
    @SerialName("provider_url") val providerUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val blurhash: String? = null,
    @SerialName("image_description") val imageDescription: String? = null,
)

@Serializable
data class Quote(
    val state: String = "",
    @SerialName("quoted_status") val quotedStatus: Status? = null,
    @SerialName("quoted_status_id") val quotedStatusId: String? = null,
)

@Serializable
data class Status(
    val id: String = "",
    val account: Account = Account(),
    val content: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("edited_at") val editedAt: String? = null,
    val url: String? = null,
    val visibility: String = "public",
    @SerialName("spoiler_text") val spoilerText: String = "",
    val sensitive: Boolean = false,
    val language: String? = null,
    @SerialName("in_reply_to_id") val inReplyToId: String? = null,
    @SerialName("in_reply_to_account_id") val inReplyToAccountId: String? = null,
    @SerialName("replies_count") val repliesCount: Int = 0,
    @SerialName("reblogs_count") val reblogsCount: Int = 0,
    @SerialName("favourites_count") val favouritesCount: Int = 0,
    val favourited: Boolean = false,
    val reblogged: Boolean = false,
    val bookmarked: Boolean = false,
    val pinned: Boolean = false,
    @SerialName("media_attachments") val mediaAttachments: List<MediaAttachment> = emptyList(),
    val mentions: List<Mention> = emptyList(),
    val emojis: List<CustomEmoji> = emptyList(),
    val poll: Poll? = null,
    val card: PreviewCard? = null,
    val reblog: Status? = null,
    val quote: Quote? = null,
    val tags: List<Tag> = emptyList(),
    val filtered: List<StatusFilterMatch> = emptyList(),
) {
    val original: Status
        get() = reblog ?: this
}

@Serializable
data class StatusFilterMatch(val filter: ContentFilter)

@Serializable
data class StatusContext(
    val ancestors: List<Status> = emptyList(),
    val descendants: List<Status> = emptyList(),
)

@Serializable
data class Notification(
    val id: String = "",
    val type: String = "",
    @SerialName("created_at") val createdAt: String = "",
    val account: Account = Account(),
    val status: Status? = null,
)

@Serializable
data class Relationship(
    val id: String = "",
    val following: Boolean = false,
    @SerialName("followed_by") val followedBy: Boolean = false,
    val requested: Boolean = false,
    val blocking: Boolean = false,
    val muting: Boolean = false,
)

@Serializable data class Tag(val name: String = "", val url: String = "")

@Serializable
data class SearchResults(
    val accounts: List<Account> = emptyList(),
    val statuses: List<Status> = emptyList(),
    val hashtags: List<Tag> = emptyList(),
)

sealed interface Timeline {
    data object Home : Timeline

    data class Public(val local: Boolean = true) : Timeline

    data class Hashtag(val tag: String) : Timeline

    data class AccountPosts(
        val accountId: String,
        val onlyMedia: Boolean = false,
        val pinned: Boolean = false,
        val excludeReplies: Boolean = false,
        val excludeReblogs: Boolean = false,
    ) : Timeline

    data object Favourites : Timeline

    data object Bookmarks : Timeline
}

data class PollDraft(
    val options: List<String>,
    val expiresIn: Int = 86400,
    val multiple: Boolean = false,
    val hideTotals: Boolean = false,
)

data class StatusDraft(
    val text: String,
    val visibility: String = "public",
    val language: String? = null,
    val spoilerText: String = "",
    val sensitive: Boolean = false,
    val inReplyToId: String? = null,
    val mediaIds: List<String> = emptyList(),
    val poll: PollDraft? = null,
    val quotePolicy: String? = null,
    val quotedStatusId: String? = null,
)

data class ProfileUpdate(
    val displayName: String,
    val note: String,
    val locked: Boolean,
    val fields: List<ProfileField> = emptyList(),
    val avatar: com.quanquan.quantoot.platform.PickedMedia? = null,
    val header: com.quanquan.quantoot.platform.PickedMedia? = null,
)

enum class StatusAction(val endpoint: String) {
    Favourite("favourite"),
    Reblog("reblog"),
    Bookmark("bookmark"),
}

sealed interface StreamSource {
    data object User : StreamSource
    data object Local : StreamSource
    data object Federated : StreamSource
    data class Hashtag(val name: String) : StreamSource
    data object LocalWrite : StreamSource
}

sealed interface StreamEvent {
    val source: StreamSource

    data class Created(override val source: StreamSource, val status: Status) : StreamEvent
    data class Edited(override val source: StreamSource, val status: Status) : StreamEvent
    data class Deleted(override val source: StreamSource, val id: String) : StreamEvent
    data class Notify(override val source: StreamSource, val notification: Notification) : StreamEvent
    data class Reconnected(override val source: StreamSource) : StreamEvent
}

data class ServerInfo(
    val title: String = "",
    val description: String = "",
    val version: String = "",
    val domain: String = "",
    val maxCharacters: Int = 500,
    val streamingUrl: String? = null,
    val supportsPkce: Boolean = false,
    val thumbnail: String? = null,
    val activeMonthlyUsers: Int? = null,
    val contactEmail: String = "",
    val contactAccount: Account? = null,
    val rules: List<ServerRule> = emptyList(),
)

@Serializable
data class ServerRule(val id: String = "", val text: String = "", val hint: String = "")

@Serializable
data class FilterKeyword(
    val id: String? = null,
    val keyword: String = "",
    @SerialName("whole_word") val wholeWord: Boolean = false,
)

@Serializable
data class ContentFilter(
    val id: String = "",
    val title: String = "",
    val context: List<String> = listOf("home", "notifications", "public", "thread", "account"),
    @SerialName("filter_action") val action: String = "warn",
    val keywords: List<FilterKeyword> = emptyList(),
    @SerialName("expires_at") val expiresAt: String? = null,
)

data class AccountSettings(
    val discoverable: Boolean = false,
    val indexable: Boolean = false,
    val locked: Boolean = false,
    val privacy: String = "public",
    val language: String = "",
    val sensitive: Boolean = false,
    val quotePolicy: String = "public",
)

@Serializable
data class NotificationPolicy(
    @SerialName("for_not_following") val forNotFollowing: String = "accept",
    @SerialName("for_not_followers") val forNotFollowers: String = "accept",
    @SerialName("for_new_accounts") val forNewAccounts: String = "accept",
    @SerialName("for_private_mentions") val forPrivateMentions: String = "accept",
    @SerialName("for_limited_accounts") val forLimitedAccounts: String = "accept",
)
