package com.quanquan.quantoot.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.quanquan.quantoot.data.*
import com.quanquan.quantoot.model.*
import kotlinx.coroutines.*

class ComposerDraft(route: Screen.Compose, me: Account?) {
    private val edit = route.edit
    private val replyMentions =
        route.reply
            ?.let { reply ->
                (listOf(reply.account.acct) + reply.mentions.map { it.acct })
                    .distinct()
                    .filterNot { it == me?.acct }
                    .joinToString(" ") { "@$it" } + " "
            }
            .orEmpty()
    var text by
        mutableStateOf(
            edit?.let { status ->
                val content = plainText(status.content)
                val missing =
                    status.mentions
                        .filterNot {
                            content.contains("@${it.acct}") || content.contains("@${it.username}")
                        }
                        .joinToString(" ") { "@${it.acct}" }
                listOf(missing, content).filter { it.isNotBlank() }.joinToString(" ")
            } ?: replyMentions
        )
    var spoiler by mutableStateOf(edit?.spoilerText ?: route.reply?.spoilerText.orEmpty())
    var sensitive by mutableStateOf(edit?.sensitive ?: false)
    var visibility by
        mutableStateOf(
            edit?.visibility ?: route.reply?.visibility ?: me?.source?.privacy ?: "public"
        )
    var language by mutableStateOf(edit?.language ?: me?.source?.language.orEmpty())
    var quotePolicy by mutableStateOf("public")
    var attachments by mutableStateOf(edit?.mediaAttachments.orEmpty())
    var pollEnabled by mutableStateOf(edit?.poll != null)
    var options by mutableStateOf(edit?.poll?.options?.map { it.title } ?: listOf("", ""))
    var multiple by mutableStateOf(edit?.poll?.multiple ?: false)
    var duration by mutableStateOf(86400)
    var hideTotals by mutableStateOf(false)
    private var lastPayload: StatusDraft? = null
    private var operationId = randomOperationId()

    fun keyFor(payload: StatusDraft): String {
        if (lastPayload != payload) {
            lastPayload = payload
            operationId = randomOperationId()
        }
        return operationId
    }

    val initialText = text
    val dirty
        get() =
            text != initialText || attachments.isNotEmpty() || spoiler.isNotBlank() || pollEnabled
}

@Composable
fun ComposerScreen(ui: UiContext, route: Screen.Compose) {
    val key = "${route.edit?.id ?: "new"}:${route.reply?.id.orEmpty()}:${route.quote?.id.orEmpty()}"
    val draft = remember(key) { ui.drafts.getOrPut(key) { ComposerDraft(route, ui.me) } }
    var busy by remember { mutableStateOf(false) }
    var limit by remember { mutableIntStateOf(500) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var emojis by remember { mutableStateOf<List<CustomEmoji>>(emptyList()) }
    var showEmoji by remember { mutableStateOf(false) }
    var editingAlt by remember { mutableStateOf<MediaAttachment?>(null) }
    var alt by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    DisposableEffect(ui) {
        ui.beforeBack = { if (draft.dirty) discard = true else ui.back() }
        onDispose { ui.beforeBack = null }
    }
    LaunchedEffect(ui.api) {
        try {
            limit = ui.api.serverInfo().maxCharacters
            if (route.edit == null && draft.text == draft.initialText) {
                val settings = ui.api.accountSettings()
                draft.language = settings.language
                draft.quotePolicy = settings.quotePolicy
                draft.sensitive = settings.sensitive
                if (route.reply == null) draft.visibility = settings.privacy
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    val count = draft.text.codePointCount() + draft.spoiler.codePointCount()
    fun publish() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                val payload =
                    StatusDraft(
                        draft.text,
                        draft.visibility,
                        draft.language.ifBlank { null },
                        draft.spoiler,
                        draft.sensitive,
                        route.reply?.id ?: route.edit?.inReplyToId,
                        draft.attachments.map { it.id },
                        if (draft.pollEnabled)
                            PollDraft(
                                draft.options.map { it.trim() },
                                draft.duration,
                                draft.multiple,
                                draft.hideTotals,
                            )
                        else null,
                        draft.quotePolicy,
                        route.quote?.id ?: route.edit?.quote?.quotedStatusId,
                    )
                val result = ui.api.publish(payload, route.edit?.id, draft.keyFor(payload))
                ui.drafts.remove(key)
                ui.update(result)
                ui.back()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ErrorNotice(error) { error = null }
        route.reply?.let {
            Text(tr("mastodon_reply_to", "name" to it.account.name))
            Text(plainText(it.content).take(240), style = MaterialTheme.typography.bodySmall)
        }
        route.quote?.let {
            Text("❞ @${it.account.acct}")
            Text(plainText(it.content).take(240), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            draft.spoiler,
            { draft.spoiler = it },
            label = { Text(tr("mastodon_content_warning")) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
        )
        OutlinedTextField(
            draft.text,
            { draft.text = it },
            label = { Text(tr("mastodon_content")) },
            placeholder = { Text(tr("mastodon_what_is_on_your_mind")) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            enabled = !busy,
            isError = count > limit,
        )
        Text(
            "$count / $limit",
            color =
                if (count > limit) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        draft.attachments.forEach { media ->
            Card {
                Column(Modifier.padding(12.dp)) {
                    AsyncImage(
                        safeWebUrl(media.previewUrl ?: media.url.orEmpty()),
                        media.description,
                        Modifier.fillMaxWidth().height(120.dp),
                    )
                    Text(media.description.orEmpty())
                    Row {
                        TextButton(
                            onClick = {
                                editingAlt = media
                                alt = media.description.orEmpty()
                            },
                            enabled = !busy,
                        ) {
                            Text(tr("mastodon_add_alt_text"))
                        }
                        TextButton(
                            onClick = {
                                draft.attachments =
                                    draft.attachments.filterNot { it.id == media.id }
                            },
                            enabled = !busy,
                        ) {
                            Text(tr("mastodon_remove_attachment"))
                        }
                    }
                }
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        try {
                            val files = ui.platform.pickMedia(4 - draft.attachments.size)
                            for (file in files) {
                                val uploaded = ui.api.upload(file)
                                draft.attachments = draft.attachments + uploaded
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && !draft.pollEnabled && draft.attachments.size < 4,
            ) {
                Text(tr("mastodon_add_media"))
            }
            TextButton(
                onClick = {
                    showEmoji = !showEmoji
                    if (emojis.isEmpty()) ui.run { emojis = ui.api.emojis() }
                }
            ) {
                Text(tr("mastodon_emoji"))
            }
        }
        if (showEmoji) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("😀", "🎉", "❤️", "👍", "🌱").forEach { emoji ->
                    TextButton(onClick = { draft.text += emoji }) { Text(emoji) }
                }
            }
            emojis.chunked(5).forEach { row ->
                Row {
                    row.forEach { emoji ->
                        TextButton(
                            onClick = { draft.text += " :${emoji.shortcode}: " },
                            modifier = Modifier.weight(1f),
                        ) {
                            Column {
                                AsyncImage(
                                    safeWebUrl(emoji.staticUrl.ifBlank { emoji.url }),
                                    emoji.shortcode,
                                    Modifier.size(28.dp),
                                )
                                Text(
                                    emoji.shortcode,
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }
        }
        Toggle(tr("mastodon_sensitive_content"), draft.sensitive) { draft.sensitive = it }
        if (draft.attachments.isEmpty())
            Toggle(tr("mastodon_poll"), draft.pollEnabled) { draft.pollEnabled = it }
        if (draft.pollEnabled) {
            draft.options.forEachIndexed { i, option ->
                Row {
                    OutlinedTextField(
                        option,
                        { value ->
                            draft.options =
                                draft.options.mapIndexed { index, old ->
                                    if (index == i) value else old
                                }
                        },
                        label = {
                            Text(tr("mastodon_poll_option_hint", "n" to (i + 1).toString()))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    if (draft.options.size > 2)
                        TextButton(
                            onClick = {
                                draft.options =
                                    draft.options.filterIndexed { index, _ -> i != index }
                            }
                        ) {
                            Text("−")
                        }
                }
            }
            if (draft.options.size < 4)
                TextButton(onClick = { draft.options = draft.options + "" }) {
                    Text(tr("mastodon_add_poll_option"))
                }
            Toggle(tr("mastodon_compose_poll_multiple_choice"), draft.multiple) {
                draft.multiple = it
            }
            Toggle("Hide totals until poll closes", draft.hideTotals) { draft.hideTotals = it }
            Choice(
                tr("mastodon_poll_length"),
                draft.duration.toString(),
                listOf(
                    "300" to "5 min",
                    "1800" to "30 min",
                    "3600" to "1 hour",
                    "21600" to "6 hours",
                    "86400" to "1 day",
                    "259200" to "3 days",
                    "604800" to "7 days",
                ),
            ) {
                draft.duration = it.toInt()
            }
        }
        Choice(tr("mastodon_visibility"), draft.visibility, visibilityOptions()) {
            draft.visibility = it
        }
        OutlinedTextField(
            draft.language,
            { draft.language = it },
            label = { Text(tr("mastodon_post_language")) },
            placeholder = { Text("en / zh / zh-TW") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Choice(tr("mastodon_compose_quote_policy"), draft.quotePolicy, quoteOptions()) {
            draft.quotePolicy = it
        }
        Row {
            Button(
                onClick = ::publish,
                enabled =
                    !busy &&
                        count <= limit &&
                        (draft.text.isNotBlank() || draft.attachments.isNotEmpty()) &&
                        (!draft.pollEnabled || draft.options.all { it.isNotBlank() }),
            ) {
                Text(tr(if (route.edit == null) "mastodon_publish" else "mastodon_save_changes"))
            }
            TextButton(
                onClick = { if (draft.dirty) discard = true else ui.back() },
                enabled = !busy,
            ) {
                Text(tr("mastodon_cancel"))
            }
        }
        if (busy) Loading()
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(tr("mastodon_discard_draft")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ui.drafts.remove(key)
                        discard = false
                        ui.back()
                    }
                ) {
                    Text(tr("mastodon_discard"))
                }
            },
            dismissButton = {
                TextButton(onClick = { discard = false }) { Text(tr("mastodon_cancel")) }
            },
        )
    editingAlt?.let { media ->
        AlertDialog(
            onDismissRequest = { editingAlt = null },
            title = { Text(tr("mastodon_edit_description")) },
            text = {
                OutlinedTextField(
                    alt,
                    { alt = it },
                    label = { Text(tr("mastodon_description_hint")) },
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ui.run {
                            val updated = ui.api.updateMedia(media.id, alt)
                            draft.attachments =
                                draft.attachments.map { if (it.id == media.id) updated else it }
                            editingAlt = null
                        }
                    }
                ) {
                    Text(tr("mastodon_save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingAlt = null }) { Text(tr("mastodon_cancel")) }
            },
        )
    }
}

private fun String.codePointCount(): Int = count { it !in '\uDC00'..'\uDFFF' }
