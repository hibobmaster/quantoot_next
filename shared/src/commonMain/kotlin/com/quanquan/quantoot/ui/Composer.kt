package com.quanquan.quantoot.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quanquan.quantoot.data.*
import com.quanquan.quantoot.model.*
import kotlinx.coroutines.*

private class DraftDefault<T>(initial: T) {
    var value by mutableStateOf(initial)
        private set
    var baseline = initial
        private set
    private var touched = false

    fun choose(value: T) {
        touched = true
        this.value = value
    }

    fun apply(value: T) {
        if (touched) return
        this.value = value
        baseline = value
    }
}

class ComposerDraft(private val route: Screen.Compose, me: Account?) {
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
    private val sensitiveDefault = DraftDefault(edit?.sensitive ?: false)
    private val visibilityDefault =
        DraftDefault(
            edit?.visibility ?: route.reply?.visibility ?: me?.source?.privacy ?: "public"
        )
    private val languageDefault = DraftDefault(
        if (edit != null) edit.language.orEmpty() else me?.source?.language.orEmpty()
    )
    private val quotePolicyDefault = DraftDefault(if (edit != null) "" else "public")
    val sensitive get() = sensitiveDefault.value
    val visibility get() = visibilityDefault.value
    val language get() = languageDefault.value
    val quotePolicy get() = quotePolicyDefault.value
    var attachments by mutableStateOf(edit?.mediaAttachments.orEmpty())
    var pollEnabled by mutableStateOf(edit?.poll != null)
    var options by mutableStateOf(edit?.poll?.options?.map { it.title } ?: listOf("", ""))
    var multiple by mutableStateOf(edit?.poll?.multiple ?: false)
    var duration by mutableStateOf(86400)
    var hideTotals by mutableStateOf(false)
    private var lastPayload: StatusDraft? = null
    private var operationId = randomOperationId()
    private val cleanPayload = effectivePayload()
    private val cleanAttachments = attachmentEdits()

    fun chooseVisibility(value: String) {
        if (edit == null) visibilityDefault.choose(value)
    }
    fun chooseLanguage(value: String) { languageDefault.choose(value) }
    fun chooseQuotePolicy(value: String) { quotePolicyDefault.choose(value) }
    fun chooseSensitive(value: Boolean) { sensitiveDefault.choose(value) }

    fun applyDefaults(settings: AccountSettings) {
        if (edit != null) return
        languageDefault.apply(settings.language)
        quotePolicyDefault.apply(settings.quotePolicy)
        sensitiveDefault.apply(settings.sensitive)
        if (route.reply == null) visibilityDefault.apply(settings.privacy)
    }

    fun payload(): StatusDraft {
        check(edit?.poll == null) { "Editing an existing poll is not supported yet" }
        return effectivePayload()
    }

    private fun effectivePayload(): StatusDraft =
        StatusDraft(
            text,
            visibility,
            language.ifBlank { null },
            spoiler,
            sensitive,
            route.reply?.id ?: edit?.inReplyToId,
            attachments.map { it.id },
            if (pollEnabled) PollDraft(options.map { it.trim() }, duration, multiple, hideTotals)
            else null,
            quotePolicy.ifBlank { null },
            route.quote?.id ?: edit?.quote?.quotedStatusId,
        )

    fun keyFor(payload: StatusDraft): String {
        if (lastPayload != payload) {
            lastPayload = payload
            operationId = randomOperationId()
        }
        return operationId
    }

    private fun attachmentEdits() = attachments.map { it.id to it.description.orEmpty() }

    val dirty
        get() =
            effectivePayload() != cleanPayload.copy(
                visibility = visibilityDefault.baseline,
                language = languageDefault.baseline.ifBlank { null },
                quotePolicy = quotePolicyDefault.baseline.ifBlank { null },
                sensitive = sensitiveDefault.baseline,
            ) || attachmentEdits() != cleanAttachments
}

private enum class ComposerPanel { Emoji, Visibility, Language, Options, QuotePolicy }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposerScreen(ui: UiContext, route: Screen.Compose) {
    val title = tr(when {
        route.edit != null -> "mastodon_edit_post"
        route.reply != null -> "mastodon_button_reply"
        else -> "mastodon_new_post"
    })
    if (route.edit?.poll != null) {
        Column(Modifier.fillMaxSize()) {
            EditorHeader(title, ui::requestBack)
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(tr("mastodon_edit_poll_in_browser"))
                Button(onClick = { ui.open(route.edit.url) }, enabled = safeWebUrl(route.edit.url.orEmpty()) != null) {
                    Text(tr("mastodon_open_in_browser"))
                }
            }
        }
        return
    }
    val key = "${route.edit?.id ?: "new"}:${route.reply?.id.orEmpty()}:${route.quote?.id.orEmpty()}"
    val draft = remember(key) { ui.drafts.getOrPut(key) { ComposerDraft(route, ui.me) } }
    var publishing by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var limit by remember { mutableIntStateOf(500) }
    var error by remember { mutableStateOf<String?>(null) }
    var discard by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<ComposerPanel?>(null) }
    var emojis by remember { mutableStateOf<List<CustomEmoji>?>(null) }
    var emojiError by remember { mutableStateOf<String?>(null) }
    var emojiAttempt by remember { mutableIntStateOf(0) }
    var suggestions by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var showWarning by remember(draft) { mutableStateOf(draft.spoiler.isNotBlank()) }
    var previousWarning by remember(draft) { mutableStateOf("") }
    var editingAlt by remember { mutableStateOf<MediaAttachment?>(null) }
    var alt by remember { mutableStateOf("") }
    var savingAlt by remember { mutableStateOf(false) }
    var altError by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val unknownError = tr("mastodon_unknown_error")
    val bodyFocus = remember { FocusRequester() }
    val warningFocus = remember { FocusRequester() }
    val pollFocus = remember { FocusRequester() }
    var requestedFocus by remember { mutableStateOf(bodyFocus) }
    LaunchedEffect(requestedFocus) { requestedFocus.requestFocus() }

    fun openPanel(next: ComposerPanel) {
        keyboard?.hide()
        panel = next
    }
    LaunchedEffect(ui.api, panel, emojiAttempt) {
        if (panel == ComposerPanel.Emoji && emojis == null) {
            emojiError = null
            try {
                emojis = ui.api.emojis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emojiError = e.message ?: unknownError
            }
        }
    }
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
    DisposableEffect(ui, draft) {
        ui.beforeBack = {
            if (!publishing) {
                if (draft.dirty || uploading) discard = true
                else {
                    ui.drafts.remove(key)
                    ui.back()
                }
            }
        }
        onDispose { ui.beforeBack = null }
    }
    LaunchedEffect(ui.api, draft) {
        try {
            limit = ui.api.serverInfo().maxCharacters
            if (route.edit == null) draft.applyDefaults(ui.api.accountSettings())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: unknownError
        }
    }
    val count = draft.text.codePointCount() + draft.spoiler.codePointCount()
    val canPublish = !publishing && !uploading && count <= limit &&
        (draft.text.isNotBlank() || draft.attachments.isNotEmpty()) &&
        (!draft.pollEnabled || draft.options.all { it.isNotBlank() })

    fun publish() {
        if (!canPublish || publishing) return
        publishing = true
        error = null
        keyboard?.hide()
        scope.launch {
            try {
                val payload = draft.payload()
                val result = ui.api.publish(payload, route.edit?.id, draft.keyFor(payload))
                ui.drafts.remove(key)
                ui.published(route, result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: unknownError
            } finally {
                publishing = false
            }
        }
    }

    fun attachMedia() {
        if (uploading || publishing || draft.pollEnabled || draft.attachments.size >= 4) return
        uploading = true
        error = null
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
                error = e.message ?: unknownError
            } finally {
                uploading = false
            }
        }
    }

    val languageOptions = composerLanguages(ui.platform.languageTag, draft.language)
    val languageLabel = languageOptions.first { it.first == draft.language }.second
    val visibilityLabel = tr(PostVisibility.from(draft.visibility).labelKey)
    val quotePolicies = if (route.edit == null) quoteOptions()
        else listOf("" to tr("mastodon_keep_current_setting")) + quoteOptions()
    val quoteLabel = quotePolicies.firstOrNull { it.first == draft.quotePolicy }?.second ?: draft.quotePolicy

    Column(Modifier.fillMaxSize().imePadding()) {
        EditorHeader(title, ui::requestBack, closeEnabled = !publishing) {
            Button(
                onClick = ::publish,
                enabled = canPublish,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                if (publishing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                }
                Text(tr(if (route.edit == null) "mastodon_publish" else "mastodon_save_changes"))
            }
        }
        error?.let { message ->
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f).padding(vertical = 6.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { error = null }) { Icon(Icons.Default.Close, tr("mastodon_close")) }
                }
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            route.reply?.let { ComposerContext(it, tr("mastodon_reply_to", "name" to it.account.name)) }
            ui.me?.let { account ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccountAvatar(account, 44.dp)
                    Column(Modifier.weight(1f)) {
                        EmojiText(account.name, account.emojis, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        val handle = account.acct.let { if ('@' in it) it else "$it@${ui.api.instance}" }
                        Text("@$handle", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                if (route.edit == null) {
                    ComposerChoice(tr("mastodon_visibility"), visibilityLabel, visibilityLabel, enabled = !publishing,
                        icon = composerVisibilityIcon(draft.visibility), onClick = { openPanel(ComposerPanel.Visibility) })
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(composerVisibilityIcon(draft.visibility), null, Modifier.size(16.dp))
                        Text(visibilityLabel, style = MaterialTheme.typography.labelLarge)
                    }
                }
                ComposerChoice(tr("mastodon_post_language"), languageLabel,
                    if (draft.language.isBlank()) tr("settings_language") else draft.language.uppercase(),
                    enabled = !publishing, onClick = { openPanel(ComposerPanel.Language) })
                if (route.edit != null) ComposerChoice(tr("mastodon_compose_quote_policy"), quoteLabel,
                    tr("mastodon_compose_quote_policy"), enabled = !publishing,
                    onClick = { openPanel(ComposerPanel.QuotePolicy) })
            }
            if (route.edit != null) Text(tr("mastodon_visibility_fixed"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showWarning) OutlinedTextField(
                draft.spoiler,
                { draft.spoiler = it },
                label = { Text(tr("mastodon_content_warning")) },
                modifier = Modifier.fillMaxWidth().focusRequester(warningFocus),
                shape = MaterialTheme.shapes.medium,
                enabled = !publishing,
            )
            val bodyStyle = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Normal, fontSize = 21.sp, lineHeight = 30.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val contentLabel = tr("mastodon_content")
            BasicTextField(
                value = draft.textInput,
                onValueChange = { draft.textInput = it },
                modifier = Modifier.fillMaxWidth().focusRequester(bodyFocus).semantics { contentDescription = contentLabel },
                minLines = 3,
                textStyle = bodyStyle,
                enabled = !publishing,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { input ->
                    Box {
                        if (draft.text.isEmpty()) Text(tr("mastodon_what_is_on_your_mind"), style = bodyStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        input()
                    }
                },
            )
            if (count > limit) Text(tr("mastodon_char_limit_exceeded"), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error)
            if (suggestions.isNotEmpty() && hashtag != null) FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                suggestions.forEach { tag ->
                    TextButton(enabled = !publishing, onClick = {
                        draft.textInput = completeHashtag(draft.textInput, hashtag, tag.name)
                    }) { Text("#${tag.name}") }
                }
            }
            route.quote?.let { ComposerContext(it, tr("mastodon_quoted_post") + " · @${it.account.acct}") }
            if (draft.pollEnabled) PollEditor(draft, pollFocus, !publishing)
            draft.attachments.forEach { media ->
                DraftAttachment(media, enabled = !publishing, onEdit = {
                    alt = media.description.orEmpty()
                    altError = null
                    editingAlt = media
                }, onRemove = { draft.attachments = draft.attachments.filterNot { it.id == media.id } })
            }
            if (uploading) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(tr("mastodon_uploading"), style = MaterialTheme.typography.labelLarge)
            }
        }
        ComposerToolbar(
            count = count, limit = limit, enabled = !publishing,
            canAttach = !uploading && !draft.pollEnabled && draft.attachments.size < 4,
            canPoll = !uploading && draft.attachments.isEmpty(),
            showWarning = showWarning, showPoll = draft.pollEnabled,
            onAttach = ::attachMedia, onEmoji = { openPanel(ComposerPanel.Emoji) },
            onWarning = { checked ->
                if (checked) draft.spoiler = previousWarning else {
                    previousWarning = draft.spoiler
                    draft.spoiler = ""
                }
                showWarning = checked
                requestedFocus = if (checked) warningFocus else bodyFocus
            },
            onPoll = { checked ->
                draft.pollEnabled = checked
                requestedFocus = if (checked) pollFocus else bodyFocus
            },
            onOptions = { openPanel(ComposerPanel.Options) },
        )
    }
    val dismissPanel = { panel = null }
    when (panel) {
        null -> Unit
        ComposerPanel.Emoji -> EmojiPickerSheet(
            emojis.orEmpty(), loading = emojis == null && emojiError == null,
            error = emojiError, onRetry = { emojiAttempt++ },
            onSelect = {
                draft.textInput = insertAtSelection(draft.textInput, it)
                dismissPanel()
            },
            onDismiss = dismissPanel,
        )
        ComposerPanel.Visibility -> ChoiceSheet(tr("mastodon_visibility"), draft.visibility, visibilityOptions(),
            draft::chooseVisibility, dismissPanel)
        ComposerPanel.Language -> ChoiceSheet(tr("mastodon_post_language"), draft.language, languageOptions,
            draft::chooseLanguage, dismissPanel)
        ComposerPanel.QuotePolicy -> ChoiceSheet(tr("mastodon_compose_quote_policy"), draft.quotePolicy, quotePolicies,
            draft::chooseQuotePolicy, dismissPanel)
        ComposerPanel.Options -> ModalBottomSheet(
            onDismissRequest = dismissPanel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("mastodon_post_options"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = dismissPanel) { Icon(Icons.Default.Close, tr("mastodon_close")) }
                }
                Toggle(tr("mastodon_sensitive_content"), draft.sensitive, onChange = draft::chooseSensitive)
                Choice(tr("mastodon_compose_quote_policy"), draft.quotePolicy, quotePolicies, draft::chooseQuotePolicy)
            }
        }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false },
        title = { Text(tr("mastodon_discard_draft")) },
        confirmButton = {
            TextButton(onClick = {
                ui.drafts.remove(key)
                discard = false
                ui.back()
            }) { Text(tr("mastodon_discard"), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { discard = false }) { Text(tr("mastodon_cancel")) } },
    )
    editingAlt?.let { media ->
        AlertDialog(
            onDismissRequest = { if (!savingAlt) editingAlt = null },
            title = { Text(tr("mastodon_edit_description")) },
            text = {
                Column {
                    OutlinedTextField(alt, { alt = it }, label = { Text(tr("mastodon_description_hint")) },
                        enabled = !savingAlt, minLines = 3)
                    ErrorNotice(altError)
                }
            },
            confirmButton = {
                TextButton(enabled = !savingAlt, onClick = {
                    if (savingAlt) return@TextButton
                    savingAlt = true
                    altError = null
                    scope.launch {
                        try {
                            val updated = ui.api.updateMedia(media.id, alt)
                            draft.attachments = draft.attachments.map { if (it.id == media.id) updated else it }
                            editingAlt = null
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { altError = e.message ?: unknownError }
                        finally { savingAlt = false }
                    }
                }) { Text(tr("mastodon_save")) }
            },
            dismissButton = { TextButton(enabled = !savingAlt, onClick = { editingAlt = null }) { Text(tr("mastodon_cancel")) } },
        )
    }
}

@Composable
private fun composerLanguages(languageTag: String, selected: String): List<Pair<String, String>> {
    val systemLanguage = languageTag.substringBefore('-').lowercase()
    val options = listOf("" to tr("mastodon_language_default")) +
        postLanguages.filter { it.first == systemLanguage }.map {
            it.first to "${tr("settings_language_system")} (${it.second})"
        } + postLanguages.filterNot { it.first == systemLanguage }
    return if (options.any { it.first == selected }) options else options + (selected to selected)
}

private fun String.codePointCount(): Int = count { it !in '\uDC00'..'\uDFFF' }
