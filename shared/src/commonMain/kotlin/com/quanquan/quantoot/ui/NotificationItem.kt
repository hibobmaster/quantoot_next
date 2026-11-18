package com.quanquan.quantoot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quanquan.quantoot.data.plainText
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
    val largeType = LocalDensity.current.fontScale > 1.3f
    val inset = if (largeType) 16.dp else 68.dp
    val colors = MaterialTheme.colorScheme
    val status = notification.status
    fun openEvent() {
        if (kind != NotificationKind.FollowRequest && status != null) ui.navigate(Screen.Thread(status.id))
        else ui.navigate(Screen.Profile(notification.account.id))
    }
    if (kind == NotificationKind.Mention && status != null) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { ui.navigate(Screen.Profile(notification.account.id)) }
                    .heightIn(min = 44.dp).padding(start = inset, end = 16.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PostIcons.Replies, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    EmojiText("${notification.account.name} $action", notification.account.emojis,
                        style = MaterialTheme.typography.labelMedium.copy(color = colors.onSurfaceVariant), maxLines = 2)
                }
                NotificationTime(notification)
            }
            StatusCard(ui, status)
        }
        return
    }
    val symbol = when (kind) {
        NotificationKind.Mention -> PostIcons.Replies
        NotificationKind.Favourite -> Icons.Default.Favorite
        NotificationKind.Reblog -> PostIcons.Boost
        NotificationKind.Follow, NotificationKind.FollowRequest -> Icons.Default.Person
        NotificationKind.Poll -> PostIcons.Poll
        NotificationKind.Update -> Icons.Default.Edit
        NotificationKind.Unknown -> Icons.Default.Notifications
    }
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = ::openEvent)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.size(46.dp).clickable(role = Role.Button) { ui.navigate(Screen.Profile(notification.account.id)) }
                    .semantics { contentDescription = notification.account.name },
            ) {
                AccountAvatar(notification.account, 40.dp)
                Box(
                    Modifier.align(Alignment.BottomEnd).size(20.dp).background(colors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(symbol, null, Modifier.size(15.dp),
                        tint = if (kind == NotificationKind.Favourite) colors.primary else colors.onSurface)
                }
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.weight(1f).clickable(role = Role.Button) { ui.navigate(Screen.Profile(notification.account.id)) }
                            .padding(end = 8.dp),
                    ) {
                        EmojiText(notification.account.name, notification.account.emojis,
                            style = MaterialTheme.typography.titleSmall, maxLines = 2)
                    }
                    if (!largeType) NotificationTime(notification)
                }
                Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                if (largeType) NotificationTime(notification)
                if (interaction && actors.othersCount > 0) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        actors.visible.drop(1).forEach { account ->
                            Box(
                                Modifier.size(44.dp).clickable(role = Role.Button) { ui.navigate(Screen.Profile(account.id)) }
                                    .semantics { contentDescription = account.name },
                                contentAlignment = Alignment.Center,
                            ) { AccountAvatar(account, 30.dp) }
                        }
                        if (actors.overflowCount > 0) Text("+${actors.overflowCount}",
                            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                }
            }
        }
        if ((interaction || kind == NotificationKind.Update || kind == NotificationKind.Poll) && status != null) {
            InteractionStatusPreview(ui, status, Modifier.padding(start = inset, end = 16.dp, bottom = 14.dp))
        }
        if (kind == NotificationKind.FollowRequest) {
            FlowRow(
                Modifier.fillMaxWidth().padding(start = inset, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                maxItemsInEachRow = if (largeType) 1 else 2,
            ) {
                Button(onClick = { onFollowRequest(true) }, enabled = !handling,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("approve-follow-request-${notification.id}")) {
                    if (handling) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(tr("mastodon_follow_request_approve"))
                }
                OutlinedButton(onClick = { onFollowRequest(false) }, enabled = !handling,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("reject-follow-request-${notification.id}")) {
                    Text(tr("mastodon_follow_request_reject"))
                }
            }
        }
        HorizontalDivider(thickness = Dp.Hairline, color = colors.outlineVariant)
    }
}

@Composable
private fun NotificationTime(notification: Notification) {
    Text(RelativeTimestamp(notification.createdAt),
        Modifier.widthIn(max = 128.dp).semantics { contentDescription = notification.createdAt },
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun InteractionStatusPreview(ui: UiContext, entry: Status, modifier: Modifier) {
    val status = ui.statuses.project(entry)?.original ?: return
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().clickable(role = Role.Button) { ui.navigate(Screen.Thread(status.id)) }
            .drawBehind {
                val stroke = 2.dp.toPx()
                val x = if (layoutDirection == LayoutDirection.Ltr) stroke / 2 else size.width - stroke / 2
                drawLine(colors.outlineVariant, Offset(x, 0f), Offset(x, size.height), stroke)
            }
            .heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
    ) {
        EmojiText(status.account.name, status.account.emojis, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        val preview = interactionPreviewText(status)
        if (preview.isNotBlank()) EmojiText(preview, status.emojis,
            style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurfaceVariant), maxLines = 3)
        if (status.spoilerText.isNotBlank()) Text(tr("mastodon_content_warning"),
            style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}
