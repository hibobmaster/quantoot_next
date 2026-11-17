package com.quanquan.quantoot.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import com.quanquan.quantoot.auth.SessionState
import com.quanquan.quantoot.model.*
import kotlinx.coroutines.*

@Composable
fun SettingsScreen(ui: UiContext, state: SessionState) {
    var section by remember { mutableStateOf<String?>(null) }
    val loader = SingletonImageLoader.get(LocalPlatformContext.current)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (section != null)
            TextButton(onClick = { section = null }) { Text("‹ ${tr("settings_title")}") }
        when (section) {
            null -> {
                Choice(tr("settings_language"), state.locale ?: "system", languageOptions()) {
                    ui.run { ui.store.setLocale(it) }
                }
                OutlinedButton(
                    onClick = {
                        ui.run {
                            loader.memoryCache?.clear()
                            withContext(Dispatchers.Default) { loader.diskCache?.clear() }
                            ui.message = "Media cache cleared"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tr("settings_clear_media_cache"))
                }
                if (ui.me != null) {
                    listOf(
                            "privacy" to "mastodon_settings_privacy",
                            "defaults" to "mastodon_settings_posting_defaults",
                            "filters" to "mastodon_settings_filters",
                            "policy" to "mastodon_settings_notifications",
                        )
                        .forEach { (key, label) ->
                            OutlinedButton(
                                onClick = { section = key },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(tr(label))
                            }
                        }
                }
                OutlinedButton(
                    onClick = { section = "server" },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tr("mastodon_settings_about_server"))
                }
                OutlinedButton(
                    onClick = { section = "privacyPolicy" },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(tr("settings_privacy_policy"))
                }
                Text("QuanToot", style = MaterialTheme.typography.headlineSmall)
                Text("0.6.0", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { section = "licenses" }) {
                    Text(tr("settings_open_source_licenses"))
                }
                TextButton(
                    onClick = {
                        ui.platform.share("QuanToot support: BobMaster <bobmaster@hibobmaster.com>")
                    }
                ) {
                    Text("${tr("settings_developer_support")}: BobMaster")
                }
                TextButton(onClick = { ui.open("https://quanquan.space/") }) {
                    Text(tr("settings_feedback"))
                }
            }
            "privacy",
            "defaults" -> AccountDefaults(ui, section == "privacy")
            "filters" -> FiltersSettings(ui)
            "policy" -> NotificationSettings(ui)
            "server" -> ServerSettings(ui)
            "privacyPolicy" -> PrivacyPolicy(ui)
            "licenses" -> {
                Text(
                    tr("settings_open_source_licenses"),
                    style = MaterialTheme.typography.titleLarge,
                )
                listOf(
                        "Kotlin · Apache 2.0" to
                            "https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt",
                        "Compose Multiplatform · Apache 2.0" to
                            "https://github.com/JetBrains/compose-multiplatform/blob/master/LICENSE.txt",
                        "Ktor · Apache 2.0" to "https://github.com/ktorio/ktor/blob/main/LICENSE",
                        "Coil · Apache 2.0" to
                            "https://github.com/coil-kt/coil/blob/main/LICENSE.txt",
                        "QRose · MIT" to
                            "https://github.com/alexzhirkevich/qrose/blob/master/LICENSE",
                        "LXGW WenKai Lite · SIL OFL 1.1" to
                            "https://github.com/lxgw/LxgwWenKai-Lite/blob/main/OFL.txt",
                    )
                    .forEach { (label, url) ->
                        TextButton(onClick = { ui.open(url) }) { Text(label) }
                    }
            }
        }
    }
}

@Composable
private fun AccountDefaults(ui: UiContext, privacy: Boolean) {
    var settings by remember { mutableStateOf<AccountSettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        try {
            settings = ui.api.accountSettings()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    ErrorNotice(error) { attempt++ }
    val value = settings
    if (value == null) {
        if (error == null) Loading()
        return
    }
    if (privacy) {
        Toggle(tr("mastodon_settings_discoverable"), value.discoverable) {
            settings = value.copy(discoverable = it)
        }
        Toggle(tr("mastodon_settings_indexable"), value.indexable) {
            settings = value.copy(indexable = it)
        }
        Toggle(tr("mastodon_profile_locked"), value.locked) { settings = value.copy(locked = it) }
    } else {
        Text(tr("mastodon_settings_posting_explanation"))
        Choice(tr("mastodon_settings_visibility"), value.privacy, visibilityOptions()) {
            settings = value.copy(privacy = it)
        }
        OutlinedTextField(
            value.language,
            { settings = value.copy(language = it) },
            label = { Text(tr("mastodon_settings_language")) },
            modifier = Modifier.fillMaxWidth(),
        )
        Toggle(tr("mastodon_sensitive_content"), value.sensitive) {
            settings = value.copy(sensitive = it)
        }
        Choice(tr("mastodon_settings_quote_policy"), value.quotePolicy, quoteOptions()) {
            settings = value.copy(quotePolicy = it)
        }
    }
    Button(
        onClick = {
            busy = true
            ui.run {
                try {
                    ui.store.updateAccount(ui.api.updateAccountSettings(value))
                    ui.message = "Settings saved"
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy,
    ) {
        Text(tr("mastodon_save"))
    }
}

@Composable
private fun NotificationSettings(ui: UiContext) {
    var policy by remember { mutableStateOf<NotificationPolicy?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(attempt) {
        try {
            policy = ui.api.notificationPolicy()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    ErrorNotice(error) { attempt++ }
    val value = policy
    if (value == null) {
        if (error == null) Loading()
        return
    }
    val options = listOf("accept" to "Accept", "filter" to "Filter", "drop" to "Drop")
    Choice(tr("mastodon_settings_notify_not_following"), value.forNotFollowing, options) {
        policy = value.copy(forNotFollowing = it)
    }
    Choice(tr("mastodon_settings_notify_not_followers"), value.forNotFollowers, options) {
        policy = value.copy(forNotFollowers = it)
    }
    Choice(tr("mastodon_settings_notify_new_accounts"), value.forNewAccounts, options) {
        policy = value.copy(forNewAccounts = it)
    }
    Choice(tr("mastodon_settings_notify_private_mentions"), value.forPrivateMentions, options) {
        policy = value.copy(forPrivateMentions = it)
    }
    Choice("Limited accounts", value.forLimitedAccounts, options) {
        policy = value.copy(forLimitedAccounts = it)
    }
    Button(
        onClick = {
            busy = true
            ui.run {
                try {
                    policy = ui.api.updateNotificationPolicy(value)
                    ui.message = "Settings saved"
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy,
    ) {
        Text(tr("mastodon_save"))
    }
}

@Composable
private fun FiltersSettings(ui: UiContext) {
    var filters by remember { mutableStateOf<List<ContentFilter>?>(null) }
    var editing by remember { mutableStateOf<ContentFilter?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var pendingDelete by remember { mutableStateOf<ContentFilter?>(null) }
    LaunchedEffect(attempt) {
        try {
            filters = ui.api.filters()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    ErrorNotice(error) { attempt++ }
    Button(onClick = { editing = ContentFilter() }) { Text(tr("mastodon_settings_filter_add")) }
    if (filters == null && error == null) Loading()
    if (filters?.isEmpty() == true) Empty(tr("mastodon_settings_filter_empty"))
    filters.orEmpty().forEach { filter ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(filter.title, style = MaterialTheme.typography.titleMedium)
                Text(filter.keywords.joinToString(", ") { it.keyword })
                Text(filter.context.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { editing = filter }) { Text(tr("mastodon_edit")) }
                    TextButton(onClick = { pendingDelete = filter }) {
                        Text(tr("mastodon_settings_filter_delete"))
                    }
                }
            }
        }
    }
    pendingDelete?.let { filter ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr("mastodon_settings_filter_delete")) },
            text = { Text(filter.title) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ui.run {
                            ui.api.deleteFilter(filter.id)
                            pendingDelete = null
                            attempt++
                        }
                    }
                ) {
                    Text(tr("mastodon_delete"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(tr("mastodon_cancel")) }
            },
        )
    }
    editing?.let { initial ->
        key(initial) {
            var title by remember { mutableStateOf(initial.title) }
            var keywords by remember {
                mutableStateOf(initial.keywords.joinToString(", ") { it.keyword })
            }
            var warn by remember { mutableStateOf(initial.action != "hide") }
            var contexts by remember { mutableStateOf(initial.context.toSet()) }
            var wholeWord by remember { mutableStateOf(initial.keywords.all { it.wholeWord }) }
            var expires by remember { mutableStateOf(initial.expiresAt.orEmpty()) }
            AlertDialog(
                onDismissRequest = { editing = null },
                title = { Text(tr("mastodon_settings_filters")) },
                text = {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            title,
                            { title = it },
                            label = { Text(tr("mastodon_settings_filter_title")) },
                        )
                        OutlinedTextField(
                            keywords,
                            { keywords = it },
                            label = { Text(tr("mastodon_settings_filter_keywords")) },
                        )
                        Toggle(tr("mastodon_settings_filter_warn"), warn) { warn = it }
                        Toggle("Whole words", wholeWord) { wholeWord = it }
                        Text(tr("mastodon_settings_filter_contexts"))
                        listOf("home", "notifications", "public", "thread", "account").forEach {
                            context ->
                            Toggle(context, context in contexts) { checked ->
                                contexts = if (checked) contexts + context else contexts - context
                            }
                        }
                        OutlinedTextField(
                            expires,
                            { expires = it },
                            label = { Text("Expires at (ISO 8601, optional)") },
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            ui.run {
                                ui.api.saveFilter(
                                    initial.copy(
                                        title = title.trim(),
                                        action = if (warn) "warn" else "hide",
                                        context = contexts.toList(),
                                        keywords =
                                            keywords
                                                .split(',')
                                                .map { it.trim() }
                                                .filter { it.isNotEmpty() }
                                                .map { word ->
                                                    FilterKeyword(
                                                        initial.keywords
                                                            .firstOrNull { it.keyword == word }
                                                            ?.id,
                                                        word,
                                                        wholeWord,
                                                    )
                                                },
                                        expiresAt = expires.ifBlank { null },
                                    )
                                )
                                editing = null
                                attempt++
                            }
                        },
                        enabled =
                            title.isNotBlank() &&
                                keywords.split(',').any { it.isNotBlank() } &&
                                contexts.isNotEmpty(),
                    ) {
                        Text(tr("mastodon_save"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editing = null }) { Text(tr("mastodon_cancel")) }
                },
            )
        }
    }
}

@Composable
private fun ServerSettings(ui: UiContext) {
    var info by remember { mutableStateOf<ServerInfo?>(null) }
    var description by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        try {
            info = ui.api.serverInfo()
            description = ui.api.serverDescription()
            error = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message
        }
    }
    ErrorNotice(error) { attempt++ }
    if (info == null && error == null) Loading()
    info?.let { server ->
        Text(server.title, style = MaterialTheme.typography.headlineSmall)
        Text(server.domain)
        Text(server.version)
        HtmlContent(ui, server.description)
        Text("${server.maxCharacters} characters per post")
        HtmlContent(ui, description)
        TextButton(
            onClick = { ui.open("https://${ui.api.instance.removePrefix("https://")}/about") }
        ) {
            Text(tr("mastodon_open_in_browser"))
        }
    }
}

@Composable
private fun PrivacyPolicy(ui: UiContext) {
    Text("QuanToot Privacy Policy", style = MaterialTheme.typography.headlineSmall)
    Text("Last updated: July 14, 2026", style = MaterialTheme.typography.bodySmall)
    val sections =
        listOf(
            "Data collected by QuanToot" to
                "QuanToot is a client for Mastodon servers and does not operate a social network or backend service. BobMaster does not collect, receive, sell, or share personal information through the app. QuanToot contains no advertising, analytics, or tracking SDKs.\n\nThe app stores the server you select, preferences, and cached timeline or media data locally. Mastodon account sessions and access tokens are kept in the operating system's secure storage. Android backup is disabled for the app's data. This information is not sent to BobMaster.",
            "Independent Mastodon servers" to
                "QuanToot communicates directly with the independently operated Mastodon server you choose. Its operator may collect or process your IP address, account and profile information, posts, media, interactions, and usage records. Remote media or linked-content hosts may also receive ordinary connection information when content is loaded.\n\nThese parties are not controlled by QuanToot or BobMaster. Their own privacy policies and terms apply. Review your server's policies before signing in or posting.",
            "Photos and media" to
                "QuanToot uses the system picker when you choose media to upload, so the app receives only the items you select. When you explicitly save media, the app may write that item to your device's gallery.",
            "Your choices and data deletion" to
                "Remove an account, clear QuanToot's data in system settings, or uninstall the app to delete locally stored data. For data held by a Mastodon server, use that server's controls or contact its operator.",
            "Changes to this policy" to
                "This policy may be updated when QuanToot's features or legal requirements change. The date above identifies the latest version.",
        )
    sections.forEach { (title, body) ->
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body)
    }
    Text("Contact", style = MaterialTheme.typography.titleMedium)
    Text("BobMaster · bobmaster@hibobmaster.com")
    TextButton(onClick = { ui.open("https://quanquan.space/") }) { Text(tr("settings_feedback")) }
}
