package com.quanquan.quantoot.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.plainText
import com.quanquan.quantoot.data.safeWebUrl
import com.quanquan.quantoot.model.Account
import com.quanquan.quantoot.model.Notification
import com.quanquan.quantoot.model.Status

internal enum class NotificationKind(val labelKey: String?) {
    Mention("mastodon_replied"),
    Favourite("mastodon_favorited"),
    Reblog("mastodon_boosted"),
    Follow("mastodon_followed_you"),
    FollowRequest("mastodon_follow_request"),
    Poll("mastodon_poll_ended"),
    Update("mastodon_edited_post"),
    Unknown(null);

    companion object {
        fun from(type: String) = when (type) {
            "mention" -> Mention
            "favourite" -> Favourite
            "reblog" -> Reblog
            "follow" -> Follow
            "follow_request" -> FollowRequest
            "poll" -> Poll
            "update" -> Update
            else -> Unknown
        }
    }
}

internal class NotificationActors(accounts: List<Account>) {
    val visible = accounts.take(8)
    val othersCount = accounts.size - 1
    val overflowCount = accounts.size - visible.size
}

internal fun interactionPreviewText(status: Status): String =
    status.spoilerText.takeIf { it.isNotBlank() } ?: plainText(status.content)

@Composable
internal fun NotificationItem(
    ui: UiContext,
    group: List<Notification>,
    handling: Boolean,
    onFollowRequest: (Boolean) -> Unit,
) {
    val notification = group.first()
    val kind = NotificationKind.from(notification.type)
    val actors = NotificationActors(group.map { it.account })
    val action = kind.labelKey?.let { tr(it) } ?: notification.type.ifBlank { tr("mastodon_notifications") }
    val interaction = kind == NotificationKind.Favourite || kind == NotificationKind.Reblog
    val label = if (interaction && actors.othersCount > 0)
        tr("mastodon_notification_others", "count" to actors.othersCount.toString(), "action" to action)
    else action
    val inset = if (LocalDensity.current.fontScale > 1.3f) 16.dp else 48.dp
    fun openEvent() {
        val status = notification.status
        if (kind != NotificationKind.FollowRequest && status != null) ui.navigate(Screen.Thread(status.id))
        else ui.navigate(Screen.Profile(notification.account.id))
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth()) {
            if (interaction && actors.othersCount > 0) {
                FlowRow(
                    Modifier.padding(start = inset, end = 16.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    actors.visible.forEach { account -> NotificationAvatar(ui, account) }
                    if (actors.overflowCount > 0) Text("+${actors.overflowCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable(onClick = ::openEvent)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val icon = when (kind) {
                    NotificationKind.Mention -> PostIcons.Replies
                    NotificationKind.Favourite -> Icons.Default.Star
                    NotificationKind.Reblog -> PostIcons.Boost
                    NotificationKind.Follow, NotificationKind.FollowRequest -> Icons.Default.Person
                    NotificationKind.Poll -> PostIcons.Poll
                    NotificationKind.Update -> Icons.Default.Edit
                    NotificationKind.Unknown -> Icons.Default.Notifications
                }
                val tint = when (kind) {
                    NotificationKind.Favourite -> MaterialTheme.colorScheme.tertiary
                    NotificationKind.Reblog, NotificationKind.Follow,
                    NotificationKind.FollowRequest, NotificationKind.Update -> MaterialTheme.colorScheme.primary
                    NotificationKind.Poll -> MaterialTheme.colorScheme.secondary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(icon, null, Modifier.size(20.dp), tint = tint)
                Column(Modifier.weight(1f)) {
                    Box(Modifier.clickable { ui.navigate(Screen.Profile(notification.account.id)) }
                        .heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)) {
                        EmojiText(notification.account.name, notification.account.emojis,
                            style = MaterialTheme.typography.titleSmall)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically) {
                        Text(label, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(RelativeTimestamp(notification.createdAt),
                            Modifier.semantics { contentDescription = notification.createdAt },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!interaction || actors.othersCount == 0) NotificationAvatar(ui, notification.account)
            }
            when (kind) {
                NotificationKind.Mention -> notification.status?.let { StatusCard(ui, it) }
                NotificationKind.Favourite, NotificationKind.Reblog -> notification.status?.let {
                    InteractionStatusPreview(ui, it, Modifier.padding(start = inset, end = 16.dp, bottom = 12.dp))
                }
                NotificationKind.FollowRequest -> FlowRow(
                    Modifier.fillMaxWidth().padding(start = inset, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
                ) {
                    Button(onClick = { onFollowRequest(true) }, enabled = !handling,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .testTag("approve-follow-request-${notification.id}")) {
                        if (handling) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(tr("mastodon_follow_request_approve"))
                    }
                    OutlinedButton(onClick = { onFollowRequest(false) }, enabled = !handling,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .testTag("reject-follow-request-${notification.id}")) {
                        Text(tr("mastodon_follow_request_reject"))
                    }
                }
                else -> Unit
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun NotificationAvatar(ui: UiContext, account: Account) {
    Box(Modifier.size(48.dp).clickable { ui.navigate(Screen.Profile(account.id)) }
        .semantics { contentDescription = account.name }, contentAlignment = Alignment.Center) {
        AsyncImage(safeWebUrl(account.avatarStatic.ifBlank { account.avatar }), null,
            Modifier.size(32.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun InteractionStatusPreview(ui: UiContext, entry: Status, modifier: Modifier) {
    val status = ui.statuses.project(entry)?.original ?: return
    Surface(
        onClick = { ui.navigate(Screen.Thread(status.id)) },
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AsyncImage(safeWebUrl(status.account.avatarStatic.ifBlank { status.account.avatar }), null,
                    Modifier.size(24.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    EmojiText(status.account.name, status.account.emojis, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    Text("@${status.account.acct}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val preview = interactionPreviewText(status)
            if (preview.isNotBlank()) EmojiText(preview, status.emojis,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), maxLines = 4)
            if (status.spoilerText.isNotBlank()) Text(tr("mastodon_content_warning"),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
