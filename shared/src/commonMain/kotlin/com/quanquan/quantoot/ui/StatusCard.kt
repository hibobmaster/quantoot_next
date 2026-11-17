package com.quanquan.quantoot.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.*
import com.quanquan.quantoot.model.*
import com.quanquan.quantoot.platform.NativeVideoPlayer

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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun HtmlContent(
    ui: UiContext,
    html: String,
    modifier: Modifier = Modifier,
    emojis: List<CustomEmoji> = emptyList(),
) {
    val color = MaterialTheme.colorScheme.primary
    val shortcodes = emojis.map { it.shortcode }.toSet()
    val inline = emojiContent(emojis)
    val annotated =
        remember(html, color, emojis) {
            val content = buildAnnotatedString {
                val source =
                    Regex("<(script|style)\\b[^>]*>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE)
                        .replace(html, "")
                fun fragment(value: String) =
                    plainText("\uE000$value\uE001").removePrefix("\uE000").removeSuffix("\uE001")
                val links =
                    Regex(
                        "<a\\b[^>]*href\\s*=\\s*[\"']([^\"']*)[\"'][^>]*>([\\s\\S]*?)</a>",
                        RegexOption.IGNORE_CASE,
                    )
                var cursor = 0
                for (match in links.findAll(source)) {
                    appendEmojiText(
                        fragment(source.substring(cursor, match.range.first)),
                        shortcodes,
                    )
                    val href = safeWebUrl(match.groupValues[1].replace("&amp;", "&"))
                    val label = plainText(match.groupValues[2])
                    if (href == null) appendEmojiText(label, shortcodes)
                    else
                        withLink(
                            LinkAnnotation.Url(
                                href,
                                TextLinkStyles(SpanStyle(color = color)),
                                LinkInteractionListener { ui.open(href) },
                            )
                        ) {
                            appendEmojiText(label, shortcodes)
                        }
                    cursor = match.range.last + 1
                }
                appendEmojiText(fragment(source.substring(cursor)), shortcodes)
            }
            val start = content.text.indexOfFirst { !it.isWhitespace() }
            val end = content.text.indexOfLast { !it.isWhitespace() }
            if (start < 0) AnnotatedString("") else content.subSequence(start, end + 1)
        }
    androidx.compose.foundation.text.selection.SelectionContainer {
        Text(
            annotated,
            modifier,
            inlineContent = inline,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun StatusCard(
    ui: UiContext,
    entry: Status,
    highlighted: Boolean = false,
    nested: Boolean = false,
) {
    val projected = ui.statuses.project(entry) ?: return
    val status = projected.original
    var revealed by
        remember(status.id) { mutableStateOf(status.spoilerText.isBlank() && !status.sensitive) }
    var menu by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var reportComment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = if (nested) 0.dp else 10.dp)
            .clickable { ui.navigate(Screen.Thread(status.id)) },
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (highlighted) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLowest
            ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (status.pinned)
                Text(tr("mastodon_featured"), style = MaterialTheme.typography.labelMedium)
            if (projected.reblog != null)
                Text(
                    tr("mastodon_boosted_by", "name" to projected.account.name),
                    style = MaterialTheme.typography.labelMedium,
                )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    AccountRow(status.account) { ui.navigate(Screen.Profile(status.account.id)) }
                }
                Box {
                    TextButton(
                        modifier = Modifier.semantics { contentDescription = "More post actions" },
                        onClick = { menu = true },
                    ) {
                        Text("⋯")
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("mastodon_open_in_browser")) },
                            onClick = {
                                menu = false
                                ui.open(status.url)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(tr("mastodon_button_share")) },
                            onClick = {
                                menu = false
                                ui.platform.share(
                                    safeWebUrl(status.url.orEmpty()) ?: plainText(status.content)
                                )
                            },
                        )
                        if (ui.me?.id == status.account.id) {
                            DropdownMenuItem(
                                text = { Text(tr("mastodon_edit")) },
                                onClick = {
                                    menu = false
                                    ui.navigate(Screen.Compose(edit = status))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("mastodon_delete")) },
                                onClick = {
                                    menu = false
                                    deleteConfirm = true
                                },
                            )
                        } else if (ui.me != null) {
                            DropdownMenuItem(
                                text = { Text(tr("mastodon_mute")) },
                                onClick = {
                                    menu = false
                                    ui.run {
                                        ui.api.relationshipAction(status.account.id, "mute")
                                        ui.message = "Muted @${status.account.acct}"
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("mastodon_block")) },
                                onClick = {
                                    menu = false
                                    ui.run {
                                        ui.api.relationshipAction(status.account.id, "block")
                                        ui.message = "Blocked @${status.account.acct}"
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(tr("mastodon_report")) },
                                onClick = {
                                    menu = false
                                    report = true
                                },
                            )
                        }
                    }
                }
            }
            if (status.spoilerText.isNotBlank() || status.sensitive) {
                Text(status.spoilerText.ifBlank { tr("mastodon_sensitive_content") })
                TextButton(onClick = { revealed = !revealed }) {
                    Text(tr(if (revealed) "mastodon_hide" else "mastodon_show"))
                }
            }
            if (revealed) {
                HtmlContent(ui, status.content, emojis = status.emojis)
                status.mediaAttachments.forEach { media -> MediaView(ui, media) }
                status.poll?.let { PollView(ui, it) }
                status.card?.let { card ->
                    if (card.title.isNotBlank() && safeWebUrl(card.url) != null)
                        OutlinedCard(
                            onClick = { ui.open(card.url) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(card.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    card.description,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                }
                status.quote?.let { quote ->
                    if (quote.quotedStatus != null && !nested)
                        StatusCard(ui, quote.quotedStatus, nested = true)
                    else
                        TextButton(
                            onClick = {
                                quote.quotedStatusId?.let { ui.navigate(Screen.Thread(it)) }
                            },
                            enabled = quote.quotedStatusId != null,
                        ) {
                            Text("Quote: ${quote.state}")
                        }
                }
            }
            TextButton(
                modifier = Modifier.semantics { contentDescription = status.createdAt },
                onClick = { ui.navigate(Screen.Thread(status.id)) },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    RelativeTimestamp(status.createdAt) +
                        if (status.editedAt != null) " · ${tr("mastodon_edited")}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (!nested) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(
                        modifier =
                            Modifier.semantics {
                                contentDescription = "View replies, ${status.repliesCount} replies"
                            },
                        onClick = { ui.navigate(Screen.Thread(status.id)) },
                    ) {
                        Text("↩ ${status.repliesCount}")
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
                        Text("${if(status.reblogged) "↻✓" else "↻"} ${status.reblogsCount}")
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
                        Text("${if(status.favourited) "♥" else "♡"} ${status.favouritesCount}")
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
                        Text(if (status.bookmarked) "▣" else "▢")
                    }
                    TextButton(
                        modifier = Modifier.semantics { contentDescription = "Quote post" },
                        onClick = {
                            ui.requireLogin { ui.navigate(Screen.Compose(quote = status)) }
                        },
                        enabled = status.visibility in listOf("public", "unlisted"),
                    ) {
                        Text("❞")
                    }
                }
            }
        }
    }
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
fun MediaView(ui: UiContext, media: MediaAttachment) {
    var viewer by remember(media.id) { mutableStateOf(false) }
    val url = safeWebUrl(media.url.orEmpty()) ?: safeWebUrl(media.remoteUrl.orEmpty())
    val preview = safeWebUrl(media.previewUrl.orEmpty()) ?: url
    Column {
        if (media.type in listOf("video", "gifv", "audio") && url != null) {
            NativeVideoPlayer(
                url,
                Modifier.fillMaxWidth().height(if (media.type == "audio") 90.dp else 240.dp),
            )
        } else {
            AsyncImage(
                preview,
                media.description ?: media.type,
                Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 320.dp).clickable(
                    enabled = url != null
                ) {
                    if (media.type == "image") viewer = true else ui.open(url)
                },
                contentScale = ContentScale.Fit,
            )
        }
        if (!media.description.isNullOrBlank())
            Text(media.description.orEmpty(), style = MaterialTheme.typography.bodySmall)
        if (media.type != "image")
            TextButton(onClick = { ui.open(url) }, enabled = url != null) {
                Text(tr("mastodon_open_in_browser"))
            }
    }
    if (viewer && url != null) ImageViewer(ui, media, url) { viewer = false }
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
