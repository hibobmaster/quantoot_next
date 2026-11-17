package com.quanquan.quantoot.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.*
import com.quanquan.quantoot.model.*

enum class StatusPresentation { Full, Quoted }

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
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            safeWebUrl(account.avatarStatic.ifBlank { account.avatar }),
            account.name,
            Modifier.size(44.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            EmojiText(
                account.name,
                account.emojis,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
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
) {
    val projected = ui.statuses.project(entry) ?: return
    val status = projected.original
    val quoted = presentation == StatusPresentation.Quoted
    var revealed by
        remember(status.id, status.spoilerText) { mutableStateOf(status.spoilerText.isBlank()) }
    var menu by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var reportComment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val repliesDescription = tr("mastodon_view_replies", "count" to status.repliesCount.toString())
    Card(
        Modifier.fillMaxWidth().padding(horizontal = if (quoted) 0.dp else 8.dp)
            .testTag("status-card-${status.id}")
            .clickable { ui.navigate(Screen.Thread(status.id)) },
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (quoted) MaterialTheme.colorScheme.surfaceContainerLow
                    else if (highlighted) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLowest
            ),
        border = if (quoted) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
    ) {
        Column(Modifier.padding(if (quoted) 12.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (status.pinned)
                Text(tr("mastodon_featured"), style = MaterialTheme.typography.labelMedium)
            if (projected.reblog != null)
                Text(
                    tr("mastodon_boosted_by", "name" to projected.account.name),
                    style = MaterialTheme.typography.labelMedium,
                )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (quoted) QuotedIdentity(status.account) { ui.navigate(Screen.Profile(status.account.id)) }
                    else AccountRow(status.account) { ui.navigate(Screen.Profile(status.account.id)) }
                }
                Box {
                    IconButton(
                        modifier = Modifier.semantics { contentDescription = "More post actions" },
                        onClick = { menu = true },
                    ) {
                        Icon(Icons.Default.MoreVert, null)
                    }

                }
            }
            status.inReplyToId?.let { parentId ->
                val author = status.mentions.firstOrNull { it.id == status.inReplyToAccountId }?.acct
                    ?: status.account.acct.takeIf { status.account.id == status.inReplyToAccountId }
                TextButton(onClick = { ui.navigate(Screen.Thread(parentId)) }, contentPadding = PaddingValues(0.dp)) {
                    Icon(PostIcons.Replies, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (author == null) tr("mastodon_view_parent_post")
                        else tr("mastodon_in_reply_to", "name" to "@$author"),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            if (status.spoilerText.isNotBlank()) {
                Text(status.spoilerText, style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { revealed = !revealed }) {
                    Text(tr(if (revealed) "mastodon_hide" else "mastodon_show"))
                }
            }
            if (revealed) {
                HtmlContent(ui, status.content, emojis = status.emojis, mentions = status.mentions, tags = status.tags)
                MediaAttachments(ui, status.mediaAttachments, status.sensitive)
                status.poll?.let { PollView(ui, it) }
                status.card?.let { LinkPreview(ui, it) }
                if (!quoted) QuotedPost(ui, status)
            }
            StatusMetadata(ui, status)
            if (!quoted) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        modifier =
                            Modifier.semantics {
                                contentDescription = repliesDescription
                            },
                        onClick = { ui.navigate(Screen.Thread(status.id)) },
                    ) {
                        Icon(PostIcons.Replies, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(status.repliesCount.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    if (status.reblogged)
                                        "Undo boost, ${status.reblogsCount} boosts"
                                    else "Boost, ${status.reblogsCount} boosts"
                            },
                        onClick = {
                            ui.requireLogin {
                                busy = true
                                ui.run {
                                    try {
                                        ui.statuses.action(status.id, StatusAction.Reblog) {
                                            ui.api.setStatusAction(
                                                status.id,
                                                StatusAction.Reblog,
                                                !status.reblogged,
                                            )
                                        }
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        enabled =
                            !busy &&
                                status.visibility != "direct" &&
                                status.visibility != "private",
                    ) {
                        Icon(PostIcons.Boost, null, Modifier.size(19.dp),
                            tint = if (status.reblogged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(status.reblogsCount.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    if (status.favourited)
                                        "Remove favorite, ${status.favouritesCount} favorites"
                                    else "Favorite, ${status.favouritesCount} favorites"
                            },
                        onClick = {
                            ui.requireLogin {
                                busy = true
                                ui.run {
                                    try {
                                        ui.statuses.action(status.id, StatusAction.Favourite) {
                                            ui.api.setStatusAction(
                                                status.id,
                                                StatusAction.Favourite,
                                                !status.favourited,
                                            )
                                        }
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        },
                        enabled = !busy,
                    ) {
                        Icon(if (status.favourited) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            null, Modifier.size(19.dp),
                            tint = if (status.favourited) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(status.favouritesCount.toString(), style = MaterialTheme.typography.labelMedium)
                    }
                    TextButton(
                        modifier =
                            Modifier.semantics {
                                contentDescription =
                                    if (status.bookmarked) "Remove bookmark" else "Bookmark"
                            },
                        onClick = {
                            ui.requireLogin {
                                ui.run {
                                    ui.statuses.action(status.id, StatusAction.Bookmark) {
                                        ui.api.setStatusAction(
                                            status.id,
                                            StatusAction.Bookmark,
                                            !status.bookmarked,
                                        )
                                    }
                                }
                            }
                        },
                    ) {
                        Icon(PostIcons.Bookmark, null, Modifier.size(19.dp),
                            tint = if (status.bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        modifier = Modifier.semantics { contentDescription = "Quote post" },
                        onClick = {
                            ui.requireLogin { ui.navigate(Screen.Compose(quote = status)) }
                        },
                        enabled = status.visibility in listOf("public", "unlisted"),
                    ) {
                        Icon(PostIcons.Quote, null, Modifier.size(19.dp))
                    }
                }
            }
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
                            ui.message = "Report sent"
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
private fun QuotedIdentity(account: Account, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            safeWebUrl(account.avatarStatic.ifBlank { account.avatar }), null,
            Modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            EmojiText(account.name, account.emojis, style = MaterialTheme.typography.labelLarge)
            Text("@${account.acct}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StatusMetadata(ui: UiContext, status: Status) {
    val visibility = PostVisibility.from(status.visibility)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            modifier = Modifier.semantics { contentDescription = status.createdAt },
            onClick = { ui.navigate(Screen.Thread(status.id)) },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(RelativeTimestamp(status.createdAt) +
                if (status.editedAt != null) " · ${tr("mastodon_edited")}" else "",
                style = MaterialTheme.typography.bodySmall)
        }
        Row(
            Modifier.semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(when (visibility) {
                PostVisibility.Public -> PostIcons.Public
                PostVisibility.Unlisted -> PostIcons.Unlisted
                PostVisibility.Followers -> Icons.Default.Lock
                PostVisibility.Direct -> Icons.Default.Email
                PostVisibility.Unknown -> Icons.Default.Info
            }, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr(visibility.labelKey), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
