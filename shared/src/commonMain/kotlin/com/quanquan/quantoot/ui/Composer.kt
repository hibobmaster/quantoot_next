package com.quanquan.quantoot.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.layout.ContentScale
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
    private val startingText =
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
    var textInput by mutableStateOf(TextFieldValue(startingText, TextRange(startingText.length)))
    var text: String
        get() = textInput.text
        set(value) { textInput = TextFieldValue(value, TextRange(value.length)) }
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
    var emojisLoading by remember { mutableStateOf(false) }
    var emojisLoaded by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var showWarning by remember(draft) { mutableStateOf(draft.spoiler.isNotBlank()) }
    var editingAlt by remember { mutableStateOf<MediaAttachment?>(null) }
    var alt by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val hashtag = remember(draft.textInput) { hashtagAtCaret(draft.textInput) }
    LaunchedEffect(ui.api, hashtag?.query, hashtag?.start) {
        suggestions = emptyList()
        val token = hashtag ?: return@LaunchedEffect
        delay(240)
        try {
            suggestions = (if (token.query.isEmpty()) ui.api.trendingTags()
                else ui.api.search(token.query, type = "hashtags").hashtags)
                .distinctBy { it.name.lowercase() }.take(3)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            suggestions = emptyList()
        }
    }
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
                ui.published(route, result)
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
        Toggle(tr("mastodon_content_warning"), showWarning) {
            showWarning = it
            if (!it) draft.spoiler = ""
        }
        if (showWarning) OutlinedTextField(
                draft.spoiler,
                { draft.spoiler = it },
                label = { Text(tr("mastodon_content_warning")) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = !busy,
            )
        OutlinedTextField(
            draft.textInput,
            { draft.textInput = it },
            label = { Text(tr("mastodon_content")) },
            placeholder = { Text(tr("mastodon_what_is_on_your_mind")) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 6,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ),
            enabled = !busy,
            isError = count > limit,
        )
        if (suggestions.isNotEmpty() && hashtag != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
            ) {
                Column {
                    suggestions.forEach { tag ->
                        TextButton(
                            onClick = { draft.textInput = completeHashtag(draft.textInput, hashtag, tag.name) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("#${tag.name}") }
                    }
                }
            }
        }
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
                        contentScale = ContentScale.Crop,
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
            FilledTonalButton(
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
                Icon(Icons.Default.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("mastodon_add_media"))
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = {
                    keyboard?.hide()
                    showEmoji = true
                    if (!emojisLoaded && !emojisLoading) {
                        emojisLoading = true
                        scope.launch {
                            try {
                                emojis = ui.api.emojis()
                                emojisLoaded = true
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message }
                            finally { emojisLoading = false }
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Face, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("mastodon_emoji"))
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
        val systemLanguage = ui.platform.languageTag.substringBefore('-').lowercase()
        val languages = listOf("" to tr("mastodon_language_default")) +
            postLanguages.filter { it.first == systemLanguage }.map {
                it.first to "${tr("settings_language_system")} (${it.second})"
            } + postLanguages.filterNot { it.first == systemLanguage }
        Choice(tr("mastodon_post_language"), draft.language,
            if (draft.language.isNotBlank() && languages.none { it.first == draft.language })
                languages + (draft.language to draft.language)
            else languages,
        ) { draft.language = it }
        Choice(tr("mastodon_compose_quote_policy"), draft.quotePolicy, quoteOptions()) {
            draft.quotePolicy = it
        }
        Row(Modifier.fillMaxWidth()) {
            Button(
                onClick = ::publish,
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
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
    if (showEmoji) EmojiPickerSheet(
        emojis,
        emojisLoading,
        onSelect = { draft.textInput = insertAtSelection(draft.textInput, it) },
        onDismiss = { showEmoji = false },
    )
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
