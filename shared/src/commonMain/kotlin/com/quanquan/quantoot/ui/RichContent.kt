package com.quanquan.quantoot.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.safeWebUrl
import com.quanquan.quantoot.model.*
import io.github.alexzhirkevich.qrose.ImageFormat
import io.github.alexzhirkevich.qrose.rememberQrCodePainter
import io.github.alexzhirkevich.qrose.toByteArray
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.delay

internal fun AnnotatedString.Builder.appendEmojiText(value: String, shortcodes: Set<String>) {
    var cursor = 0
    Regex(":([A-Za-z0-9_]+):").findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        if (match.groupValues[1] in shortcodes)
            appendInlineContent("emoji:${match.groupValues[1]}", match.value)
        else append(match.value)
        cursor = match.range.last + 1
    }
    append(value.substring(cursor))
}

@Composable
internal fun emojiContent(emojis: List<CustomEmoji>): Map<String, InlineTextContent> =
    emojis
        .mapNotNull { emoji ->
            val url = safeWebUrl(emoji.url) ?: safeWebUrl(emoji.staticUrl) ?: return@mapNotNull null
            "emoji:${emoji.shortcode}" to
                InlineTextContent(
                    Placeholder(1.15.em, 1.15.em, PlaceholderVerticalAlign.TextCenter)
                ) {
                    AsyncImage(
                        url,
                        ":${emoji.shortcode}:",
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
        }
        .toMap()

@Composable
internal fun EmojiText(
    value: String,
    emojis: List<CustomEmoji>,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
) {
    val inline = emojiContent(emojis)
    val text =
        remember(value, emojis) {
            buildAnnotatedString { appendEmojiText(value, emojis.map { it.shortcode }.toSet()) }
        }
    Text(
        text,
        inlineContent = inline,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

internal fun relativeTimestamp(value: String, now: Instant, strings: Strings): String {
    val time = runCatching { Instant.parse(value) }.getOrNull() ?: return value
    val seconds = (now - time).inWholeSeconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> strings.text("mastodon_time_now")
        seconds < 3600 -> strings.text("mastodon_time_minutes_ago", "n" to (seconds / 60).toString())
        seconds < 86400 -> strings.text("mastodon_time_hours_ago", "n" to (seconds / 3600).toString())
        seconds < 604800 -> strings.text("mastodon_time_days_ago", "n" to (seconds / 86400).toString())
        else -> value.take(10)
    }
}

@Composable
internal fun RelativeTimestamp(value: String): String {
    var now by remember(value) { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(value) {
        while (true) {
            delay(60_000)
            now = Clock.System.now()
        }
    }
    return relativeTimestamp(value, now, LocalStrings.current)
}

@Composable
internal fun ProfileQr(ui: UiContext, account: Account, dismiss: () -> Unit) {
    val url = safeWebUrl(account.url) ?: return
    val painter = rememberQrCodePainter(url)
    var saving by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { EmojiText(account.name, account.emojis) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter,
                    "Profile QR code for @${account.acct}",
                    Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White).padding(20.dp),
                )
                Text("@${account.acct}")
                Text(url, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = {
                        saving = true
                        ui.run {
                            try {
                                ui.platform.saveImageBytes(
                                    painter.toByteArray(1024, 1024, ImageFormat.PNG),
                                    "quantoot-${account.id}.png",
                                )
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving,
                ) {
                    Text(tr("mastodon_save_to_gallery"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { ui.platform.share(url) }) { Text(tr("mastodon_button_share")) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(tr("mastodon_close")) } },
    )
}
