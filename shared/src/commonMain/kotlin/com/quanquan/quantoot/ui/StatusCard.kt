package com.quanquan.quantoot.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quanquan.quantoot.data.*
import com.quanquan.quantoot.model.*

enum class StatusPresentation { Full, Focal, Quoted }

internal enum class PostVisibility(val labelKey: String) {
    Public("mastodon_public"),
    Unlisted("mastodon_unlisted"),
    Followers("mastodon_followers_only"),
    Direct("mastodon_direct"),
    Unknown("mastodon_visibility_unavailable");

    companion object {
        fun from(value: String) = when (value) {
            "public" -> Public
            "unlisted" -> Unlisted
            "private" -> Followers
            "direct" -> Direct
            else -> Unknown
        }
    }
}

@Composable
fun AccountRow(account: Account, onClick: () -> Unit) {
    PostAuthor(account, 44.dp, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp))
}

@Composable
private fun PostAuthor(account: Account, avatarSize: Dp, modifier: Modifier = Modifier) {
    Row(
        modifier.heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccountAvatar(account, avatarSize)
        Column(Modifier.weight(1f)) {
            EmojiText(account.name, account.emojis, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                "@${account.acct}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun StatusCard(
    ui: UiContext,
    entry: Status,
    highlighted: Boolean = false,
    presentation: StatusPresentation = StatusPresentation.Full,
    onViewReplies: (() -> Unit)? = null,
) {
    val projected = ui.statuses.project(entry) ?: return
    val status = projected.original
    val quoted = presentation == StatusPresentation.Quoted
    val focal = presentation == StatusPresentation.Focal
    val largeType = LocalDensity.current.fontScale > 1.3f
    val rail = if (presentation == StatusPresentation.Full && !largeType) 50.dp else 0.dp
    val isPublic = PostVisibility.from(status.visibility) == PostVisibility.Public
    val compactTime = presentation == StatusPresentation.Full && !largeType && isPublic && status.editedAt == null
    val footerMetadata = focal && isPublic
    val highlightAlpha by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = tween(450),
    )
    var revealed by remember(status.id, status.spoilerText) { mutableStateOf(status.spoilerText.isBlank()) }
    var menu by remember(status.id) { mutableStateOf(false) }
    var deleteConfirm by remember(status.id) { mutableStateOf(false) }
    var report by remember(status.id) { mutableStateOf(false) }
    var reportComment by remember(status.id) { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    val openThread = { ui.navigate(Screen.Thread(status.id)) }
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("status-card-${status.id}"),
        onClick = openThread,
        shape = if (quoted) MaterialTheme.shapes.medium else RectangleShape,
        color = if (quoted) colors.surfaceContainerLow else colors.surface,
        border = when {
            quoted -> BorderStroke(1.dp, colors.outlineVariant)
            highlightAlpha > 0f -> BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.24f * highlightAlpha))
            else -> null
        },
    ) {
        Column {
            Column(
                Modifier.fillMaxWidth()
                    .padding(horizontal = if (quoted) 12.dp else 16.dp)
                    .padding(top = if (quoted) 6.dp else 12.dp, bottom = if (quoted) 12.dp else 2.dp),
            ) {
                if (status.pinned) PostContext(Icons.Default.Star, tr("mastodon_featured"), rail)
                if (projected.reblog != null)
                    PostContext(PostIcons.Boost, tr("mastodon_boosted_by", "name" to projected.account.name), rail)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PostAuthor(
                        status.account,
                        if (quoted) 28.dp else if (focal) 46.dp else 40.dp,
                        Modifier.weight(1f).clickable(role = Role.Button) {
                            ui.navigate(Screen.Profile(status.account.id))
                        },
                    )
                    if (compactTime) {
                        Row(
                            Modifier.widthIn(max = 116.dp).clickable(role = Role.Button, onClick = openThread).heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(PostIcons.Public, tr("mastodon_public"), Modifier.size(13.dp), tint = colors.onSurfaceVariant)
                            PostTime(status)
                        }
                    }
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, tr("mastodon_more_post_actions"), Modifier.size(21.dp).rotate(90f),
                            tint = colors.onSurfaceVariant)
                    }
                }
                Column(
                    Modifier.fillMaxWidth().padding(start = rail),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!compactTime && !footerMetadata) PostMetadata(status)
                    status.inReplyToId?.let { parentId ->
                        val author = status.mentions.firstOrNull { it.id == status.inReplyToAccountId }?.acct
                            ?: status.account.acct.takeIf { status.account.id == status.inReplyToAccountId }
                            ?: ui.statuses.known(parentId)?.original?.account?.acct
                        Row(
                            Modifier.clickable(role = Role.Button) { ui.navigate(Screen.Thread(parentId)) }
                                .heightIn(min = 44.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(PostIcons.Parent, null, Modifier.size(15.dp), tint = colors.onSurfaceVariant)
                            Text(
                                if (author == null) tr("mastodon_view_parent_post")
                                else tr("mastodon_in_reply_to", "name" to "@$author"),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                    if (status.spoilerText.isNotBlank()) {
                        Surface(
                            onClick = { revealed = !revealed }, color = colors.surfaceContainerLow,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr("mastodon_content_warning"), style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant)
                                    Text(status.spoilerText, style = MaterialTheme.typography.titleSmall)
                                }
                                Text(tr(if (revealed) "mastodon_hide" else "mastodon_show"),
                                    style = MaterialTheme.typography.labelLarge, color = colors.primary)
                            }
                        }
                    }
                    if (revealed) {
                        HtmlContent(
                            ui, status.content, emojis = status.emojis, mentions = status.mentions, tags = status.tags,
                            style = when (presentation) {
                                StatusPresentation.Focal -> MaterialTheme.typography.bodyLarge.copy(
                                    fontSize = 19.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal,
                                )
                                StatusPresentation.Quoted -> MaterialTheme.typography.bodyMedium
                                StatusPresentation.Full -> MaterialTheme.typography.bodyLarge
                            },
                        )
                        MediaAttachments(ui, status.mediaAttachments, status.sensitive)
                        status.poll?.let { PollView(ui, it) }
                        status.card?.let { LinkPreview(ui, it) }
                        if (!quoted) QuotedPost(ui, status)
                    }
                    if (footerMetadata) PostMetadata(status)
                }
                if (!quoted) {
                    PostActions(ui, status, Modifier.fillMaxWidth().padding(start = rail), onViewReplies ?: openThread)
                }
            }
            if (!quoted) HorizontalDivider(thickness = Dp.Hairline, color = colors.outlineVariant)
        }
    }
    if (menu) PostActionSheet(ui, status, onDismiss = { menu = false },
        onDelete = { deleteConfirm = true }, onReport = { report = true })
    if (deleteConfirm)
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text(tr("mastodon_delete_confirm_title")) },
            text = { Text(tr("mastodon_delete_confirm_message")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirm = false
                        ui.run {
                            ui.api.deleteStatus(status.id)
                            ui.ingest(StreamEvent.Deleted(StreamSource.LocalWrite, status.id))
                        }
                    }
                ) {
                    Text(tr("mastodon_delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirm = false }) { Text(tr("mastodon_cancel")) }
            },
        )
    val reportSent = tr("mastodon_report_sent")
    if (report)
        AlertDialog(
            onDismissRequest = { report = false },
            title = { Text(tr("mastodon_report")) },
            text = {
                OutlinedTextField(
                    reportComment,
                    { reportComment = it },
                    label = { Text(tr("mastodon_report_comment")) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ui.run {
                            ui.api.report(status.account.id, reportComment, listOf(status.id))
                            report = false
                            ui.message = reportSent
                        }
                    }
                ) {
                    Text(tr("mastodon_report"))
                }
            },
            dismissButton = {
                TextButton(onClick = { report = false }) { Text(tr("mastodon_cancel")) }
            },
        )
}

@Composable
private fun PostContext(icon: ImageVector, label: String, inset: Dp) {
    Row(
        Modifier.padding(start = inset, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PostTime(status: Status) {
    Text(
        RelativeTimestamp(status.createdAt),
        Modifier.semantics { contentDescription = status.createdAt },
        maxLines = 1, overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PostMetadata(status: Status) {
    val visibility = PostVisibility.from(status.visibility)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        PostTime(status)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                when (visibility) {
                    PostVisibility.Public -> PostIcons.Public
                    PostVisibility.Unlisted -> PostIcons.Unlisted
                    PostVisibility.Followers -> Icons.Default.Lock
                    PostVisibility.Direct -> Icons.Default.Email
                    PostVisibility.Unknown -> Icons.Default.Info
                },
                null, Modifier.size(14.dp), tint = muted,
            )
            Text(tr(visibility.labelKey), style = MaterialTheme.typography.bodySmall, color = muted)
        }
        if (status.editedAt != null) Text(tr("mastodon_edited"), style = MaterialTheme.typography.bodySmall, color = muted)
    }
}

@Composable
private fun PostActions(ui: UiContext, status: Status, modifier: Modifier, onViewReplies: () -> Unit) {
    val pending = remember(status.id) { mutableStateListOf<StatusAction>() }
    fun toggle(action: StatusAction, desired: Boolean) {
        ui.requireLogin {
            ui.run {
                if (action in pending) return@run
                pending += action
                try {
                    ui.statuses.action(status.id, action) { ui.api.setStatusAction(status.id, action, desired) }
                } finally {
                    pending.remove(action)
                }
            }
        }
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PostAction(
            PostIcons.Replies, tr("mastodon_view_replies", "count" to status.repliesCount.toString()),
            Modifier.weight(1f), count = status.repliesCount, onClick = onViewReplies,
        )
        PostAction(
            PostIcons.Boost,
            tr(if (status.reblogged) "mastodon_undo_boost_count" else "mastodon_boost_count",
                "count" to status.reblogsCount.toString()),
            Modifier.weight(1f), count = status.reblogsCount, active = status.reblogged,
            busy = StatusAction.Reblog in pending, enabled = status.visibility != "direct" && status.visibility != "private",
        ) { toggle(StatusAction.Reblog, !status.reblogged) }
        PostAction(
            if (status.favourited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            tr(if (status.favourited) "mastodon_undo_favorite_count" else "mastodon_favorite_count",
                "count" to status.favouritesCount.toString()),
            Modifier.weight(1f), count = status.favouritesCount, active = status.favourited,
            busy = StatusAction.Favourite in pending,
        ) { toggle(StatusAction.Favourite, !status.favourited) }
        PostAction(
            if (status.bookmarked) PostIcons.Bookmarked else PostIcons.Bookmark,
            tr(if (status.bookmarked) "mastodon_remove_bookmark" else "mastodon_save_bookmark"),
            Modifier.weight(1f), active = status.bookmarked, busy = StatusAction.Bookmark in pending,
        ) { toggle(StatusAction.Bookmark, !status.bookmarked) }
    }
}

@Composable
private fun PostAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier,
    count: Int = 0,
    active: Boolean = false,
    busy: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tint = when {
        !enabled -> colors.onSurface.copy(alpha = 0.38f)
        active -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Row(
        modifier.heightIn(min = 48.dp).clickable(enabled = enabled && !busy, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = tint, strokeWidth = 2.dp)
        else Icon(icon, null, Modifier.size(20.dp), tint = tint)
        if (count > 0) Text(
            LocalStrings.current.compactCount(count), color = tint, style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PollView(ui: UiContext, initial: Poll) {
    var poll by remember(initial) { mutableStateOf(initial) }
    var selected by remember(poll.id) { mutableStateOf<Set<Int>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    Column {
        poll.options.forEachIndexed { i, option ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (!poll.voted && !poll.expired)
                    Checkbox(
                        i in selected,
                        { checked ->
                            selected =
                                if (poll.multiple) {
                                    if (checked) selected + i else selected - i
                                } else setOf(i)
                        },
                    )
                Text(option.title, Modifier.weight(1f))
                if (poll.voted || poll.expired) Text("${option.votesCount ?: 0}")
            }
            if (poll.voted || poll.expired)
                LinearProgressIndicator(
                    progress = {
                        (option.votesCount ?: 0).toFloat() / poll.votesCount.coerceAtLeast(1)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
        }
        Text(
            "${poll.votersCount ?: poll.votesCount} · ${poll.expiresAt.orEmpty()}",
            style = MaterialTheme.typography.bodySmall,
        )
        if (!poll.voted && !poll.expired)
            TextButton(
                onClick = {
                    ui.requireLogin {
                        busy = true
                        ui.run {
                            try {
                                poll = ui.api.vote(poll.id, selected.toList())
                            } finally {
                                busy = false
                            }
                        }
                    }
                },
                enabled = selected.isNotEmpty() && !busy,
            ) {
                Text(tr("mastodon_publish"))
            }
    }
}
